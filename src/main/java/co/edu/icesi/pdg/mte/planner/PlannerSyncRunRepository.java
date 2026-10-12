package co.edu.icesi.pdg.mte.planner;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlannerSyncRunRepository extends JpaRepository<PlannerSyncRun, Long> {
    List<PlannerSyncRun> findByConnectionIdOrderByStartedAtDesc(Long connectionId);
}
