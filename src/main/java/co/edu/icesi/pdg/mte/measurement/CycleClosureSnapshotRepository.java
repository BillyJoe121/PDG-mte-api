package co.edu.icesi.pdg.mte.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CycleClosureSnapshotRepository extends JpaRepository<CycleClosureSnapshot, Long> {
    List<CycleClosureSnapshot> findByCycleClosureId(Long cycleClosureId);
}
