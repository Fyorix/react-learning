package fr.epita.platform.apps.poll.service;

import fr.epita.platform.apps.poll.dto.PollDtos.OptionDto;
import fr.epita.platform.apps.poll.dto.PollDtos.OptionResultDto;
import fr.epita.platform.apps.poll.dto.PollDtos.PollDto;
import fr.epita.platform.apps.poll.dto.PollDtos.PollRequest;
import fr.epita.platform.apps.poll.dto.PollDtos.PollResultsDto;
import fr.epita.platform.apps.poll.dto.PollDtos.PollSummaryDto;
import fr.epita.platform.apps.poll.entity.Poll;
import fr.epita.platform.apps.poll.entity.PollOption;
import fr.epita.platform.apps.poll.entity.PollVote;
import fr.epita.platform.apps.poll.repository.PollRepository;
import fr.epita.platform.apps.poll.repository.PollVoteRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PollService {

    private final PollRepository polls;
    private final PollVoteRepository votes;
    private final UserLookup userLookup;
    private final Clock clock;

    @Transactional
    public PollDto create(AuthUser me, PollRequest req) {
        validate(req);
        Poll poll = new Poll();
        poll.setCreatorId(me.id());
        apply(poll, req);
        return toDto(polls.saveAndFlush(poll), me);
    }

    @Transactional(readOnly = true)
    public PageResponse<PollSummaryDto> list(AuthUser me, String status, boolean mine, int page, int size) {
        Instant now = clock.instant();
        List<Poll> all = polls.findAllByOrderByCreatedAtDesc().stream()
                .filter(p -> status == null || ("open".equals(status) == p.isOpen(now)))
                .filter(p -> !mine || p.getCreatorId().equals(me.id()))
                .toList();
        List<PollVote> allVotes = votes.findByPollIdIn(all.stream().map(Poll::getId).toList());
        Map<UUID, Set<UUID>> votersByPoll = allVotes.stream().collect(Collectors.groupingBy(PollVote::getPollId,
                Collectors.mapping(PollVote::getUserId, Collectors.toSet())));
        Map<UUID, String> names = userLookup.namesOf(all, Poll::getCreatorId);
        List<PollSummaryDto> content = all.stream().map(p -> {
            Set<UUID> voters = votersByPoll.getOrDefault(p.getId(), Set.of());
            return new PollSummaryDto(p.getId(), p.getQuestion(), p.getCreatorId(), names.get(p.getCreatorId()),
                    p.isOpen(now), p.getClosesAt(), p.isMultipleChoice(), voters.size(), voters.contains(me.id()),
                    p.getCreatedAt());
        }).toList();
        return PageResponse.ofList(content, page, size);
    }

    @Transactional(readOnly = true)
    public PollDto get(AuthUser me, UUID id) {
        return toDto(find(id), me);
    }

    /** Modification possible seulement tant qu'il n'y a aucun vote. */
    @Transactional
    public PollDto update(AuthUser me, UUID id, PollRequest req) {
        Poll poll = find(id);
        me.requireOwnerOrAdmin(poll.getCreatorId());
        if (votes.existsByPollId(id)) {
            throw ApiException.conflict("POLL_HAS_VOTES", "Le sondage a déjà reçu des votes : il n'est plus modifiable");
        }
        validate(req);
        apply(poll, req);
        return toDto(polls.saveAndFlush(poll), me);
    }

    @Transactional
    public PollDto close(AuthUser me, UUID id) {
        Poll poll = find(id);
        me.requireOwnerOrAdmin(poll.getCreatorId());
        if (poll.getClosedAt() == null) {
            poll.setClosedAt(clock.instant());
        }
        return toDto(poll, me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Poll poll = find(id);
        me.requireOwnerOrAdmin(poll.getCreatorId());
        polls.delete(poll);
    }

    /** Un vote par utilisateur et par sondage, modifiable tant que le sondage est ouvert. */
    @Transactional
    public PollDto vote(AuthUser me, UUID id, List<UUID> optionIds) {
        Poll poll = polls.lockById(id).orElseThrow(() -> ApiException.notFound("Sondage"));
        requireOpen(poll);
        Set<UUID> chosen = new LinkedHashSet<>(optionIds);
        if (!poll.isMultipleChoice() && chosen.size() != 1) {
            throw ApiException.badRequest("INVALID_VOTE", "Ce sondage n'accepte qu'un seul choix");
        }
        Set<UUID> valid = poll.getOptions().stream().map(PollOption::getId).collect(Collectors.toSet());
        if (!valid.containsAll(chosen)) {
            throw ApiException.badRequest("INVALID_VOTE", "Option inconnue pour ce sondage");
        }
        votes.deleteVote(id, me.id());
        votes.flush();
        Instant now = clock.instant();
        chosen.forEach(optionId -> votes.save(new PollVote(id, optionId, me.id(), now)));
        votes.flush();
        return toDto(poll, me);
    }

    @Transactional
    public void removeVote(AuthUser me, UUID id) {
        Poll poll = find(id);
        requireOpen(poll);
        if (votes.deleteVote(id, me.id()) == 0) {
            throw ApiException.notFound("Vote");
        }
    }

    @Transactional(readOnly = true)
    public PollResultsDto results(AuthUser me, UUID id) {
        Poll poll = find(id);
        if (!canSeeResults(poll, me, hasVoted(poll.getId(), me.id()))) {
            throw ApiException.forbidden("Les résultats seront visibles après votre vote ou à la clôture");
        }
        List<PollVote> all = votes.findByPollId(id);
        long voters = all.stream().map(PollVote::getUserId).distinct().count();
        Map<UUID, Long> counts = all.stream().collect(Collectors.groupingBy(PollVote::getOptionId, Collectors.counting()));
        List<OptionResultDto> options = poll.getOptions().stream().map(o -> {
            long n = counts.getOrDefault(o.getId(), 0L);
            double pct = voters == 0 ? 0 : Math.round(n * 1000.0 / voters) / 10.0;
            return new OptionResultDto(o.getId(), o.getLabel(), n, pct);
        }).toList();
        return new PollResultsDto(poll.getId(), poll.getQuestion(), poll.isOpen(clock.instant()), voters, options);
    }

    private void validate(PollRequest req) {
        Set<String> labels = new HashSet<>();
        var collector = new ValidationException.Collector();
        for (String label : req.options()) {
            collector.check(labels.add(label.trim().toLowerCase()), "options", "Les options doivent être distinctes");
        }
        collector.check(req.closesAt() == null || req.closesAt().isAfter(clock.instant()), "closesAt",
                "La date de clôture doit être dans le futur");
        collector.throwIfAny();
    }

    private void apply(Poll poll, PollRequest req) {
        poll.setQuestion(req.question().trim());
        poll.setMultipleChoice(req.multipleChoice());
        poll.setResultsVisibleBeforeVote(req.resultsVisibleBeforeVote());
        poll.setClosesAt(req.closesAt());
        poll.getOptions().clear();
        int position = 0;
        for (String label : req.options()) {
            PollOption option = new PollOption();
            option.setPoll(poll);
            option.setLabel(label.trim());
            option.setPosition(position++);
            poll.getOptions().add(option);
        }
    }

    private void requireOpen(Poll poll) {
        if (!poll.isOpen(clock.instant())) {
            throw ApiException.conflict("POLL_CLOSED", "Le sondage est clos");
        }
    }

    private boolean hasVoted(UUID pollId, UUID userId) {
        return !votes.findByPollIdAndUserId(pollId, userId).isEmpty();
    }

    private boolean canSeeResults(Poll poll, AuthUser me, boolean voted) {
        return poll.isResultsVisibleBeforeVote() || voted || !poll.isOpen(clock.instant())
                || me.isAdmin() || poll.getCreatorId().equals(me.id());
    }

    private Poll find(UUID id) {
        return polls.findById(id).orElseThrow(() -> ApiException.notFound("Sondage"));
    }

    private PollDto toDto(Poll poll, AuthUser me) {
        List<PollVote> all = votes.findByPollId(poll.getId());
        List<UUID> mine = all.stream().filter(v -> v.getUserId().equals(me.id())).map(PollVote::getOptionId).toList();
        long voters = all.stream().map(PollVote::getUserId).distinct().count();
        boolean canManage = me.isAdmin() || poll.getCreatorId().equals(me.id());
        return new PollDto(poll.getId(), poll.getQuestion(), poll.getCreatorId(), userLookup.name(poll.getCreatorId()),
                poll.isMultipleChoice(), poll.isResultsVisibleBeforeVote(), poll.getClosesAt(), poll.getClosedAt(),
                poll.isOpen(clock.instant()),
                poll.getOptions().stream().map(o -> new OptionDto(o.getId(), o.getLabel())).toList(),
                mine.isEmpty() ? null : mine, voters, canManage, canSeeResults(poll, me, !mine.isEmpty()),
                poll.getCreatedAt());
    }
}
