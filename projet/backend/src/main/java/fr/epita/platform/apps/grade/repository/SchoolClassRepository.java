package fr.epita.platform.apps.grade.repository;

import fr.epita.platform.apps.grade.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    List<SchoolClass> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    @Query("select c from SchoolClass c join c.studentIds s where s = :u")
    Optional<SchoolClass> ofStudent(@Param("u") UUID studentId);
}
