package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectProgressEntryRepository extends JpaRepository<ProjectProgressEntry, Long> {
    List<ProjectProgressEntry> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectProgressEntry> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    /** Each row is {projectId, latest createdAt} for the given projects that have progress entries. */
    @Query("select e.project.id, max(e.createdAt) from ProjectProgressEntry e where e.project.id in :projectIds group by e.project.id")
    List<Object[]> findLatestCreatedAtByProjectIds(@Param("projectIds") Collection<Long> projectIds);
}
