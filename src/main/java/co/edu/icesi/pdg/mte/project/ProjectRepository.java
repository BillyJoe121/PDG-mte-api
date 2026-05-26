package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    Optional<Project> findByExternalSourceAndExternalProjectId(String externalSource, Long externalProjectId);

    List<Project> findByStatus(ProjectStatus status);

    boolean existsByKeyResultIdAndStatus(Long keyResultId, ProjectStatus status);

    boolean existsByStartPeriodOrEndPeriod(String startPeriod, String endPeriod);

    boolean existsByDepartmentId(Long departmentId);
}
