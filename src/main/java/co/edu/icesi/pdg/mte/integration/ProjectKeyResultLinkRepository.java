package co.edu.icesi.pdg.mte.integration;

import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProjectKeyResultLinkRepository extends JpaRepository<ProjectKeyResultLink, Long> {
    long countByKeyResultIdAndActiveTrue(Long keyResultId);

    List<ProjectKeyResultLink> findByKeyResultIdAndActiveTrueOrderByIdAsc(Long keyResultId);

    List<ProjectKeyResultLink> findByKeyResultIdInAndActiveTrueOrderByIdAsc(Collection<Long> keyResultIds);

    List<ProjectKeyResultLink> findByProjectIdAndActiveTrueOrderByIdAsc(Long projectId);

    List<ProjectKeyResultLink> findByProjectIdInAndActiveTrueOrderByIdAsc(Collection<Long> projectIds);

    List<ProjectKeyResultLink> findByActiveTrueAndProjectStatus(ProjectStatus status);

    boolean existsByProjectIdAndActiveTrue(Long projectId);

    boolean existsByProjectIdAndKeyResultIdAndActiveTrue(Long projectId, Long keyResultId);
}
