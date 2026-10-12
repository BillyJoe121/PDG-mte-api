package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InitiativeProgressEntryRepository extends JpaRepository<InitiativeProgressEntry, Long> {
    List<InitiativeProgressEntry> findByInitiativeIdOrderByOccurredAtDesc(Long initiativeId);
}
