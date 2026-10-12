package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KrAssignmentRepository extends JpaRepository<KrAssignment, Long> {
    Optional<KrAssignment> findByKeyResultIdAndEndedAtIsNull(Long keyResultId);
    List<KrAssignment> findByProfessorIdAndEndedAtIsNull(Long professorId);
}
