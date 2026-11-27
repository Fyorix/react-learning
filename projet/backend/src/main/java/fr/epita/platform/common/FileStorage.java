package fr.epita.platform.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Stockage des fichiers envoyés (photos, justificatifs, CV) sur disque, dans le volume Docker {@code uploads}.
 * Les métadonnées (clé, type, nom) sont stockées en base par chaque application.
 */
@Component
public class FileStorage {

    public static final long MAX_SIZE = 5L * 1024 * 1024;
    public static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> DOCUMENTS = Set.of("image/jpeg", "image/png", "application/pdf");
    public static final Set<String> PDF = Set.of("application/pdf");

    private final Path root;

    public FileStorage(@Value("${app.uploads.dir:${java.io.tmpdir}/platform-uploads}") String dir) {
        this.root = Path.of(dir);
    }

    public record Stored(String key, String contentType, String originalName, long size) {
    }

    public Stored store(MultipartFile file, Set<String> allowedTypes) {
        if (file == null || file.isEmpty()) {
            throw invalid("Fichier manquant");
        }
        if (file.getSize() > MAX_SIZE) {
            throw invalid("Fichier trop volumineux (5 Mo maximum)");
        }
        String type = file.getContentType();
        if (type == null || !allowedTypes.contains(type)) {
            throw invalid("Type de fichier non accepté : " + allowedTypes);
        }
        String key = UUID.randomUUID().toString();
        try {
            Files.createDirectories(root);
            file.transferTo(root.resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Stored(key, type, file.getOriginalFilename(), file.getSize());
    }

    public byte[] load(String key) {
        try {
            return Files.readAllBytes(root.resolve(UUID.fromString(key).toString()));
        } catch (IOException e) {
            throw ApiException.notFound("Fichier");
        }
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(root.resolve(UUID.fromString(key).toString()));
        } catch (IOException ignored) {
            // un fichier orphelin n'est pas bloquant
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", message);
    }
}
