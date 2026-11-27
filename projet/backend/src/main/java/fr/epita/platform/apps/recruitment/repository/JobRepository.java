package fr.epita.platform.apps.recruitment.repository;

import fr.epita.platform.apps.recruitment.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findAllByOrderByCreatedAtDesc();
}
