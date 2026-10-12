package co.edu.icesi.pdg.mte.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SituationHistoryRepository extends JpaRepository<SituationHistory, Long> {
    List<SituationHistory> findBySituationIdOrderByChangedAtDesc(Long situationId);
}
