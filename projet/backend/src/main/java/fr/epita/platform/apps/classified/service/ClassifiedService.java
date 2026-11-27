package fr.epita.platform.apps.classified.service;

import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ConversationDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingRequest;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ListingSummaryDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.MessageDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.MessageRequest;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.PhotoDto;
import fr.epita.platform.apps.classified.dto.ClassifiedDtos.ReportedDto;
import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingStatus;
import fr.epita.platform.apps.classified.entity.ClassifiedEnums.ListingType;
import fr.epita.platform.apps.classified.entity.Listing;
import fr.epita.platform.apps.classified.entity.ListingMessage;
import fr.epita.platform.apps.classified.entity.ListingReport;
import fr.epita.platform.apps.classified.entity.Photo;
import fr.epita.platform.apps.classified.repository.ListingMessageRepository;
import fr.epita.platform.apps.classified.repository.ListingReportRepository;
import fr.epita.platform.apps.classified.repository.ListingRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.FileStorage;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassifiedService {

    static final Duration LIFETIME = Duration.ofDays(30);
    static final int MAX_ACTIVE = 10;
    static final int MAX_PHOTOS = 6;
    static final int HIDE_AFTER_REPORTS = 3;

    private final ListingRepository listings;
    private final ListingMessageRepository messages;
    private final ListingReportRepository reports;
    private final FileStorage storage;
    private final UserLookup userLookup;
    private final Clock clock;

    public record Image(byte[] content, String contentType) {
    }

    // ---------- annonces ----------

    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryDto> search(ListingType type, String category, BigDecimal minPrice, BigDecimal maxPrice,
                                                  String search, int page, int size) {
        Instant now = clock.instant();
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<ListingSummaryDto> list = listings.findAllByOrderByCreatedAtDesc().stream()
                .filter(l -> !l.isHidden() && l.effectiveStatus(now) == ListingStatus.ACTIVE)
                .filter(l -> type == null || l.getType() == type)
                .filter(l -> category == null || category.isBlank() || l.getCategory().equalsIgnoreCase(category.trim()))
                .filter(l -> minPrice == null || (l.getPrice() != null && l.getPrice().compareTo(minPrice) >= 0))
                .filter(l -> maxPrice == null || l.getPrice() == null || l.getPrice().compareTo(maxPrice) <= 0)
                .filter(l -> term.isEmpty() || l.getTitle().toLowerCase(Locale.ROOT).contains(term)
                        || l.getDescription().toLowerCase(Locale.ROOT).contains(term))
                .map(l -> summary(l, now)).toList();
        return PageResponse.ofList(list, page, size);
    }

    @Transactional
    public ListingDto publish(AuthUser me, ListingRequest req, List<MultipartFile> photos) {
        validate(req);
        Instant now = clock.instant();
        long live = listings.findByAuthorIdOrderByCreatedAtDesc(me.id()).stream().filter(l -> l.isLive(now)).count();
        if (live >= MAX_ACTIVE) {
            throw ApiException.conflict("TOO_MANY_LISTINGS", MAX_ACTIVE + " annonces actives maximum par personne");
        }
        Listing l = new Listing();
        l.setAuthorId(me.id());
        apply(l, req);
        l.setExpiresAt(now.plus(LIFETIME));
        l.setCreatedAt(now);
        replacePhotos(l, photos);
        return toDto(listings.saveAndFlush(l), me);
    }

    @Transactional(readOnly = true)
    public ListingDto get(AuthUser me, UUID id) {
        Listing l = listing(id);
        if (l.isHidden() && !me.isAdmin() && !l.getAuthorId().equals(me.id())) {
            throw ApiException.notFound("Annonce");
        }
        return toDto(l, me);
    }

    @Transactional
    public ListingDto update(AuthUser me, UUID id, ListingRequest req) {
        Listing l = owned(me, id);
        validate(req);
        apply(l, req);
        return toDto(l, me);
    }

    @Transactional
    public ListingDto updatePhotos(AuthUser me, UUID id, List<MultipartFile> photos) {
        Listing l = owned(me, id);
        replacePhotos(l, photos);
        return toDto(listings.saveAndFlush(l), me);
    }

    @Transactional(readOnly = true)
    public Image photo(UUID id, UUID photoId) {
        Photo p = listing(id).getPhotos().stream().filter(x -> x.getId().equals(photoId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Photo"));
        return new Image(storage.load(p.getFileKey()), p.getContentType());
    }

    @Transactional
    public ListingDto changeStatus(AuthUser me, UUID id, ListingStatus target) {
        Listing l = owned(me, id);
        ListingStatus current = l.effectiveStatus(clock.instant());
        if (target == ListingStatus.EXPIRED) {
            throw ValidationException.field("status", "Valeurs possibles : ACTIVE, RESERVED, CLOSED");
        }
        if (current == ListingStatus.CLOSED || current == ListingStatus.EXPIRED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Annonce " + current + " : renouvelez-la ou publiez-en une nouvelle");
        }
        l.setStatus(target);
        return toDto(l, me);
    }

    /** Renouvellement : 30 jours de plus à partir d'aujourd'hui ; une annonce close ne se renouvelle pas. */
    @Transactional
    public ListingDto renew(AuthUser me, UUID id) {
        Listing l = owned(me, id);
        if (l.getStatus() == ListingStatus.CLOSED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Une annonce close ne se renouvelle pas");
        }
        Instant now = clock.instant();
        if (!l.isLive(now)) {
            long live = listings.findByAuthorIdOrderByCreatedAtDesc(me.id()).stream().filter(x -> x.isLive(now)).count();
            if (live >= MAX_ACTIVE) {
                throw ApiException.conflict("TOO_MANY_LISTINGS", MAX_ACTIVE + " annonces actives maximum par personne");
            }
            l.setStatus(ListingStatus.ACTIVE);
        }
        l.setExpiresAt(now.plus(LIFETIME));
        return toDto(l, me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Listing l = listing(id);
        me.requireOwnerOrAdmin(l.getAuthorId());
        l.getPhotos().forEach(p -> storage.delete(p.getFileKey()));
        listings.delete(l);
    }

    @Transactional(readOnly = true)
    public List<ListingSummaryDto> mine(AuthUser me) {
        Instant now = clock.instant();
        return listings.findByAuthorIdOrderByCreatedAtDesc(me.id()).stream().map(l -> summary(l, now)).toList();
    }

    // ---------- messagerie ----------

    @Transactional
    public ConversationDto message(AuthUser me, UUID id, MessageRequest req) {
        Listing l = listing(id);
        UUID recipient;
        if (l.getAuthorId().equals(me.id())) {
            if (req.toUserId() == null) {
                throw ApiException.conflict("OWN_LISTING", "Vous ne pouvez pas contacter votre propre annonce");
            }
            boolean existing = messages.findBySenderIdOrRecipientIdOrderByCreatedAtAsc(me.id(), me.id()).stream()
                    .anyMatch(m -> m.getListingId().equals(id) && (m.getSenderId().equals(req.toUserId()) || m.getRecipientId().equals(req.toUserId())));
            if (!existing) {
                throw ValidationException.field("toUserId", "l'auteur ne répond qu'à une personne qui l'a contacté");
            }
            recipient = req.toUserId();
        } else {
            if (l.isHidden() || !l.isLive(clock.instant())) {
                throw ApiException.conflict("LISTING_UNAVAILABLE", "Cette annonce n'est plus disponible");
            }
            recipient = l.getAuthorId();
        }
        ListingMessage m = new ListingMessage();
        m.setListingId(id);
        m.setSenderId(me.id());
        m.setRecipientId(recipient);
        m.setContent(req.content().trim());
        messages.saveAndFlush(m);
        return conversations(me).stream().filter(c -> c.listingId().equals(id) && c.otherUserId().equals(recipient))
                .findFirst().orElseThrow();
    }

    /** Fils de messages, un par annonce et par interlocuteur ; visibles des deux seuls participants. */
    @Transactional(readOnly = true)
    public List<ConversationDto> conversations(AuthUser me) {
        Map<String, List<ListingMessage>> threads = new LinkedHashMap<>();
        for (ListingMessage m : messages.findBySenderIdOrRecipientIdOrderByCreatedAtAsc(me.id(), me.id())) {
            UUID other = m.getSenderId().equals(me.id()) ? m.getRecipientId() : m.getSenderId();
            threads.computeIfAbsent(m.getListingId() + "|" + other, k -> new ArrayList<>()).add(m);
        }
        List<ConversationDto> out = new ArrayList<>();
        threads.forEach((key, list) -> {
            UUID listingId = UUID.fromString(key.split("\\|")[0]);
            UUID other = UUID.fromString(key.split("\\|")[1]);
            Listing l = listings.findById(listingId).orElse(null);
            if (l == null) {
                return;
            }
            out.add(new ConversationDto(listingId, l.getTitle(), other, userLookup.name(other), l.getAuthorId().equals(me.id()),
                    list.stream().map(m -> new MessageDto(m.getId(), m.getSenderId(), userLookup.name(m.getSenderId()),
                            m.getContent(), m.getCreatedAt())).toList()));
        });
        out.sort((a, b) -> b.messages().get(b.messages().size() - 1).createdAt().compareTo(a.messages().get(a.messages().size() - 1).createdAt()));
        return out;
    }

    // ---------- signalements ----------

    /** Une annonce signalée par 3 personnes différentes est masquée en attendant la décision d'un ADMIN. */
    @Transactional
    public void report(AuthUser me, UUID id, String reason) {
        Listing l = listing(id);
        if (l.getAuthorId().equals(me.id())) {
            throw ApiException.conflict("OWN_LISTING", "Vous ne pouvez pas signaler votre propre annonce");
        }
        ListingReport.Key key = new ListingReport.Key(id, me.id());
        if (reports.existsById(key)) {
            throw ApiException.conflict("ALREADY_REPORTED", "Vous avez déjà signalé cette annonce");
        }
        ListingReport r = new ListingReport();
        r.setId(key);
        r.setReason(reason.trim());
        r.setCreatedAt(clock.instant());
        reports.saveAndFlush(r);
        if (reports.countByIdListingId(id) >= HIDE_AFTER_REPORTS) {
            l.setHidden(true);
        }
    }

    @Transactional(readOnly = true)
    public List<ReportedDto> reported(AuthUser me) {
        me.requireAdmin();
        Instant now = clock.instant();
        return listings.findAllByOrderByCreatedAtDesc().stream()
                .map(l -> Map.entry(l, reports.findByIdListingId(l.getId())))
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> new ReportedDto(summary(e.getKey(), now), e.getKey().isHidden(),
                        e.getValue().stream().map(ListingReport::getReason).toList()))
                .toList();
    }

    /** ADMIN : rétablit une annonce masquée et efface ses signalements. */
    @Transactional
    public ListingDto restore(AuthUser me, UUID id) {
        me.requireAdmin();
        Listing l = listing(id);
        l.setHidden(false);
        reports.deleteAll(reports.findByIdListingId(id));
        return toDto(l, me);
    }

    // ---------- outils ----------

    private static void validate(ListingRequest req) {
        if (req.type() == ListingType.SALE && req.price() == null) {
            throw ValidationException.field("price", "est obligatoire pour une vente");
        }
        if (req.type() != ListingType.SALE && req.price() != null) {
            throw ValidationException.field("price", "est interdit pour un don ou un service");
        }
    }

    private static void apply(Listing l, ListingRequest req) {
        l.setType(req.type());
        l.setTitle(req.title().trim());
        l.setDescription(req.description().trim());
        l.setCategory(req.category().trim());
        l.setPrice(req.price());
    }

    private void replacePhotos(Listing l, List<MultipartFile> files) {
        List<MultipartFile> list = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (list.isEmpty() || list.size() > MAX_PHOTOS) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_FILE", "1 à " + MAX_PHOTOS + " photos par annonce");
        }
        List<FileStorage.Stored> stored = list.stream().map(f -> storage.store(f, FileStorage.IMAGES)).toList();
        l.getPhotos().forEach(p -> storage.delete(p.getFileKey()));
        l.getPhotos().clear();
        for (int i = 0; i < stored.size(); i++) {
            Photo p = new Photo();
            p.setListing(l);
            p.setPosition(i);
            p.setFileKey(stored.get(i).key());
            p.setContentType(stored.get(i).contentType());
            l.getPhotos().add(p);
        }
    }

    private Listing listing(UUID id) {
        return listings.findById(id).orElseThrow(() -> ApiException.notFound("Annonce"));
    }

    private Listing owned(AuthUser me, UUID id) {
        Listing l = listing(id);
        if (!l.getAuthorId().equals(me.id())) {
            throw ApiException.forbidden("Seul l'auteur modifie son annonce");
        }
        return l;
    }

    private static PhotoDto photoDto(Listing l, Photo p) {
        return new PhotoDto(p.getId(), p.getPosition(), "/api/classifieds/" + l.getId() + "/photos/" + p.getId());
    }

    private ListingSummaryDto summary(Listing l, Instant now) {
        return new ListingSummaryDto(l.getId(), l.getType(), l.getTitle(), l.getCategory(), l.getPrice(), l.effectiveStatus(now),
                userLookup.name(l.getAuthorId()), l.getPhotos().isEmpty() ? null : photoDto(l, l.getPhotos().get(0)), l.getCreatedAt());
    }

    private ListingDto toDto(Listing l, AuthUser me) {
        Instant now = clock.instant();
        return new ListingDto(l.getId(), l.getType(), l.getTitle(), l.getDescription(), l.getCategory(), l.getPrice(),
                l.effectiveStatus(now), l.isHidden(), l.getAuthorId(), userLookup.name(l.getAuthorId()),
                l.getPhotos().stream().map(p -> photoDto(l, p)).toList(), l.getExpiresAt(), l.getAuthorId().equals(me.id()),
                l.getCreatedAt());
    }
}
