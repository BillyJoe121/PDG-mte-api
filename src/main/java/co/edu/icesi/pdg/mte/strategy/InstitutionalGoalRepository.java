package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstitutionalGoalRepository extends JpaRepository<InstitutionalGoal, Long> {
    @Override
    @EntityGraph(attributePaths = {"measurementUnit", "world", "periods"})
    List<InstitutionalGoal> findAll();

    boolean existsByMeasurementUnitId(Long measurementUnitId);

    boolean existsByPeriodsId(Long periodId);

    boolean existsByWorldId(Long worldId);
}
