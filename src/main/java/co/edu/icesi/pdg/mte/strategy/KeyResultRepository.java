package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeyResultRepository extends JpaRepository<KeyResult, Long> {
    List<KeyResult> findByObjectiveIdOrderByIdAsc(Long objectiveId);

    boolean existsByMeasurementUnitId(Long measurementUnitId);
}
