package co.edu.icesi.pdg.mte.planner;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PlannerConnectionRepository extends JpaRepository<PlannerConnection, Long> {
    Optional<PlannerConnection> findByProfessorIdAndRevokedAtIsNullAndConsentStatus(Long professorId, String consentStatus);
    List<PlannerConnection> findByProfessorId(Long professorId);
}
