package fr.epita.platform.apps.grouporder.repository;

import fr.epita.platform.apps.grouporder.entity.ItemOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ItemOptionRepository extends JpaRepository<ItemOption, UUID> {

    List<ItemOption> findByItemIdInOrderByLabelAsc(Collection<UUID> itemIds);

    List<ItemOption> findByItemId(UUID itemId);
}
