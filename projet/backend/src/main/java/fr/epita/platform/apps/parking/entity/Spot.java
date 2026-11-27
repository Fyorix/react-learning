package fr.epita.platform.apps.parking.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "parking_spots")
@Getter
@Setter
@NoArgsConstructor
public class Spot extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String number;
    @Column(nullable = false)
    private String level;
    @Column(name = "holder_id", unique = true)
    private UUID holderId;
}
