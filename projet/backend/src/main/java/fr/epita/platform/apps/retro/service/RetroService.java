package fr.epita.platform.apps.retro.service;

import fr.epita.platform.apps.retro.dto.RetroDtos.*;
import fr.epita.platform.apps.retro.entity.*;
import fr.epita.platform.apps.retro.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Le serveur filtre selon la phase : en WRITING chacun ne reçoit que ses cartes ; les totaux de votes restent
 * masqués jusqu'à la fin du vote. Une rétrospective CLOSED est en lecture seule.
 */
@Service
@RequiredArgsConstructor
public class RetroService {

    static final int VOTES_PER_PERSON = 3;
    static final int MAX_PER_TARGET = 2;

    private final RetroTeamRepository teams;
    private final RetroRepository retros;
    private final RetroCardRepository cards;
    private final RetroGroupRepository groups;
    private final RetroVoteRepository votes;
    private final RetroActionRepository actions;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- équipes ----------

    @Transactional(readOnly = true)
    public List<TeamDto> myTeams(AuthUser me) {
        return teams.ofMember(me.id()).stream().map(this::toDto).toList();
    }

    @Transactional
    public TeamDto createTeam(AuthUser me, TeamRequest req) {
        Set<UUID> members = new LinkedHashSet<>(req.memberIds());
        members.add(me.id());
        members.forEach(userLookup::require);
        RetroTeam t = new RetroTeam();
        t.setName(req.name().trim());
        t.setMemberIds(new HashSet<>(members));
        return toDto(teams.saveAndFlush(t));
    }

    // ---------- rétrospectives ----------

    @Transactional(readOnly = true)
    public List<RetroSummary> list(AuthUser me, UUID teamId) {
        Map<UUID, RetroTeam> mine = teams.ofMember(me.id()).stream().collect(Collectors.toMap(RetroTeam::getId, t -> t));
        Collection<UUID> ids = teamId == null ? mine.keySet() : mine.containsKey(teamId) ? List.of(teamId) : List.of();
        if (ids.isEmpty()) {
            return List.of();
        }
        return retros.findByTeamIdInOrderByCreatedAtDesc(ids).stream().map(r -> new RetroSummary(r.getId(), r.getTeamId(), mine.get(r.getTeamId()).getName(),
                r.getTitle(), r.getSprint(), userLookup.name(r.getFacilitatorId()), r.getPhase(), actions.countByRetroId(r.getId()), r.getCreatedAt())).toList();
    }

    @Transactional
    public RetroDto create(AuthUser me, RetroRequest req) {
        RetroTeam t = teamOf(me, req.teamId());
        Retro r = new Retro();
        r.setTeamId(t.getId());
        r.setTitle(req.title().trim());
        r.setSprint(req.sprint().trim());
        r.setFacilitatorId(me.id());
        r.setPhase(RetroPhase.WRITING);
        return state(me, retros.saveAndFlush(r));
    }

    @Transactional(readOnly = true)
    public RetroDto get(AuthUser me, UUID id) {
        return state(me, member(me, id, false));
    }

    @Transactional
    public RetroDto nextPhase(AuthUser me, UUID id) {
        Retro r = facilitator(me, id);
        RetroPhase next = r.getPhase().next();
        if (next == null) {
            throw ApiException.conflict("INVALID_PHASE_TRANSITION", "La rétrospective est close");
        }
        r.setPhase(next);
        // le timer de la phase précédente n'a plus de sens
        r.setTimerEndsAt(null);
        r.setTimerRemaining(null);
        return state(me, retros.saveAndFlush(r));
    }

