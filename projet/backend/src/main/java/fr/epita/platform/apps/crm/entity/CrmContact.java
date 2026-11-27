package fr.epita.platform.apps.crm.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "crm_contacts")
@Getter
@Setter
@NoArgsConstructor
public class CrmContact extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Column(name = "first_name", nullable = false)
    private String firstName;
    @Column(name = "last_name", nullable = false)
    private String lastName;
    @Column(nullable = false)
    private String email;
    private String phone;
    @Column(nullable = false)
    private String role;
}
