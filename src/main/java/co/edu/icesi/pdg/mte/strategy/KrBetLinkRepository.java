package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KrBetLinkRepository extends JpaRepository<KrBetLink, Long> {
    List<KrBetLink> findByKeyResultIdAndEndedAtIsNull(Long keyResultId);
    List<KrBetLink> findByStrategicBetIdAndEndedAtIsNull(Long strategicBetId);
}
