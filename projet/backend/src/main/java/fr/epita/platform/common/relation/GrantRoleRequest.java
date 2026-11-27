package fr.epita.platform.common.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Attribution d'un rôle propre à l'application")
public record GrantRoleRequest(
        @Schema(description = "Rôle à attribuer (valeurs possibles indiquées dans la description de l'opération)", example = "SALES")
        @NotBlank String role) {
}