    /** Le serveur stocke la fin du décompte ; la fin du timer ne change jamais la phase. */
    @Transactional
    public RetroDto timer(AuthUser me, UUID id, TimerRequest req) {
        Retro r = facilitator(me, id);
        requireOpen(r);
        Instant now = clock.instant();
        switch (req.action()) {
            case START -> {
                requireSeconds(req);
                r.setTimerEndsAt(now.plusSeconds(req.seconds()));
                r.setTimerRemaining(null);
            }
            case PAUSE -> {
                if (r.getTimerEndsAt() == null) {
                    throw ApiException.conflict("TIMER_NOT_RUNNING", "Aucun décompte en cours");
                }
                r.setTimerRemaining((int) Math.max(0, Duration.between(now, r.getTimerEndsAt()).toSeconds()));
                r.setTimerEndsAt(null);
            }
            case RESUME -> {
                if (r.getTimerRemaining() == null) {
                    throw ApiException.conflict("TIMER_NOT_PAUSED", "Le décompte n'est pas en pause");
                }
                r.setTimerEndsAt(now.plusSeconds(r.getTimerRemaining()));
                r.setTimerRemaining(null);
            }
            case ADD -> {
                requireSeconds(req);
                if (r.getTimerEndsAt() != null) {
                    r.setTimerEndsAt(r.getTimerEndsAt().isBefore(now) ? now.plusSeconds(req.seconds()) : r.getTimerEndsAt().plusSeconds(req.seconds()));
                } else if (r.getTimerRemaining() != null) {
                    r.setTimerRemaining(r.getTimerRemaining() + req.seconds());
                } else {
                    throw ApiException.conflict("TIMER_NOT_RUNNING", "Aucun décompte à prolonger");
                }
            }
        }
        return state(me, retros.saveAndFlush(r));
    }

    private void requireSeconds(TimerRequest req) {
        if (req.seconds() == null) {
            throw ValidationException.field("seconds", "obligatoire pour " + req.action());
        }
    }

    // ---------- cartes et groupes ----------

    @Transactional
    public RetroDto addCard(AuthUser me, UUID id, CardRequest req) {
        Retro r = member(me, id, true);
        requirePhase(r, RetroPhase.WRITING);
        RetroCard c = new RetroCard();
        c.setRetroId(id);
        c.setAuthorId(me.id());
        c.setColumn(req.column());
        c.setText(req.text().trim());
        cards.saveAndFlush(c);
        return state(me, r);
    }

    @Transactional
    public RetroDto updateCard(AuthUser me, UUID id, UUID cardId, CardRequest req) {
        Retro r = member(me, id, true);
        requirePhase(r, RetroPhase.WRITING);
        RetroCard c = ownCard(me, id, cardId);
        c.setColumn(req.column());
        c.setText(req.text().trim());
        cards.saveAndFlush(c);
        return state(me, r);
    }

    @Transactional
    public RetroDto deleteCard(AuthUser me, UUID id, UUID cardId) {
        Retro r = member(me, id, true);
        requirePhase(r, RetroPhase.WRITING);
        cards.delete(ownCard(me, id, cardId));
        cards.flush();
        return state(me, r);
    }

    @Transactional
    public RetroDto group(AuthUser me, UUID id, GroupRequest req) {
        Retro r = facilitator(me, id);
        requirePhase(r, RetroPhase.GROUPING);
        Map<UUID, RetroCard> byId = cards.findByRetroIdOrderByCreatedAtAsc(id).stream().collect(Collectors.toMap(RetroCard::getId, c -> c));
        for (int i = 0; i < req.cardIds().size(); i++) {
            if (!byId.containsKey(req.cardIds().get(i))) {
                throw ValidationException.field("cardIds[" + i + "]", "carte inconnue dans cette rétrospective");
            }
        }
        RetroGroup g = new RetroGroup();
        g.setRetroId(id);
        g.setTitle(req.title().trim());
        groups.saveAndFlush(g);
        req.cardIds().forEach(cid -> {
            RetroCard c = byId.get(cid);
            c.setGroupId(g.getId());
            cards.save(c);
        });
        cards.flush();
        return state(me, r);
    }

    // ---------- votes ----------

