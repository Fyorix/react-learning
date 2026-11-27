package fr.epita.platform.apps.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class DirectoryDtos {

    private DirectoryDtos() {
    }

    @Schema(description = "Compétence déclarée avec son niveau")
    public record SkillLevel(
            @Schema(description = "Compétence du référentiel", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID skillId,
            @Schema(description = "Niveau : 1 = notions, 2 = autonome, 3 = confirmé, 4 = expert", example = "3")
            @NotNull @Min(1) @Max(4) Integer level) {
    }

    @Schema(description = "Modification de ma fiche (l'équipe et le manager sont fixés par un ADMIN)")
    public record ProfileRequest(
            @Schema(description = "Poste, 100 caractères maximum", example = "Développeuse front")
            @NotBlank @Size(max = 100) String jobTitle,
            @Schema(description = "Site ou ville, 80 caractères maximum", example = "Paris")
            @NotBlank @Size(max = 80) String location,
            @Schema(description = "Téléphone facultatif (chiffres, espaces, +, tirets ; 6 à 30 caractères)", example = "+33 6 12 34 56 78")
            @Pattern(regexp = "[0-9 +().-]{6,30}") String phone,
            @Schema(description = "Présentation facultative, 1000 caractères maximum", example = "Passionnée d'accessibilité.")
            @Size(max = 1000) String bio,
            @Schema(description = "Compétences : 30 maximum, une fois chacune, du référentiel uniquement")
            @NotNull @Size(max = 30) List<@Valid SkillLevel> skills) {
    }

    @Schema(description = "Affectation d'un collaborateur (ADMIN)")
    public record AssignmentRequest(
            @Schema(description = "Équipe, ou null pour aucune", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            UUID teamId,
            @Schema(description = "Manager, ou null pour aucun ; jamais le collaborateur lui-même", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID managerId) {
    }

    @Schema(description = "Création d'une équipe (ADMIN)")
    public record TeamRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Plateforme")
            @NotBlank @Size(max = 80) String name) {
    }

    @Schema(description = "Ajout d'une compétence au référentiel (ADMIN)")
    public record SkillRequest(
            @Schema(description = "Nom unique, 60 caractères maximum", example = "React")
            @NotBlank @Size(max = 60) String name,
            @Schema(description = "Catégorie facultative, 60 caractères maximum", example = "Front")
            @Size(max = 60) String category) {
    }

    @Schema(description = "Compétence d'une fiche")
    public record ProfileSkillDto(UUID skillId, String name, String category, int level) {
    }

    @Schema(description = "Carte du trombinoscope")
    public record PersonCardDto(UUID userId, String displayName, String jobTitle, UUID teamId, String teamName,
                                String location, boolean hasPhoto, List<ProfileSkillDto> topSkills) {
    }

    @Schema(description = "Personne liée à une fiche (manager, collègue)")
    public record PersonRefDto(UUID userId, String displayName, String jobTitle) {
    }

    @Schema(description = "Fiche détaillée")
    public record PersonDto(UUID userId, String displayName, String email, String jobTitle, UUID teamId, String teamName,
                            PersonRefDto manager, String location, String phone, String bio, boolean hasPhoto,
                            List<ProfileSkillDto> skills, List<PersonRefDto> teammates, List<PersonRefDto> reports) {
    }

    @Schema(description = "Équipe")
    public record TeamDto(UUID id, String name, long members) {
    }

    @Schema(description = "Compétence du référentiel")
    public record SkillDto(UUID id, String name, String category) {
    }
}
