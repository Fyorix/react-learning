package fr.epita.platform.common.relation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Relation manager → salarié")
public record ReportDto(UUID employeeId, String employeeName, UUID managerId, String managerName) {
}
