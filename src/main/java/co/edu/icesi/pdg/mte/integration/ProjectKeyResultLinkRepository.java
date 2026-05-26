package co.edu.icesi.pdg.mte.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ProjectKeyResultLinkRepository extends JpaRepository<ProjectKeyResultLink, Long> {
    interface KeyResultWeightTotal {
        Long getKeyResultId();

        BigDecimal getTotalWeight();
    }

    long countByKeyResultIdAndActiveTrue(Long keyResultId);

    @EntityGraph(attributePaths = {
            "keyResult",
            "keyResult.academicPeriod",
            "keyResult.objective",
            "keyResult.objective.academicPeriod",
            "keyResult.objective.goal",
            "keyResult.objective.strategicBet",
            "project"
    })
    List<ProjectKeyResultLink> findByKeyResultIdAndActiveTrueOrderByIdAsc(Long keyResultId);

    @EntityGraph(attributePaths = {
            "keyResult",
            "keyResult.academicPeriod",
            "keyResult.objective",
            "keyResult.objective.academicPeriod",
            "keyResult.objective.goal",
            "keyResult.objective.strategicBet",
            "project"
    })
    List<ProjectKeyResultLink> findByKeyResultIdInAndActiveTrueOrderByIdAsc(Collection<Long> keyResultIds);

    @Query("""
            select link
            from ProjectKeyResultLink link
            join fetch link.keyResult keyResult
            left join fetch link.project project
            where link.active = true
              and keyResult.id in :keyResultIds
            order by link.id asc
            """)
    List<ProjectKeyResultLink> findDashboardActiveByKeyResultIds(@Param("keyResultIds") Collection<Long> keyResultIds);

    @EntityGraph(attributePaths = {
            "keyResult",
            "keyResult.academicPeriod",
            "keyResult.objective",
            "keyResult.objective.academicPeriod",
            "keyResult.objective.goal",
            "keyResult.objective.strategicBet",
            "project"
    })
    List<ProjectKeyResultLink> findByActiveTrueOrderByIdAsc();

    @EntityGraph(attributePaths = {
            "keyResult",
            "keyResult.academicPeriod",
            "keyResult.objective",
            "keyResult.objective.academicPeriod",
            "keyResult.objective.goal",
            "keyResult.objective.strategicBet",
            "project"
    })
    List<ProjectKeyResultLink> findByProjectIdAndActiveTrueOrderByIdAsc(Long projectId);

    @Query("""
            select link
            from ProjectKeyResultLink link
            join fetch link.keyResult keyResult
            where link.active = true
              and link.project.id in :projectIds
            order by link.project.id asc, link.id asc
            """)
    List<ProjectKeyResultLink> findActiveByProjectIds(@Param("projectIds") Collection<Long> projectIds);

    @Query("""
            select distinct link.project.id
            from ProjectKeyResultLink link
            where link.active = true
              and link.project.id in :projectIds
            """)
    List<Long> findActiveProjectIdsByProjectIds(@Param("projectIds") Collection<Long> projectIds);

    @Query("""
            select distinct link.project.id
            from ProjectKeyResultLink link
            where link.active = true
              and link.project.id is not null
              and link.keyResult.objective.id = :objectiveId
            """)
    List<Long> findActiveProjectIdsByObjectiveId(@Param("objectiveId") Long objectiveId);

    @Query("""
            select distinct link.keyResult.id
            from ProjectKeyResultLink link
            where link.active = true
              and link.keyResult.id is not null
              and link.project.status = :status
            """)
    List<Long> findActiveKeyResultIdsByProjectStatus(@Param("status") co.edu.icesi.pdg.mte.project.ProjectStatus status);

    @Query("""
            select link.keyResult.id as keyResultId,
                   coalesce(sum(link.contributionWeight), 0) as totalWeight
            from ProjectKeyResultLink link
            where link.active = true
              and link.keyResult.id in :keyResultIds
            group by link.keyResult.id
            """)
    List<KeyResultWeightTotal> sumActiveContributionWeightsByKeyResultIds(@Param("keyResultIds") Collection<Long> keyResultIds);

    boolean existsByProjectIdAndActiveTrue(Long projectId);

    boolean existsByProjectIdAndKeyResultIdAndActiveTrue(Long projectId, Long keyResultId);
}