    /** Remplace mes votes : 3 au total, 2 au plus par carte ou groupe. */
    @Transactional
    public RetroDto vote(AuthUser me, UUID id, VotesRequest req) {
        Retro r = member(me, id, true);
        requirePhase(r, RetroPhase.VOTING);
        Set<UUID> targets = new HashSet<>();
        cards.findByRetroIdOrderByCreatedAtAsc(id).forEach(c -> targets.add(c.getId()));
        groups.findByRetroIdOrderByCreatedAtAsc(id).forEach(g -> targets.add(g.getId()));
        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        for (int i = 0; i < req.votes().size(); i++) {
            VoteItem v = req.votes().get(i);
            if (!targets.contains(v.targetId())) {
                throw ValidationException.field("votes[" + i + "].targetId", "carte ou groupe inconnu");
            }
            wanted.merge(v.targetId(), v.count(), Integer::sum);
        }
        if (wanted.values().stream().mapToInt(Integer::intValue).sum() > VOTES_PER_PERSON) {
            throw ApiException.conflict("NO_VOTE_LEFT", VOTES_PER_PERSON + " votes maximum");
        }
        if (wanted.values().stream().anyMatch(c -> c > MAX_PER_TARGET)) {
            throw ApiException.conflict("NO_VOTE_LEFT", MAX_PER_TARGET + " votes maximum sur une même carte ou un même groupe");
        }
        votes.deleteAll(votes.findByIdRetroIdAndIdUserId(id, me.id()));
        votes.flush();
        wanted.forEach((target, count) -> votes.save(new RetroVote(new RetroVote.Key(id, me.id(), target), count)));
        votes.flush();
        return state(me, r);
    }

    // ---------- actions ----------

    @Transactional
    public RetroDto addAction(AuthUser me, UUID id, ActionRequest req) {
        Retro r = member(me, id, true);
        requirePhase(r, RetroPhase.ACTIONS);
        RetroTeam t = teams.findById(r.getTeamId()).orElseThrow();
        if (!t.getMemberIds().contains(req.ownerId())) {
            throw ValidationException.field("ownerId", "le responsable doit être membre de l'équipe");
        }
        RetroAction a = new RetroAction();
        a.setRetroId(id);
        a.setTeamId(t.getId());
        a.setText(req.text().trim());
        a.setOwnerId(req.ownerId());
        a.setDueDate(req.dueDate());
        actions.saveAndFlush(a);
        return state(me, r);
    }

    @Transactional
    public ActionDto setDone(AuthUser me, UUID actionId, boolean done) {
        RetroAction a = actions.findById(actionId).orElseThrow(() -> ApiException.notFound("Action"));
        RetroTeam t = teamOf(me, a.getTeamId());
        Retro r = retros.findById(a.getRetroId()).orElseThrow();
        if (!a.getOwnerId().equals(me.id()) && !r.getFacilitatorId().equals(me.id())) {
            throw ApiException.forbidden("Réservé au responsable de l'action");
        }
        a.setDone(done);
        actions.saveAndFlush(a);
        return toDto(a, t);
    }

    @Transactional(readOnly = true)
    public List<ActionDto> openActions(AuthUser me, UUID teamId) {
        RetroTeam t = teamOf(me, teamId);
        return actions.findByTeamIdAndDoneFalseOrderByDueDateAsc(teamId).stream().map(a -> toDto(a, t)).toList();
    }

    // ---------- état ----------

