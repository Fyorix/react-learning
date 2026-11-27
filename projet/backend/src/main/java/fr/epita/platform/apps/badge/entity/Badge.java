package fr.epita.platform.apps.badge.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "badge_badges")
@Getter
@Setter
@NoArgsConstructor
public class Badge extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String icon;
    @Column(nullable = false)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rarity rarity;
    @Column(nullable = false)
    private boolean secret;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;
    private String event;
    private Integer threshold;
    @Column(name = "tier_bronze")
    private Integer tierBronze;
    @Column(name = "tier_silver")
    private Integer tierSilver;
    @Column(name = "tier_gold")
    private Integer tierGold;
}
