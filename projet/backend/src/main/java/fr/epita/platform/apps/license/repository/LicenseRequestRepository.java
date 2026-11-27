package fr.epita.platform.apps.license.repository;

import fr.epita.platform.apps.license.entity.LicenseRequest;
import fr.epita.platform.apps.license.entity.LicenseRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LicenseRequestRepository extends JpaRepository<LicenseRequest, UUID> {

    List<LicenseRequest> findAllByOrderByCreatedAtDesc();

    List<LicenseRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<LicenseRequest> findByUserIdAndStatus(UUID userId, LicenseRequestStatus status);

    boolean existsBySoftwareIdAndUserIdAndStatus(UUID softwareId, UUID userId, LicenseRequestStatus status);
}
