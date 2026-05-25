package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectProgressEntryRepository extends JpaRepository<ProjectProgressEntry, Long> {
    @EntityGraph(attributePaths = "project")
    List<ProjectProgressEntry> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectProgressEntry> findFirstByProjectIdOrderByCreatedAtDesc(Long projectId);

    @EntityGraph(attributePaths = "project")
    @Query("""
            select entry
            from ProjectProgressEntry entry
            where entry.project.id in :projectIds
            order by entry.createdAt asc
            """)
    List<ProjectProgressEntry> findByProjectIdsOrderByCreatedAtAsc(@Param("projectIds") Collection<Long> projectIds);

    @EntityGraph(attributePaths = "project")
    @Query("""
            select entry
            from ProjectProgressEntry entry
            where entry.project.id in :projectIds
            order by entry.project.id asc, entry.createdAt desc
            """)
    List<ProjectProgressEntry> findByProjectIdsOrderByProjectIdAscCreatedAtDesc(@Param("projectIds") Collection<Long> projectIds);
}
