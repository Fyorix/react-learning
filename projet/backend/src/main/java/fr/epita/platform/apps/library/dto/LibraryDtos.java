package fr.epita.platform.apps.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class LibraryDtos {

    private LibraryDtos() {
    }

    public enum BookStatus { AVAILABLE, LOANED, RESERVED }

    @Schema(description = "Ajout ou modification d'un livre (ADMIN)")
    public record BookRequest(
            @Schema(description = "Titre, 200 caractères maximum", example = "Clean Code")
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "Auteur, 150 caractères maximum", example = "Robert C. Martin")
            @NotBlank @Size(max = 150) String author,
            @Schema(description = "Genre, 50 caractères maximum", example = "Informatique")
            @NotBlank @Size(max = 50) String genre,
            @Schema(description = "ISBN facultatif, 20 caractères maximum", example = "9780132350884")
            @Size(max = 20) String isbn) {
    }

    public record BookDto(UUID id, String title, String author, String genre, String isbn, BookStatus status,
                          boolean available, LocalDate dueDate, int queueSize, Integer myQueuePosition,
                          UUID myActiveLoanId, boolean reservedForMe, Instant reservedUntil) {
    }

    public record LoanDto(UUID id, UUID bookId, String bookTitle, String bookAuthor, UUID userId, String userName,
                          Instant borrowedAt, LocalDate dueDate, Instant returnedAt, boolean extended,
                          boolean overdue, boolean canExtend) {
    }
}
