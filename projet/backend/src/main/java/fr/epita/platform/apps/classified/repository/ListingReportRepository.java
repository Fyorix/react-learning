package fr.epita.platform.apps.classified.repository;

import fr.epita.platform.apps.classified.entity.ListingReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingReportRepository extends JpaRepository<ListingReport, ListingReport.Key> {

    List<ListingReport> findByIdListingId(UUID listingId);

    long countByIdListingId(UUID listingId);
}
