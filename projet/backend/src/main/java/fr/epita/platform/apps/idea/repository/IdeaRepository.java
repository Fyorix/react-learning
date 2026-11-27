package fr.epita.platform.apps.idea.repository;

import fr.epita.platform.apps.idea.entity.Idea;
import fr.epita.platform.apps.idea.entity.IdeaCategory;
import fr.epita.platform.apps.idea.entity.IdeaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface IdeaRepository extends JpaRepository<Idea, UUID> {

    @Query("""
            select i from Idea i
            where (:status is null or i.status = :status)
              and (:category is null or i.category = :category)
              and (:search is null
                   or lower(i.title) like lower(concat('%', cast(:search as string), '%'))
                   or lower(i.description) like lower(concat('%', cast(:search as string), '%')))
            """)
    Page<Idea> search(@Param("status") IdeaStatus status, @Param("category") IdeaCategory category,
                      @Param("search") String search, Pageable pageable);
}