    private RetroDto state(AuthUser me, Retro r) {
        RetroTeam t = teams.findById(r.getTeamId()).orElseThrow();
        boolean writing = r.getPhase() == RetroPhase.WRITING;
        boolean showTotals = r.getPhase().ordinal() > RetroPhase.VOTING.ordinal();
        List<RetroVote> allVotes = votes.findByIdRetroId(r.getId());
        Map<UUID, Integer> totals = allVotes.stream().collect(Collectors.groupingBy(v -> v.getId().getTargetId(), Collectors.summingInt(RetroVote::getCount)));
        List<RetroCard> all = cards.findByRetroIdOrderByCreatedAtAsc(r.getId());
        List<RetroCard> visible = writing ? all.stream().filter(c -> c.getAuthorId().equals(me.id())).toList() : all;
        Map<UUID, String> names = userLookup.namesOf(visible, RetroCard::getAuthorId);
        List<CardDto> cardDtos = visible.stream().map(c -> new CardDto(c.getId(), c.getColumn(), c.getText(), c.getAuthorId(), names.get(c.getAuthorId()),
                c.getAuthorId().equals(me.id()), c.getGroupId(), showTotals ? totals.getOrDefault(c.getId(), 0) : null)).toList();
        List<GroupDto> groupDtos = groups.findByRetroIdOrderByCreatedAtAsc(r.getId()).stream()
                .map(g -> new GroupDto(g.getId(), g.getTitle(), all.stream().filter(c -> g.getId().equals(c.getGroupId())).map(RetroCard::getId).toList(),
                        showTotals ? totals.getOrDefault(g.getId(), 0) : null)).toList();
        List<VoteItem> mine = allVotes.stream().filter(v -> v.getId().getUserId().equals(me.id()))
                .map(v -> new VoteItem(v.getId().getTargetId(), v.getCount())).toList();
        int left = VOTES_PER_PERSON - mine.stream().mapToInt(VoteItem::count).sum();
        List<ActionDto> current = actions.findByRetroIdOrderByCreatedAtAsc(r.getId()).stream().map(a -> toDto(a, t)).toList();
        List<ActionDto> previous = actions.findByTeamIdAndDoneFalseOrderByDueDateAsc(t.getId()).stream()
                .filter(a -> !a.getRetroId().equals(r.getId())).map(a -> toDto(a, t)).toList();
        return new RetroDto(r.getId(), t.getId(), t.getName(), r.getTitle(), r.getSprint(), r.getFacilitatorId(), userLookup.name(r.getFacilitatorId()),
                r.getFacilitatorId().equals(me.id()), r.getPhase(), r.getTimerEndsAt(), r.getTimerRemaining(), clock.instant(),
                cardDtos, all.size() - visible.size(), groupDtos, mine, left, current, previous, toDto(t).members());
    }

    // ---------- garde-fous ----------

    private RetroTeam teamOf(AuthUser me, UUID teamId) {
        RetroTeam t = teams.findById(teamId).orElseThrow(() -> ApiException.notFound("Équipe"));
        if (!t.getMemberIds().contains(me.id())) {
            throw ApiException.notFound("Équipe");
        }
        return t;
    }

    private Retro member(AuthUser me, UUID id, boolean lock) {
        Retro r = (lock ? retros.lock(id) : retros.findById(id)).orElseThrow(() -> ApiException.notFound("Rétrospective"));
        teams.findById(r.getTeamId()).filter(t -> t.getMemberIds().contains(me.id())).orElseThrow(() -> ApiException.notFound("Rétrospective"));
        return r;
    }

    private Retro facilitator(AuthUser me, UUID id) {
        Retro r = member(me, id, true);
        if (!r.getFacilitatorId().equals(me.id())) {
            throw ApiException.forbidden("Réservé à l'animateur");
        }
        return r;
    }

    private void requireOpen(Retro r) {
        if (r.getPhase() == RetroPhase.CLOSED) {
            throw ApiException.conflict("PHASE_LOCKED", "Rétrospective close : lecture seule");
        }
    }

    private void requirePhase(Retro r, RetroPhase phase) {
        if (r.getPhase() != phase) {
            throw ApiException.conflict("PHASE_LOCKED", "Action possible seulement en phase " + phase + " (phase actuelle : " + r.getPhase() + ")");
        }
    }

    private RetroCard ownCard(AuthUser me, UUID retroId, UUID cardId) {
        RetroCard c = cards.findById(cardId).filter(x -> x.getRetroId().equals(retroId)).orElseThrow(() -> ApiException.notFound("Carte"));
        if (!c.getAuthorId().equals(me.id())) {
            throw ApiException.forbidden("Réservé à l'auteur de la carte");
        }
        return c;
    }

    private TeamDto toDto(RetroTeam t) {
        Map<UUID, String> names = userLookup.names(t.getMemberIds());
        return new TeamDto(t.getId(), t.getName(), t.getMemberIds().stream().map(u -> new MemberDto(u, names.get(u)))
                .sorted(Comparator.comparing(MemberDto::name, Comparator.nullsLast(String::compareTo))).toList());
    }

    private ActionDto toDto(RetroAction a, RetroTeam t) {
        return new ActionDto(a.getId(), a.getRetroId(), a.getText(), a.getOwnerId(), userLookup.name(a.getOwnerId()), a.getDueDate(), a.isDone());
    }
}
