package co.edu.icesi.pdg.mte.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolFinalReportRepository extends JpaRepository<SchoolFinalReport, Long> {
    List<SchoolFinalReport> findByCycleId(Long cycleId);
    Optional<SchoolFinalReport> findByCycleIdAndDepartmentId(Long cycleId, Long departmentId);
}
