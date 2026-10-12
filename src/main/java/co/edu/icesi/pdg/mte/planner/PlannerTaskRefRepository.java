package co.edu.icesi.pdg.mte.planner;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlannerTaskRefRepository extends JpaRepository<PlannerTaskRef, Long> {
    List<PlannerTaskRef> findByInitiativeId(Long initiativeId);
    Optional<PlannerTaskRef> findByConnectionIdAndExternalTaskIdAndInitiativeId(Long connectionId, String externalTaskId, Long initiativeId);
}
