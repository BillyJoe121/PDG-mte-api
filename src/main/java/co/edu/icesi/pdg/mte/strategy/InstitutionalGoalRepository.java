package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionalGoalRepository extends JpaRepository<InstitutionalGoal, Long> {
    boolean existsByMeasurementUnitId(Long measurementUnitId);

    boolean existsByPeriodsId(Long periodId);
}
