package co.edu.icesi.pdg.mte.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MonitoringBranchRepository extends JpaRepository<MonitoringBranch, Long> {
    List<MonitoringBranch> findByCutId(Long cutId);
    List<MonitoringBranch> findByCutIdAndBranchType(Long cutId, String branchType);
    List<MonitoringBranch> findByDepartmentId(Long departmentId);
}
