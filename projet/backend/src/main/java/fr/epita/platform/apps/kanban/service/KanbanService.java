package fr.epita.platform.apps.kanban.service;

import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardSummaryDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.CardDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.CardRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.ColumnDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.ColumnRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.MemberDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.MoveDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.MyCardDto;
import fr.epita.platform.apps.kanban.entity.Board;
import fr.epita.platform.apps.kanban.entity.BoardColumn;
import fr.epita.platform.apps.kanban.entity.Card;
import fr.epita.platform.apps.kanban.entity.CardMove;
import fr.epita.platform.apps.kanban.repository.BoardColumnRepository;
import fr.epita.platform.apps.kanban.repository.BoardRepository;
import fr.epita.platform.apps.kanban.repository.CardMoveRepository;
import fr.epita.platform.apps.kanban.repository.CardRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KanbanService {

    static final List<String> DEFAULT_COLUMNS = List.of("À faire", "En cours", "Terminé");

    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final CardRepository cards;
    private final CardMoveRepository moves;
    private final UserLookup userLookup;
    private final fr.epita.platform.user.repository.UserRepository users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<fr.epita.platform.user.dto.UserDtos.UserSummary> candidates() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .map(fr.epita.platform.user.dto.UserDtos.UserSummary::of).toList();
    }

    // ---------- tableaux ----------

    @Transactional(readOnly = true)
    public List<BoardSummaryDto> myBoards(AuthUser me) {
        return boards.findAllByOrderByNameAsc().stream().filter(b -> b.isMember(me.id())).map(b -> {
            List<UUID> cols = columns.findByBoardIdOrderByPositionAsc(b.getId()).stream().map(BoardColumn::getId).toList();
            long count = cols.isEmpty() ? 0 : cards.findByColumnIdInOrderByPositionAsc(cols).size();
            return new BoardSummaryDto(b.getId(), b.getName(), userLookup.name(b.getOwnerId()), members(b).size(), count);
        }).toList();
    }

    @Transactional
    public BoardDto create(AuthUser me, BoardRequest req) {
        Board b = new Board();
        b.setOwnerId(me.id());
        applyBoard(b, req);
        boards.saveAndFlush(b);
        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            BoardColumn c = new BoardColumn();
            c.setBoardId(b.getId());
            c.setName(DEFAULT_COLUMNS.get(i));
            c.setPosition(i);
            columns.save(c);
        }
        columns.flush();
        return toDto(b, me);
    }

    @Transactional(readOnly = true)
    public BoardDto get(AuthUser me, UUID id) {
        return toDto(memberBoard(me, id), me);
    }

    @Transactional
    public BoardDto update(AuthUser me, UUID id, BoardRequest req) {
        Board b = ownedBoard(me, id);
        applyBoard(b, req);
        // un membre retiré n'est plus assigné aux cartes du tableau
        Set<UUID> allowed = new HashSet<>(b.getMemberIds());
        allowed.add(b.getOwnerId());
        boardCards(b.getId()).stream().filter(c -> c.getAssigneeId() != null && !allowed.contains(c.getAssigneeId()))
                .forEach(c -> c.setAssigneeId(null));
        return toDto(boards.saveAndFlush(b), me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Board b = ownedBoard(me, id);
        cards.deleteAll(boardCards(b.getId()));
        cards.flush();
        boards.delete(b);
    }

    // ---------- colonnes ----------

    @Transactional
    public BoardDto addColumn(AuthUser me, UUID boardId, ColumnRequest req) {
        Board b = memberBoard(me, boardId);
        List<BoardColumn> list = new ArrayList<>(columns.findByBoardIdOrderByPositionAsc(boardId));
        BoardColumn c = new BoardColumn();
        c.setBoardId(boardId);
        c.setName(req.name().trim());
        c.setWipLimit(req.wipLimit());
        int pos = req.position() == null ? list.size() : Math.min(req.position(), list.size());
        list.add(pos, c);
        renumberColumns(list);
        columns.saveAllAndFlush(list);
        return toDto(b, me);
    }

    @Transactional
    public BoardDto updateColumn(AuthUser me, UUID columnId, ColumnRequest req) {
        BoardColumn c = column(columnId);
        Board b = memberBoard(me, c.getBoardId());
        long count = cards.countByColumnId(columnId);
        if (req.wipLimit() != null && req.wipLimit() < count) {
            throw ApiException.conflict("WIP_LIMIT_REACHED", "La colonne contient déjà " + count + " cartes");
        }
        c.setName(req.name().trim());
        c.setWipLimit(req.wipLimit());
        if (req.position() != null) {
            List<BoardColumn> list = new ArrayList<>(columns.findByBoardIdOrderByPositionAsc(b.getId()));
            list.removeIf(x -> x.getId().equals(columnId));
            list.add(Math.min(req.position(), list.size()), c);
            renumberColumns(list);
            columns.saveAllAndFlush(list);
        }
        return toDto(b, me);
    }

    @Transactional
    public BoardDto deleteColumn(AuthUser me, UUID columnId) {
        BoardColumn c = column(columnId);
        Board b = memberBoard(me, c.getBoardId());
        if (cards.countByColumnId(columnId) > 0) {
            throw ApiException.conflict("COLUMN_NOT_EMPTY", "Videz la colonne avant de la supprimer");
        }
        columns.delete(c);
        columns.flush();
        List<BoardColumn> list = new ArrayList<>(columns.findByBoardIdOrderByPositionAsc(b.getId()));
        renumberColumns(list);
        columns.saveAllAndFlush(list);
        return toDto(b, me);
    }

    // ---------- cartes ----------

    @Transactional
    public CardDto addCard(AuthUser me, UUID columnId, CardRequest req) {
        BoardColumn col = column(columnId);
        Board b = lockMemberBoard(me, col.getBoardId());
        checkWip(col, null);
        Card card = new Card();
        card.setColumnId(columnId);
        applyCard(b, card, req);
        card.setPosition((int) cards.countByColumnId(columnId));
        cards.saveAndFlush(card);
        record(card, null, col, me);
        return toDto(card, LocalDate.now(clock), lastColumnId(b.getId()));
    }

    @Transactional
    public CardDto updateCard(AuthUser me, UUID cardId, CardRequest req) {
        Card card = card(cardId);
        Board b = memberBoard(me, column(card.getColumnId()).getBoardId());
        applyCard(b, card, req);
        return toDto(card, LocalDate.now(clock), lastColumnId(b.getId()));
    }

    @Transactional
    public void deleteCard(AuthUser me, UUID cardId) {
        Card card = card(cardId);
        BoardColumn col = column(card.getColumnId());
        lockMemberBoard(me, col.getBoardId());
        cards.delete(card);
        cards.flush();
        List<Card> rest = new ArrayList<>(cards.findByColumnIdOrderByPositionAsc(col.getId()));
        renumberCards(rest);
        cards.saveAllAndFlush(rest);
    }

    /**
     * Déplacement par glisser-déposer : refusé si la colonne cible est pleine (WIP) ; les positions des autres cartes
     * des deux colonnes sont recalculées dans la même transaction, sous verrou du tableau (pas de trou ni de doublon).
     */
    @Transactional
    public BoardDto move(AuthUser me, UUID cardId, UUID targetColumnId, int position) {
        Card card = card(cardId);
        BoardColumn from = column(card.getColumnId());
        BoardColumn to = column(targetColumnId);
        if (!from.getBoardId().equals(to.getBoardId())) {
            throw ValidationException.field("columnId", "doit appartenir au même tableau");
        }
        Board b = lockMemberBoard(me, from.getBoardId());
        boolean sameColumn = from.getId().equals(to.getId());
        if (!sameColumn) {
            checkWip(to, card.getId());
        }
        List<Card> source = new ArrayList<>(cards.findByColumnIdOrderByPositionAsc(from.getId()));
        source.removeIf(c -> c.getId().equals(cardId));
        List<Card> target = sameColumn ? source : new ArrayList<>(cards.findByColumnIdOrderByPositionAsc(to.getId()));
        target.add(Math.min(position, target.size()), card);
        card.setColumnId(to.getId());
        renumberCards(source);
        if (!sameColumn) {
            renumberCards(target);
            cards.saveAll(target);
        }
        cards.saveAllAndFlush(source);
        cards.saveAndFlush(card);
        if (!sameColumn) {
            record(card, from, to, me);
        }
        return toDto(b, me);
    }

    @Transactional(readOnly = true)
    public List<MoveDto> history(AuthUser me, UUID cardId) {
        Card card = card(cardId);
        memberBoard(me, column(card.getColumnId()).getBoardId());
        return moves.findByCardIdOrderByCreatedAtAsc(cardId).stream()
                .map(m -> new MoveDto(m.getFromName(), m.getToName(), userLookup.name(m.getAuthorId()), m.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyCardDto> myCards(AuthUser me) {
        LocalDate today = LocalDate.now(clock);
        List<MyCardDto> out = new ArrayList<>();
        for (Card c : cards.findByAssigneeId(me.id())) {
            BoardColumn col = column(c.getColumnId());
            Board b = boards.findById(col.getBoardId()).orElseThrow();
            if (b.isMember(me.id())) {
                out.add(new MyCardDto(b.getId(), b.getName(), col.getName(), toDto(c, today, lastColumnId(b.getId()))));
            }
        }
        return out;
    }

    // ---------- outils ----------

    private void checkWip(BoardColumn col, UUID ignoredCard) {
        if (col.getWipLimit() == null) {
            return;
        }
        long count = cards.findByColumnIdOrderByPositionAsc(col.getId()).stream()
                .filter(c -> !c.getId().equals(ignoredCard)).count();
        if (count >= col.getWipLimit()) {
            throw ApiException.conflict("WIP_LIMIT_REACHED", "La colonne « " + col.getName() + " » est pleine (" + col.getWipLimit() + " cartes)");
        }
    }

    private void record(Card card, BoardColumn from, BoardColumn to, AuthUser me) {
        CardMove m = new CardMove();
        m.setCardId(card.getId());
        m.setFromColumnId(from == null ? null : from.getId());
        m.setFromName(from == null ? null : from.getName());
        m.setToColumnId(to.getId());
        m.setToName(to.getName());
        m.setAuthorId(me.id());
        moves.saveAndFlush(m);
    }

    private void applyBoard(Board b, BoardRequest req) {
        req.memberIds().forEach(userLookup::require);
        b.setName(req.name().trim());
        Set<UUID> members = new HashSet<>(req.memberIds());
        members.remove(b.getOwnerId());
        b.setMemberIds(members);
    }

    private void applyCard(Board b, Card card, CardRequest req) {
        if (req.assigneeId() != null && !b.isMember(req.assigneeId())) {
            throw ValidationException.field("assigneeId", "doit être membre du tableau");
        }
        card.setTitle(req.title().trim());
        card.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        card.setAssigneeId(req.assigneeId());
        card.setDueDate(req.dueDate());
        card.setLabels(req.labels() == null ? "" : req.labels().stream().map(String::trim).distinct().collect(Collectors.joining(",")));
    }

    private static void renumberColumns(List<BoardColumn> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setPosition(i);
        }
    }

    private static void renumberCards(List<Card> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setPosition(i);
        }
    }

    private List<Card> boardCards(UUID boardId) {
        List<UUID> cols = columns.findByBoardIdOrderByPositionAsc(boardId).stream().map(BoardColumn::getId).toList();
        return cols.isEmpty() ? List.of() : cards.findByColumnIdInOrderByPositionAsc(cols);
    }

    private UUID lastColumnId(UUID boardId) {
        List<BoardColumn> cols = columns.findByBoardIdOrderByPositionAsc(boardId);
        return cols.isEmpty() ? null : cols.get(cols.size() - 1).getId();
    }

    private Board memberBoard(AuthUser me, UUID id) {
        Board b = boards.findById(id).orElseThrow(() -> ApiException.notFound("Tableau"));
        if (!b.isMember(me.id())) {
            throw ApiException.forbidden("Réservé aux membres du tableau");
        }
        return b;
    }

    private Board lockMemberBoard(AuthUser me, UUID id) {
        Board b = boards.lock(id).orElseThrow(() -> ApiException.notFound("Tableau"));
        if (!b.isMember(me.id())) {
            throw ApiException.forbidden("Réservé aux membres du tableau");
        }
        return b;
    }

    private Board ownedBoard(AuthUser me, UUID id) {
        Board b = memberBoard(me, id);
        if (!b.getOwnerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé au propriétaire du tableau");
        }
        return b;
    }

    private BoardColumn column(UUID id) {
        return columns.findById(id).orElseThrow(() -> ApiException.notFound("Colonne"));
    }

    private Card card(UUID id) {
        return cards.findById(id).orElseThrow(() -> ApiException.notFound("Carte"));
    }

    private List<MemberDto> members(Board b) {
        List<UUID> ids = new ArrayList<>();
        ids.add(b.getOwnerId());
        ids.addAll(b.getMemberIds());
        Map<UUID, String> names = userLookup.names(ids);
        return ids.stream().map(id -> new MemberDto(id, names.get(id), id.equals(b.getOwnerId())))
                .sorted((x, y) -> Boolean.compare(y.owner(), x.owner())).toList();
    }

    private CardDto toDto(Card c, LocalDate today, UUID lastColumnId) {
        boolean overdue = c.getDueDate() != null && c.getDueDate().isBefore(today) && !c.getColumnId().equals(lastColumnId);
        return new CardDto(c.getId(), c.getColumnId(), c.getTitle(), c.getDescription(), c.getAssigneeId(),
                userLookup.name(c.getAssigneeId()), c.getDueDate(), c.labelList(), c.getPosition(), overdue);
    }

    private BoardDto toDto(Board b, AuthUser me) {
        List<BoardColumn> cols = columns.findByBoardIdOrderByPositionAsc(b.getId());
        UUID last = cols.isEmpty() ? null : cols.get(cols.size() - 1).getId();
        LocalDate today = LocalDate.now(clock);
        Map<UUID, List<Card>> byColumn = cols.isEmpty() ? Map.of()
                : cards.findByColumnIdInOrderByPositionAsc(cols.stream().map(BoardColumn::getId).toList()).stream()
                .collect(Collectors.groupingBy(Card::getColumnId));
        List<ColumnDto> list = cols.stream().map(c -> new ColumnDto(c.getId(), c.getName(), c.getPosition(), c.getWipLimit(),
                byColumn.getOrDefault(c.getId(), List.of()).stream()
                        .sorted(java.util.Comparator.comparingInt(Card::getPosition))
                        .map(card -> toDto(card, today, last)).toList())).toList();
        return new BoardDto(b.getId(), b.getName(), b.getOwnerId(), members(b), list, b.getOwnerId().equals(me.id()));
    }
}
