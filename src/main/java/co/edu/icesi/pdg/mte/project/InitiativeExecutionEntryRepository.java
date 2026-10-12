package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InitiativeExecutionEntryRepository extends JpaRepository<InitiativeExecutionEntry, Long> {
    List<InitiativeExecutionEntry> findByInitiativeId(Long initiativeId);
    List<InitiativeExecutionEntry> findByInitiativeIdOrderByExecutionDateDesc(Long initiativeId);
}
