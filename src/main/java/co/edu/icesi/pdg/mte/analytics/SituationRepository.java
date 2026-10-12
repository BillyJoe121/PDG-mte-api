package co.edu.icesi.pdg.mte.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SituationRepository extends JpaRepository<Situation, Long> {
    List<Situation> findByDepartmentId(Long departmentId);
    List<Situation> findByCycleId(Long cycleId);
}
