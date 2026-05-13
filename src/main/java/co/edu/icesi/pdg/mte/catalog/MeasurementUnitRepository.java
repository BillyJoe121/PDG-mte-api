package co.edu.icesi.pdg.mte.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, Long> {
    boolean existsByNameIgnoreCase(String name);

    Optional<MeasurementUnit> findByNameIgnoreCase(String name);
}
