package co.edu.icesi.pdg.mte.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectKeyResultLinkRepository extends JpaRepository<ProjectKeyResultLink, Long> {
    long countByKeyResultIdAndActiveTrue(Long keyResultId);

    List<ProjectKeyResultLink> findByKeyResultIdAndActiveTrueOrderByIdAsc(Long keyResultId);

    List<ProjectKeyResultLink> findByProjectIdAndActiveTrueOrderByIdAsc(Long projectId);

    boolean existsByProjectIdAndKeyResultIdAndActiveTrue(Long projectId, Long keyResultId);
}
