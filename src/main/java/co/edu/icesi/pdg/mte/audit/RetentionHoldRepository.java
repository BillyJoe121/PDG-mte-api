package co.edu.icesi.pdg.mte.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RetentionHoldRepository extends JpaRepository<RetentionHold, Long> {
    List<RetentionHold> findByEntityTypeAndEntityIdAndHoldStatus(String entityType, String entityId, String holdStatus);
}
