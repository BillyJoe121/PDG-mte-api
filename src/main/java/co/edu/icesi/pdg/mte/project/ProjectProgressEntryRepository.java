package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectProgressEntryRepository extends JpaRepository<ProjectProgressEntry, Long> {
    List<ProjectProgressEntry> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
