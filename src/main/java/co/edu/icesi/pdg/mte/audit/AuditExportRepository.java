package co.edu.icesi.pdg.mte.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditExportRepository extends JpaRepository<AuditExport, Long> {
    List<AuditExport> findByExportedBy(String exportedBy);
}
