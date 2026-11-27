package fr.epita.platform.apps.license.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "license_requests")
@Getter
@Setter
@NoArgsConstructor
public class LicenseRequest extends BaseEntity {

    @Column(name = "software_id", nullable = false)
    private UUID softwareId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LicenseRequestStatus status;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "reject_reason")
    private String rejectReason;
}
