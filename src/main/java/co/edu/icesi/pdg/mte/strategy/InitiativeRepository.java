package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InitiativeRepository extends JpaRepository<Initiative, Long> {
    List<Initiative> findByKeyResultIdAndActiveTrue(Long keyResultId);
    List<Initiative> findByDepartmentIdAndActiveTrue(Long departmentId);
}
