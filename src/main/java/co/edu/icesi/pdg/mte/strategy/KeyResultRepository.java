package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyResultRepository extends JpaRepository<KeyResult, Long> {
    boolean existsByMeasurementUnitId(Long measurementUnitId);

    boolean existsByAcademicPeriodId(Long academicPeriodId);
}
