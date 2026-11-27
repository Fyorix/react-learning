package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.library.dto.LibraryDtos.BookDto;
import fr.epita.platform.apps.library.dto.LibraryDtos.BookRequest;
import fr.epita.platform.apps.library.dto.LibraryDtos.BookStatus;
import fr.epita.platform.apps.library.dto.LibraryDtos.LoanDto;
import fr.epita.platform.apps.library.service.LibraryService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryServiceTest extends IntegrationTest {

    @Autowired
    LibraryService service;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
    }

    private BookDto book(String title) {
        return service.create(admin, new BookRequest(title, "Auteur", "Roman", null));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void borrowingSetsA14DaysDueDateAndMakesTheBookUnavailable() {
        BookDto b = book("Dune");
        LoanDto loan = service.borrow(alice, b.id());
        assertThat(loan.dueDate()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(service.get(bob, b.id()).status()).isEqualTo(BookStatus.LOANED);
        assertCode(() -> service.borrow(bob, b.id()), "BOOK_UNAVAILABLE");
    }

    @Test
    void atMostThreeActiveLoans() {
        for (int i = 0; i < 3; i++) {
            service.borrow(alice, book("Livre " + i).id());
        }
        BookDto fourth = book("Livre 4");
        assertCode(() -> service.borrow(alice, fourth.id()), "LOAN_LIMIT_REACHED");
    }

    @Test
    void overdueUsersCannotBorrow() {
        LoanDto loan = service.borrow(alice, book("Dune").id());
        jdbc.update("update library_loans set due_date = ? where id = ?", LocalDate.now().minusDays(1), loan.id());
        BookDto other = book("Fondation");
        assertCode(() -> service.borrow(alice, other.id()), "OVERDUE_LOANS");
    }

    @Test
    void oneExtensionOnlyAndNotWhenSomeoneIsWaiting() {
        BookDto b = book("Dune");
        LoanDto loan = service.borrow(alice, b.id());
        assertThat(service.extend(alice, loan.id()).dueDate()).isEqualTo(LocalDate.now().plusDays(21));
        assertCode(() -> service.extend(alice, loan.id()), "EXTENSION_NOT_ALLOWED");

        BookDto b2 = book("Fondation");
        LoanDto loan2 = service.borrow(alice, b2.id());
        service.joinQueue(bob, b2.id());
        assertCode(() -> service.extend(alice, loan2.id()), "EXTENSION_NOT_ALLOWED");
    }

    @Test
    void returnedBookIsReservedForTheFirstInQueue() {
        AuthUser carol = auth(user("Carol"));
        BookDto b = book("Dune");
        LoanDto loan = service.borrow(alice, b.id());
        service.joinQueue(bob, b.id());
        service.joinQueue(carol, b.id());
        assertCode(() -> service.joinQueue(bob, b.id()), "ALREADY_IN_QUEUE");
        assertCode(() -> service.joinQueue(alice, b.id()), "ALREADY_BORROWED");

        service.returnLoan(alice, loan.id());
        assertThat(service.get(carol, b.id()).status()).isEqualTo(BookStatus.RESERVED);
        assertThat(service.get(bob, b.id()).reservedForMe()).isTrue();
        assertCode(() -> service.borrow(carol, b.id()), "BOOK_UNAVAILABLE");
        service.borrow(bob, b.id());
        assertThat(service.get(carol, b.id()).myQueuePosition()).isEqualTo(1);
    }

    @Test
    void expiredReservationPassesToTheNextInQueue() {
        AuthUser carol = auth(user("Carol"));
        BookDto b = book("Dune");
        LoanDto loan = service.borrow(alice, b.id());
        service.joinQueue(bob, b.id());
        service.joinQueue(carol, b.id());
        service.returnLoan(alice, loan.id());
        jdbc.update("update library_queue set reserved_until = now() - interval '1 hour' where user_id = ?", bob.id());

        service.borrow(carol, b.id());
        assertThat(service.get(bob, b.id()).myQueuePosition()).isNull();
    }

    @Test
    void bookWithActiveLoanCannotBeDeleted() {
        BookDto b = book("Dune");
        service.borrow(alice, b.id());
        assertCode(() -> service.delete(admin, b.id()), "BOOK_HAS_ACTIVE_LOAN");
    }
}
