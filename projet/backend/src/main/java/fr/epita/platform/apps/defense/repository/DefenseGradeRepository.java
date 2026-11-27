package fr.epita.platform.apps.defense.repository;

import fr.epita.platform.apps.defense.entity.DefenseGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DefenseGradeRepository extends JpaRepository<DefenseGrade, DefenseGrade.Key> {

    List<DefenseGrade> findByIdSlotId(UUID slotId);
}
