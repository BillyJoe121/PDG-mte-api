package co.edu.icesi.pdg.mte.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportExportRepository extends JpaRepository<ReportExport, Long> {
    List<ReportExport> findByExportedBy(String exportedBy);
    List<ReportExport> findByCutId(Long cutId);
}
