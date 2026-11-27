package fr.epita.platform.common.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Affectation d'un manager à un salarié")
public record AssignManagerRequest(
        @Schema(description = "Manager à affecter (différent du salarié, sans créer de cycle)",
                example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
        @NotNull UUID managerId) {
}
