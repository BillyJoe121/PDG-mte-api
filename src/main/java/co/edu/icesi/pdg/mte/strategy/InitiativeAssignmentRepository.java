package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface InitiativeAssignmentRepository extends JpaRepository<InitiativeAssignment, Long> {
    Optional<InitiativeAssignment> findByInitiativeIdAndEndedAtIsNull(Long initiativeId);
    List<InitiativeAssignment> findByProfessorIdAndEndedAtIsNull(Long professorId);
}
