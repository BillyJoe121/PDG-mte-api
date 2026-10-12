package co.edu.icesi.pdg.mte.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MonitoringEntryRepository extends JpaRepository<MonitoringEntry, Long> {
    List<MonitoringEntry> findByBranchId(Long branchId);
    List<MonitoringEntry> findByActorEmail(String actorEmail);
}
