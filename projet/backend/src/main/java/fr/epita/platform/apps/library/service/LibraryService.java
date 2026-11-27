package fr.epita.platform.apps.library.service;

import fr.epita.platform.apps.library.dto.LibraryDtos.BookDto;
import fr.epita.platform.apps.library.dto.LibraryDtos.BookRequest;
import fr.epita.platform.apps.library.dto.LibraryDtos.BookStatus;
import fr.epita.platform.apps.library.dto.LibraryDtos.LoanDto;
import fr.epita.platform.apps.library.entity.Book;
import fr.epita.platform.apps.library.entity.Loan;
import fr.epita.platform.apps.library.entity.QueueEntry;
import fr.epita.platform.apps.library.repository.BookRepository;
import fr.epita.platform.apps.library.repository.LoanRepository;
import fr.epita.platform.apps.library.repository.QueueEntryRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    public static final int MAX_ACTIVE_LOANS = 3;
    public static final int LOAN_DAYS = 14;
    public static final int EXTENSION_DAYS = 7;
    public static final Duration RESERVATION = Duration.ofDays(3);

    private final BookRepository books;
    private final LoanRepository loans;
    private final QueueEntryRepository queue;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- catalogue ----------

    @Transactional
    public PageResponse<BookDto> list(AuthUser me, String search, String genre, Boolean available, int page, int size) {
        String term = search == null ? null : search.trim().toLowerCase();
        List<BookDto> all = books.findAllByOrderByTitleAsc().stream()
                .filter(b -> term == null || term.isEmpty() || b.getTitle().toLowerCase().contains(term)
                        || b.getAuthor().toLowerCase().contains(term))
                .filter(b -> genre == null || genre.isBlank() || b.getGenre().equalsIgnoreCase(genre))
                .map(b -> toDto(b, me))
                .filter(b -> available == null || b.available() == available)
                .toList();
        return PageResponse.ofList(all, page, size);
    }

    @Transactional
    public BookDto get(AuthUser me, UUID id) {
        return toDto(findBook(id), me);
    }

    @Transactional
    public BookDto create(AuthUser me, BookRequest req) {
        me.requireAdmin();
        Book book = new Book();
        apply(book, req);
        return toDto(books.saveAndFlush(book), me);
    }

    @Transactional
    public BookDto update(AuthUser me, UUID id, BookRequest req) {
        me.requireAdmin();
        Book book = findBook(id);
        apply(book, req);
        return toDto(books.saveAndFlush(book), me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        me.requireAdmin();
        Book book = findBook(id);
        if (loans.findByBookIdAndReturnedAtIsNull(id).isPresent()) {
            throw ApiException.conflict("BOOK_HAS_ACTIVE_LOAN", "Le livre est emprunté : impossible de le supprimer");
        }
        books.delete(book);
    }

    // ---------- emprunts ----------

    @Transactional
    public LoanDto borrow(AuthUser me, UUID bookId) {
        Book book = books.lockById(bookId).orElseThrow(() -> ApiException.notFound("Livre"));
        LocalDate today = LocalDate.now(clock);
        List<Loan> mine = loans.findByUserIdAndReturnedAtIsNull(me.id());
        if (mine.stream().anyMatch(l -> l.isOverdue(today))) {
            throw ApiException.conflict("OVERDUE_LOANS", "Vous avez un emprunt en retard : rendez-le avant d'emprunter");
        }
        if (mine.size() >= MAX_ACTIVE_LOANS) {
            throw ApiException.conflict("LOAN_LIMIT_REACHED",
                    "Vous avez déjà " + MAX_ACTIVE_LOANS + " emprunts en cours");
        }
        if (loans.findByBookIdAndReturnedAtIsNull(bookId).isPresent()) {
            throw ApiException.conflict("BOOK_UNAVAILABLE", "Le livre est déjà emprunté");
        }
        Optional<QueueEntry> head = refreshReservation(bookId);
        if (head.isPresent() && !head.get().getUserId().equals(me.id())) {
            throw ApiException.conflict("BOOK_UNAVAILABLE", "Le livre est réservé pour une autre personne");
        }
        head.ifPresent(queue::delete);
        queue.findByBookIdAndUserId(bookId, me.id()).ifPresent(queue::delete);

        Loan loan = new Loan();
        loan.setBookId(book.getId());
        loan.setUserId(me.id());
        loan.setBorrowedAt(clock.instant());
        loan.setDueDate(today.plusDays(LOAN_DAYS));
        return toDto(loans.saveAndFlush(loan), book, today);
    }

    @Transactional(readOnly = true)
    public List<LoanDto> myLoans(AuthUser me) {
        return toDtos(loans.findByUserIdOrderByBorrowedAtDesc(me.id()));
    }

    /** Au retour, le premier de la file reçoit une réservation de 3 jours. */
    @Transactional
    public LoanDto returnLoan(AuthUser me, UUID loanId) {
        Loan loan = findLoan(loanId);
        me.requireOwnerOrAdmin(loan.getUserId());
        if (!loan.isActive()) {
            throw ApiException.conflict("LOAN_ALREADY_RETURNED", "Ce livre a déjà été rendu");
        }
        books.lockById(loan.getBookId());
        loan.setReturnedAt(clock.instant());
        loans.saveAndFlush(loan);
        List<QueueEntry> waiting = queue.findByBookIdOrderByJoinedAtAsc(loan.getBookId());
        if (!waiting.isEmpty()) {
            waiting.getFirst().setReservedUntil(clock.instant().plus(RESERVATION));
        }
        return toDto(loan, findBook(loan.getBookId()), LocalDate.now(clock));
    }

    /** Une seule prolongation de 7 jours, refusée si quelqu'un attend le livre. */
    @Transactional
    public LoanDto extend(AuthUser me, UUID loanId) {
        Loan loan = findLoan(loanId);
        if (!loan.getUserId().equals(me.id())) {
            throw ApiException.forbidden("Seul l'emprunteur peut prolonger");
        }
        LocalDate today = LocalDate.now(clock);
        if (!loan.isActive() || loan.isExtended() || loan.isOverdue(today)) {
            throw ApiException.conflict("EXTENSION_NOT_ALLOWED", "Prolongation impossible pour cet emprunt");
        }
        if (!queue.findByBookIdOrderByJoinedAtAsc(loan.getBookId()).isEmpty()) {
            throw ApiException.conflict("EXTENSION_NOT_ALLOWED", "Quelqu'un attend ce livre : prolongation impossible");
        }
        loan.setExtended(true);
        loan.setDueDate(loan.getDueDate().plusDays(EXTENSION_DAYS));
        return toDto(loans.saveAndFlush(loan), findBook(loan.getBookId()), today);
    }

    // ---------- file d'attente ----------

    @Transactional
    public BookDto joinQueue(AuthUser me, UUID bookId) {
        Book book = books.lockById(bookId).orElseThrow(() -> ApiException.notFound("Livre"));
        Optional<Loan> active = loans.findByBookIdAndReturnedAtIsNull(bookId);
        if (active.isPresent() && active.get().getUserId().equals(me.id())) {
            throw ApiException.conflict("ALREADY_BORROWED", "Vous avez déjà ce livre");
        }
        if (queue.findByBookIdAndUserId(bookId, me.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_IN_QUEUE", "Vous êtes déjà dans la file d'attente");
        }
        if (active.isEmpty() && refreshReservation(bookId).isEmpty()) {
            throw ApiException.conflict("BOOK_AVAILABLE", "Le livre est disponible : empruntez-le directement");
        }
        QueueEntry entry = new QueueEntry();
        entry.setBookId(bookId);
        entry.setUserId(me.id());
        entry.setJoinedAt(clock.instant());
        queue.saveAndFlush(entry);
        return toDto(book, me);
    }

    @Transactional
    public BookDto leaveQueue(AuthUser me, UUID bookId) {
        Book book = findBook(bookId);
        QueueEntry entry = queue.findByBookIdAndUserId(bookId, me.id())
                .orElseThrow(() -> ApiException.notFound("Place dans la file"));
        boolean wasReserved = entry.getReservedUntil() != null;
        queue.delete(entry);
        queue.flush();
        if (wasReserved) {
            passReservationToNext(bookId);
        }
        return toDto(book, me);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanDto> adminLoans(boolean overdueOnly, int page, int size) {
        List<LoanDto> all = toDtos(loans.findAllByOrderByBorrowedAtDesc()).stream()
                .filter(l -> !overdueOnly || l.overdue())
                .toList();
        return PageResponse.ofList(all, page, size);
    }

    // ---------- interne ----------

    /**
     * Supprime les réservations expirées (le suivant de la file prend la main) et renvoie
     * la tête de file réservée, s'il y en a une.
     */
    private Optional<QueueEntry> refreshReservation(UUID bookId) {
        Instant now = clock.instant();
        while (true) {
            List<QueueEntry> waiting = queue.findByBookIdOrderByJoinedAtAsc(bookId);
            if (waiting.isEmpty()) {
                return Optional.empty();
            }
            QueueEntry head = waiting.getFirst();
            if (head.getReservedUntil() == null) {
                // livre libre avec une file : on réserve pour le premier
                head.setReservedUntil(now.plus(RESERVATION));
                return Optional.of(head);
            }
            if (head.getReservedUntil().isAfter(now)) {
                return Optional.of(head);
            }
            queue.delete(head);
            queue.flush();
        }
    }

    private void passReservationToNext(UUID bookId) {
        if (loans.findByBookIdAndReturnedAtIsNull(bookId).isEmpty()) {
            List<QueueEntry> waiting = queue.findByBookIdOrderByJoinedAtAsc(bookId);
            if (!waiting.isEmpty()) {
                waiting.getFirst().setReservedUntil(clock.instant().plus(RESERVATION));
            }
        }
    }

    private BookDto toDto(Book book, AuthUser me) {
        Instant now = clock.instant();
        Optional<Loan> active = loans.findByBookIdAndReturnedAtIsNull(book.getId());
        List<QueueEntry> waiting = queue.findByBookIdOrderByJoinedAtAsc(book.getId());
        QueueEntry head = waiting.isEmpty() ? null : waiting.getFirst();
        boolean reserved = active.isEmpty() && head != null
                && (head.getReservedUntil() == null || head.getReservedUntil().isAfter(now));
        BookStatus status = active.isPresent() ? BookStatus.LOANED : reserved ? BookStatus.RESERVED : BookStatus.AVAILABLE;
        boolean reservedForMe = reserved && head.getUserId().equals(me.id());
        Integer position = null;
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).getUserId().equals(me.id())) {
                position = i + 1;
            }
        }
        UUID myLoan = active.filter(l -> l.getUserId().equals(me.id())).map(Loan::getId).orElse(null);
        return new BookDto(book.getId(), book.getTitle(), book.getAuthor(), book.getGenre(), book.getIsbn(), status,
                status == BookStatus.AVAILABLE || reservedForMe, active.map(Loan::getDueDate).orElse(null),
                waiting.size(), position, myLoan, reservedForMe, reserved ? head.getReservedUntil() : null);
    }

    private List<LoanDto> toDtos(List<Loan> list) {
        LocalDate today = LocalDate.now(clock);
        Map<UUID, Book> bookMap = books.findAllById(list.stream().map(Loan::getBookId).distinct().toList()).stream()
                .collect(Collectors.toMap(Book::getId, b -> b));
        Map<UUID, String> names = userLookup.namesOf(list, Loan::getUserId);
        Map<UUID, Boolean> hasQueue = list.stream().map(Loan::getBookId).distinct()
                .collect(Collectors.toMap(id -> id, id -> !queue.findByBookIdOrderByJoinedAtAsc(id).isEmpty()));
        return list.stream().map(l -> {
            Book b = bookMap.get(l.getBookId());
            return new LoanDto(l.getId(), l.getBookId(), b.getTitle(), b.getAuthor(), l.getUserId(),
                    names.get(l.getUserId()), l.getBorrowedAt(), l.getDueDate(), l.getReturnedAt(), l.isExtended(),
                    l.isOverdue(today), canExtend(l, today, hasQueue.get(l.getBookId())));
        }).toList();
    }

    private LoanDto toDto(Loan l, Book b, LocalDate today) {
        boolean waiting = !queue.findByBookIdOrderByJoinedAtAsc(b.getId()).isEmpty();
        return new LoanDto(l.getId(), b.getId(), b.getTitle(), b.getAuthor(), l.getUserId(),
                userLookup.name(l.getUserId()), l.getBorrowedAt(), l.getDueDate(), l.getReturnedAt(), l.isExtended(),
                l.isOverdue(today), canExtend(l, today, waiting));
    }

    private static boolean canExtend(Loan l, LocalDate today, boolean queueNotEmpty) {
        return l.isActive() && !l.isExtended() && !l.isOverdue(today) && !queueNotEmpty;
    }

    private void apply(Book book, BookRequest req) {
        book.setTitle(req.title().trim());
        book.setAuthor(req.author().trim());
        book.setGenre(req.genre().trim());
        book.setIsbn(req.isbn() == null || req.isbn().isBlank() ? null : req.isbn().trim());
    }

    private Book findBook(UUID id) {
        return books.findById(id).orElseThrow(() -> ApiException.notFound("Livre"));
    }

    private Loan findLoan(UUID id) {
        return loans.findById(id).orElseThrow(() -> ApiException.notFound("Emprunt"));
    }
}
