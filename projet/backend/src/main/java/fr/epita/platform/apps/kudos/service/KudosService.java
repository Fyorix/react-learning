package fr.epita.platform.apps.kudos.service;

import fr.epita.platform.apps.kudos.dto.KudosDtos.KudoDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.KudoRequest;
import fr.epita.platform.apps.kudos.dto.KudosDtos.PersonDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.ProfileDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.RankDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.ReactionDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.ValueDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.WallDto;
import fr.epita.platform.apps.kudos.entity.Kudo;
import fr.epita.platform.apps.kudos.entity.KudoValue;
import fr.epita.platform.apps.kudos.entity.Reaction;
import fr.epita.platform.apps.kudos.repository.KudoRepository;
import fr.epita.platform.apps.kudos.repository.KudoSpecs;
import fr.epita.platform.apps.kudos.repository.KudoValueRepository;
import fr.epita.platform.apps.kudos.repository.ReactionRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KudosService {

    public static final List<String> EMOJIS = List.of("👏", "❤️", "🎉", "🚀", "😂");
    static final int DAILY_LIMIT = 5;
    static final Duration AUTHOR_DELETE_WINDOW = Duration.ofHours(1);

    private final KudoRepository kudos;
    private final KudoValueRepository values;
    private final ReactionRepository reactions;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- mur ----------

    /** Pagination par curseur : stable même quand de nouveaux kudos arrivent pendant le défilement. */
    @Transactional(readOnly = true)
    public WallDto wall(AuthUser me, UUID cursor, Integer limit, UUID valueId, UUID userId) {
        int size = limit == null ? 20 : Math.max(1, Math.min(limit, 50));
        Instant cursorAt = null;
        if (cursor != null) {
            Kudo c = kudos.findById(cursor).orElseThrow(() -> ValidationException.field("cursor", "curseur inconnu"));
            cursorAt = c.getCreatedAt();
        }
        List<Kudo> page = kudos.findAll(KudoSpecs.wall(valueId, userId, cursorAt, cursor),
                PageRequest.of(0, size + 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).getContent();
        boolean more = page.size() > size;
        List<Kudo> items = more ? page.subList(0, size) : page;
        return new WallDto(toDtos(items, me), more ? items.get(items.size() - 1).getId() : null);
    }

    @Transactional
    public KudoDto send(AuthUser me, KudoRequest req) {
        Set<UUID> recipients = new LinkedHashSet<>(req.recipientIds());
        if (recipients.contains(me.id())) {
            throw ApiException.conflict("SELF_KUDO", "On ne s'envoie pas de kudo à soi-même");
        }
        recipients.forEach(userLookup::require);
        if (!values.existsById(req.valueId())) {
            throw ValidationException.field("valueId", "valeur inconnue");
        }
        ZoneId zone = clock.getZone();
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(zone).toInstant();
        if (kudos.countByAuthorIdAndCreatedAtGreaterThanEqual(me.id(), startOfDay) >= DAILY_LIMIT) {
            throw ApiException.conflict("DAILY_LIMIT_REACHED", DAILY_LIMIT + " kudos maximum par jour");
        }
        Kudo k = new Kudo();
        k.setAuthorId(me.id());
        k.setMessage(req.message().trim());
        k.setValueId(req.valueId());
        k.setRecipientIds(recipients);
        k.setCreatedAt(clock.instant());
        return toDtos(List.of(kudos.saveAndFlush(k)), me).get(0);
    }

    /** L'auteur supprime son kudo dans l'heure ; un ADMIN le modère (il disparaît du mur et des classements). */
    @Transactional
    public void delete(AuthUser me, UUID id) {
        Kudo k = kudos.findById(id).filter(x -> !x.isModerated()).orElseThrow(() -> ApiException.notFound("Kudo"));
        if (me.isAdmin()) {
            k.setModerated(true);
            return;
        }
        if (!k.getAuthorId().equals(me.id())) {
            throw ApiException.forbidden("Seul l'auteur ou un ADMIN peut retirer ce kudo");
        }
        if (k.getCreatedAt().plus(AUTHOR_DELETE_WINDOW).isBefore(clock.instant())) {
            throw ApiException.conflict("DELETE_WINDOW_EXPIRED", "Un kudo ne peut être supprimé que dans l'heure qui suit son envoi");
        }
        kudos.delete(k);
    }

    @Transactional
    public KudoDto react(AuthUser me, UUID id, String emoji, boolean add) {
        if (!EMOJIS.contains(emoji)) {
            throw ValidationException.field("emoji", "Réactions possibles : " + String.join(" ", EMOJIS));
        }
        Kudo k = kudos.findById(id).filter(x -> !x.isModerated()).orElseThrow(() -> ApiException.notFound("Kudo"));
        Reaction.Key key = new Reaction.Key(id, me.id(), emoji);
        if (add && !reactions.existsById(key)) {
            reactions.saveAndFlush(new Reaction(key));
        } else if (!add && reactions.existsById(key)) {
            reactions.deleteById(key);
            reactions.flush();
        }
        return toDtos(List.of(k), me).get(0);
    }

    // ---------- profils et classement ----------

    @Transactional(readOnly = true)
    public ProfileDto profile(AuthUser me, UUID userId) {
        var user = userLookup.require(userId);
        List<Kudo> received = kudos.received(userId).stream().sorted(Comparator.comparing(Kudo::getCreatedAt).reversed()).toList();
        long sent = kudos.findByAuthorIdAndModeratedFalse(userId).size();
        Map<UUID, String> names = valueNames();
        Map<String, Long> byValue = received.stream().collect(Collectors.groupingBy(k -> names.getOrDefault(k.getValueId(), "?"), Collectors.counting()));
        return new ProfileDto(new PersonDto(user.getId(), user.getDisplayName()), received.size(), sent, byValue,
                toDtos(received.stream().limit(10).toList(), me));
    }

    @Transactional(readOnly = true)
    public List<RankDto> leaderboard(YearMonth month) {
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        ZoneId zone = clock.getZone();
        Map<UUID, Long> counts = new HashMap<>();
        kudos.findByModeratedFalseAndCreatedAtBetween(m.atDay(1).atStartOfDay(zone).toInstant(),
                        m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant())
                .forEach(k -> k.getRecipientIds().forEach(r -> counts.merge(r, 1L, Long::sum)));
        Map<UUID, String> names = userLookup.names(counts.keySet());
        List<Map.Entry<UUID, Long>> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed().thenComparing(e -> names.getOrDefault(e.getKey(), ""))).toList();
        List<RankDto> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            var e = sorted.get(i);
            out.add(new RankDto(i + 1, new PersonDto(e.getKey(), names.get(e.getKey())), e.getValue()));
        }
        return out;
    }

    // ---------- valeurs ----------

    @Transactional(readOnly = true)
    public List<ValueDto> values() {
        return values.findAllByOrderByNameAsc().stream().map(v -> new ValueDto(v.getId(), v.getName())).toList();
    }

    @Transactional
    public ValueDto addValue(AuthUser me, String name) {
        me.requireAdmin();
        if (values.existsByNameIgnoreCase(name.trim())) {
            throw ApiException.conflict("VALUE_EXISTS", "Cette valeur existe déjà");
        }
        KudoValue v = new KudoValue();
        v.setName(name.trim());
        values.saveAndFlush(v);
        return new ValueDto(v.getId(), v.getName());
    }

    // ---------- outils ----------

    private Map<UUID, String> valueNames() {
        return values.findAll().stream().collect(Collectors.toMap(KudoValue::getId, KudoValue::getName));
    }

    private List<KudoDto> toDtos(List<Kudo> list, AuthUser me) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> valueNames = valueNames();
        Set<UUID> people = new LinkedHashSet<>();
        list.forEach(k -> {
            people.add(k.getAuthorId());
            people.addAll(k.getRecipientIds());
        });
        Map<UUID, String> names = userLookup.names(people);
        Map<UUID, List<Reaction>> byKudo = reactions.findByIdKudoIdIn(list.stream().map(Kudo::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getId().getKudoId()));
        Instant now = clock.instant();
        return list.stream().map(k -> {
            List<Reaction> rs = byKudo.getOrDefault(k.getId(), List.of());
            List<ReactionDto> counts = EMOJIS.stream()
                    .map(e -> new ReactionDto(e, rs.stream().filter(r -> r.getId().getEmoji().equals(e)).count(),
                            rs.stream().anyMatch(r -> r.getId().getEmoji().equals(e) && r.getId().getUserId().equals(me.id()))))
                    .filter(r -> r.count() > 0).toList();
            boolean canDelete = me.isAdmin() || (k.getAuthorId().equals(me.id()) && !k.getCreatedAt().plus(AUTHOR_DELETE_WINDOW).isBefore(now));
            return new KudoDto(k.getId(), new PersonDto(k.getAuthorId(), names.get(k.getAuthorId())),
                    k.getRecipientIds().stream().map(r -> new PersonDto(r, names.get(r))).toList(), k.getMessage(),
                    k.getValueId(), valueNames.get(k.getValueId()), counts, canDelete, k.getCreatedAt());
        }).toList();
    }
}
