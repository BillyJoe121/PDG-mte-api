package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectProgressEntryRepository extends JpaRepository<ProjectProgressEntry, Long> {
    List<ProjectProgressEntry> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectProgressEntry> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);
}
