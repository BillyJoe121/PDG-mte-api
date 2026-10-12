package co.edu.icesi.pdg.mte.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EvidenceVersionRepository extends JpaRepository<EvidenceVersion, Long> {
    List<EvidenceVersion> findByEvidenceIdOrderByVersionNumberAsc(Long evidenceId);
}
