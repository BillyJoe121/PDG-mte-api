package co.edu.icesi.pdg.mte.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {
    List<CorrectiveAction> findByDepartmentId(Long departmentId);
    List<CorrectiveAction> findBySituationId(Long situationId);
}
