package fr.epita.platform.apps.referral.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "referral_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ReferralJob extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String team;
    @Column(nullable = false)
    private String description;
    @Column(name = "bonus_amount", nullable = false)
    private BigDecimal bonusAmount;
    @Column(nullable = false)
    private boolean open;
}
