package co.edu.icesi.pdg.mte.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PresentationSessionRepository extends JpaRepository<PresentationSession, Long> {
    List<PresentationSession> findByPresenterId(String presenterId);
    List<PresentationSession> findByCycleId(Long cycleId);
}
