package fr.epita.platform.apps.timesheet.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "timesheet_projects")
@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    public static final String ABSENCE = "ABSENCE";

    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    private String client;
    /** OPEN ou CLOSED */
    @Column(nullable = false)
    private String status = "OPEN";

    public boolean isOpen() {
        return "OPEN".equals(status);
    }
}
