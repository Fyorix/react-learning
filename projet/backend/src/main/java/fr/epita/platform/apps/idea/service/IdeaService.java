package fr.epita.platform.apps.idea.service;

import fr.epita.platform.apps.idea.dto.IdeaDtos.CategoryDto;
import fr.epita.platform.apps.idea.dto.IdeaDtos.CommentDto;
import fr.epita.platform.apps.idea.dto.IdeaDtos.IdeaDto;
import fr.epita.platform.apps.idea.dto.IdeaDtos.IdeaRequest;
import fr.epita.platform.apps.idea.dto.IdeaDtos.StatusRequest;
import fr.epita.platform.apps.idea.entity.Idea;
import fr.epita.platform.apps.idea.entity.IdeaCategory;
import fr.epita.platform.apps.idea.entity.IdeaComment;
import fr.epita.platform.apps.idea.entity.IdeaStatus;
import fr.epita.platform.apps.idea.entity.IdeaVote;
import fr.epita.platform.apps.idea.repository.IdeaCommentRepository;
import fr.epita.platform.apps.idea.repository.IdeaRepository;
import fr.epita.platform.apps.idea.repository.IdeaVoteRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IdeaService {

    private final IdeaRepository ideas;
    private final IdeaVoteRepository votes;
    private final IdeaCommentRepository comments;
    private final UserLookup userLookup;
    private final Clock clock;

    @Transactional
    public IdeaDto create(AuthUser me, IdeaRequest req) {
        Idea idea = new Idea();
        idea.setAuthorId(me.id());
        apply(idea, req);
        return toDto(ideas.saveAndFlush(idea), me);
    }

    @Transactional(readOnly = true)
    public PageResponse<IdeaDto> list(AuthUser me, IdeaStatus status, IdeaCategory category, String sort,
                                      String search, int page, int size) {
        Sort order = "votes".equals(sort)
                ? Sort.by(Sort.Order.desc("voteCount"), Sort.Order.desc("createdAt"))
                : Sort.by(Sort.Order.desc("createdAt"));
        String term = search == null || search.isBlank() ? null : search.trim();
        Page<Idea> result = ideas.search(status, category, term,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), order));
        Set<UUID> voted = votes.findByIdUserIdAndIdIdeaIdIn(me.id(), result.map(Idea::getId).toList()).stream()
                .map(v -> v.getId().getIdeaId()).collect(Collectors.toSet());
        Map<UUID, String> names = userLookup.namesOf(result.getContent(), Idea::getAuthorId);
        return PageResponse.of(result, i -> toDto(i, me, voted.contains(i.getId()), names.get(i.getAuthorId())));
    }

    @Transactional(readOnly = true)
    public IdeaDto get(AuthUser me, UUID id) {
        return toDto(find(id), me);
    }

    /** L'auteur peut modifier son idée tant qu'elle est au statut NEW. */
    @Transactional
    public IdeaDto update(AuthUser me, UUID id, IdeaRequest req) {
        Idea idea = find(id);
        if (!idea.getAuthorId().equals(me.id())) {
            throw ApiException.forbidden("Seul l'auteur peut modifier son idée");
        }
        if (idea.getStatus() != IdeaStatus.NEW) {
            throw ApiException.conflict("IDEA_LOCKED", "L'idée n'est plus modifiable une fois en cours d'étude");
        }
        apply(idea, req);
        return toDto(ideas.saveAndFlush(idea), me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Idea idea = find(id);
        me.requireOwnerOrAdmin(idea.getAuthorId());
        ideas.delete(idea);
    }

    /** Un vote par utilisateur et par idée, jamais pour sa propre idée. */
    @Transactional
    public IdeaDto vote(AuthUser me, UUID id) {
        Idea idea = find(id);
        if (idea.getAuthorId().equals(me.id())) {
            throw ApiException.conflict("OWN_IDEA_VOTE", "Impossible de voter pour sa propre idée");
        }
        IdeaVote.Key key = new IdeaVote.Key(id, me.id());
        if (votes.existsById(key)) {
            throw ApiException.conflict("ALREADY_VOTED", "Vous avez déjà voté pour cette idée");
        }
        votes.saveAndFlush(new IdeaVote(id, me.id(), clock.instant()));
        idea.setVoteCount(idea.getVoteCount() + 1);
        return toDto(idea, me);
    }

    @Transactional
    public IdeaDto unvote(AuthUser me, UUID id) {
        Idea idea = find(id);
        IdeaVote.Key key = new IdeaVote.Key(id, me.id());
        if (!votes.existsById(key)) {
            throw ApiException.notFound("Vote");
        }
        votes.deleteById(key);
        votes.flush();
        idea.setVoteCount(Math.max(0, idea.getVoteCount() - 1));
        return toDto(idea, me);
    }

    @Transactional
    public IdeaDto changeStatus(AuthUser me, UUID id, StatusRequest req) {
        me.requireAdmin();
        Idea idea = find(id);
        if (!idea.getStatus().canMoveTo(req.status())) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Transition impossible : " + idea.getStatus() + " → " + req.status());
        }
        idea.setStatus(req.status());
        idea.setStatusComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
        return toDto(ideas.saveAndFlush(idea), me);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> comments(UUID id) {
        find(id);
        List<IdeaComment> list = comments.findByIdeaIdOrderByCreatedAtAsc(id);
        Map<UUID, String> names = userLookup.namesOf(list, IdeaComment::getAuthorId);
        return list.stream().map(c -> new CommentDto(c.getId(), c.getAuthorId(), names.get(c.getAuthorId()),
                c.getContent(), c.getCreatedAt())).toList();
    }

    @Transactional
    public CommentDto addComment(AuthUser me, UUID id, String content) {
        find(id);
        IdeaComment comment = new IdeaComment();
        comment.setIdeaId(id);
        comment.setAuthorId(me.id());
        comment.setContent(content.trim());
        comment = comments.saveAndFlush(comment);
        return new CommentDto(comment.getId(), me.id(), userLookup.name(me.id()), comment.getContent(),
                comment.getCreatedAt());
    }

    public List<CategoryDto> categories() {
        return Arrays.stream(IdeaCategory.values()).map(c -> new CategoryDto(c, c.label())).toList();
    }

    private void apply(Idea idea, IdeaRequest req) {
        idea.setTitle(req.title().trim());
        idea.setDescription(req.description().trim());
        idea.setCategory(req.category());
    }

    private Idea find(UUID id) {
        return ideas.findById(id).orElseThrow(() -> ApiException.notFound("Idée"));
    }

    private IdeaDto toDto(Idea idea, AuthUser me) {
        boolean voted = votes.existsById(new IdeaVote.Key(idea.getId(), me.id()));
        return toDto(idea, me, voted, userLookup.name(idea.getAuthorId()));
    }

    private IdeaDto toDto(Idea idea, AuthUser me, boolean voted, String authorName) {
        boolean canEdit = idea.getAuthorId().equals(me.id()) && idea.getStatus() == IdeaStatus.NEW;
        return new IdeaDto(idea.getId(), idea.getTitle(), idea.getDescription(), idea.getCategory(),
                idea.getCategory().label(), idea.getStatus(), idea.getStatusComment(), idea.getAuthorId(), authorName,
                idea.getVoteCount(), voted, canEdit, comments.countByIdeaId(idea.getId()), idea.getCreatedAt(),
                idea.getUpdatedAt());
    }
}
