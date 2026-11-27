package fr.epita.platform.apps.library.repository;

import fr.epita.platform.apps.library.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    Optional<Loan> findByBookIdAndReturnedAtIsNull(UUID bookId);

    List<Loan> findByReturnedAtIsNull();

    List<Loan> findByUserIdAndReturnedAtIsNull(UUID userId);

    List<Loan> findByUserIdOrderByBorrowedAtDesc(UUID userId);

    List<Loan> findAllByOrderByBorrowedAtDesc();
}
