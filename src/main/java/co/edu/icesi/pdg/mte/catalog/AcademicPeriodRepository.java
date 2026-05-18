package co.edu.icesi.pdg.mte.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, Long> {
    boolean existsByNameIgnoreCase(String name);

    Optional<AcademicPeriod> findByNameIgnoreCase(String name);

    Optional<AcademicPeriod> findFirstByStatusOrderByStartDateDesc(PeriodStatus status);
}
