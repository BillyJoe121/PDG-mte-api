package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KrGoalLinkRepository extends JpaRepository<KrGoalLink, Long> {
    List<KrGoalLink> findByKeyResultIdAndEndedAtIsNull(Long keyResultId);
    List<KrGoalLink> findByGoalIdAndEndedAtIsNull(Long goalId);
}
