package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.CardDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.CardRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.ColumnRequest;
import fr.epita.platform.apps.kanban.service.KanbanService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KanbanServiceTest extends IntegrationTest {

    @Autowired
    KanbanService service;

    AuthUser alice;
    AuthUser bob;
    AuthUser eve;
    BoardDto board;
    UUID todo;
    UUID doing;
    UUID done;

    @BeforeEach
    void setUp() {
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        eve = auth(user("Eve"));
        board = service.create(alice, new BoardRequest("Sprint", List.of(bob.id())));
        todo = board.columns().get(0).id();
        doing = board.columns().get(1).id();
        done = board.columns().get(2).id();
    }

    private CardDto card(UUID column, String title) {
        return service.addCard(alice, column, new CardRequest(title, null, null, null, List.of()));
    }

    private List<String> titles(BoardDto b, int column) {
        return b.columns().get(column).cards().stream().map(CardDto::title).toList();
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void newBoardHasDefaultColumnsAndOnlyMembersSeeIt() {
        assertThat(board.columns()).extracting("name").containsExactly("À faire", "En cours", "Terminé");
        assertThat(service.get(bob, board.id()).name()).isEqualTo("Sprint");
        assertCode(() -> service.get(eve, board.id()), "FORBIDDEN");
        assertThat(service.myBoards(eve)).isEmpty();
    }

    @Test
    void movesKeepPositionsContiguous() {
        CardDto a = card(todo, "A");
        card(todo, "B");
        CardDto c = card(todo, "C");
        BoardDto b = service.move(bob, c.id(), todo, 0);
        assertThat(titles(b, 0)).containsExactly("C", "A", "B");
        b = service.move(bob, a.id(), doing, 5);
        assertThat(titles(b, 0)).containsExactly("C", "B");
        assertThat(titles(b, 1)).containsExactly("A");
        assertThat(b.columns().get(0).cards()).extracting(CardDto::position).containsExactly(0, 1);
        assertThat(service.history(alice, a.id())).extracting("toColumn").containsExactly("À faire", "En cours");
    }

    @Test
    void wipLimitRefusesDrops() {
        service.updateColumn(alice, doing, new ColumnRequest("En cours", 1, null));
        CardDto a = card(todo, "A");
        CardDto b = card(todo, "B");
        assertCode(() -> service.updateColumn(alice, todo, new ColumnRequest("À faire", 1, null)), "WIP_LIMIT_REACHED");
        service.move(alice, a.id(), doing, 0);
        assertCode(() -> service.move(alice, b.id(), doing, 0), "WIP_LIMIT_REACHED");
        assertCode(() -> service.addCard(alice, doing, new CardRequest("C", null, null, null, List.of())), "WIP_LIMIT_REACHED");
        // réordonner dans une colonne pleine reste possible
        service.move(alice, a.id(), doing, 0);
    }

    @Test
    void nonEmptyColumnsCannotBeDeletedAndAssigneesMustBeMembers() {
        card(done, "Fini");
        assertCode(() -> service.deleteColumn(alice, done), "COLUMN_NOT_EMPTY");
        assertThatThrownBy(() -> service.addCard(alice, todo, new CardRequest("X", null, eve.id(), null, List.of())))
                .isInstanceOf(ValidationException.class);
        CardDto mine = service.addCard(alice, todo, new CardRequest("Pour Bob", null, bob.id(), null, List.of("front")));
        assertThat(service.myCards(bob)).extracting(x -> x.card().id()).containsExactly(mine.id());
    }

    @Test
    void onlyTheOwnerManagesTheBoard() {
        assertCode(() -> service.update(bob, board.id(), new BoardRequest("Renommé", List.of())), "FORBIDDEN");
        card(todo, "A");
        service.delete(alice, board.id());
        assertThat(service.myBoards(alice)).isEmpty();
    }
}
