package co.edu.icesi.pdg.mte.strategy;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ObjectiveRepository extends JpaRepository<Objective, Long>, JpaSpecificationExecutor<Objective> {
    @Override
    @EntityGraph(attributePaths = {
            "createdBy",
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults",
            "keyResults.measurementUnit",
            "keyResults.academicPeriod"
    })
    List<Objective> findAll();

    @Override
    @EntityGraph(attributePaths = {
            "createdBy",
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults",
            "keyResults.measurementUnit",
            "keyResults.academicPeriod"
    })
    List<Objective> findAll(Specification<Objective> specification);

    @EntityGraph(attributePaths = {
            "createdBy",
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults",
            "keyResults.measurementUnit",
            "keyResults.academicPeriod"
    })
    List<Objective> findByStrategicBetIdIn(Collection<Long> strategicBetIds);

    @EntityGraph(attributePaths = {
            "createdBy",
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults",
            "keyResults.measurementUnit",
            "keyResults.academicPeriod"
    })
    List<Objective> findByGoalIdIn(Collection<Long> goalIds);

    @EntityGraph(attributePaths = {
            "createdBy",
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults",
            "keyResults.measurementUnit",
            "keyResults.academicPeriod"
    })
    @Query("""
            select o
            from Objective o
            join o.academicPeriod period
            where (
                cast(substring(period.name, 1, 4) as integer) * 4
                + case
                    when substring(period.name, 6, 1) = 'Q' then cast(substring(period.name, 7, 1) as integer)
                    when substring(period.name, 6, 1) = '1' then 1
                    else 3
                  end
            ) <= :requestedEnd
              and (
                cast(substring(period.name, 1, 4) as integer) * 4
                + case
                    when substring(period.name, 6, 1) = 'Q' then cast(substring(period.name, 7, 1) as integer)
                    when substring(period.name, 6, 1) = '1' then 2
                    else 4
                  end
            ) >= :requestedStart
            """)
    List<Objective> findAllByOverlappingPeriod(
            @Param("requestedStart") int requestedStart,
            @Param("requestedEnd") int requestedEnd
    );

    @EntityGraph(attributePaths = {
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults"
    })
    @Query("""
            select o
            from Objective o
            """)
    List<Objective> findAllForDashboard();

    @EntityGraph(attributePaths = {
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults"
    })
    @Query("""
            select o
            from Objective o
            join o.academicPeriod period
            where (
                cast(substring(period.name, 1, 4) as integer) * 4
                + case
                    when substring(period.name, 6, 1) = 'Q' then cast(substring(period.name, 7, 1) as integer)
                    when substring(period.name, 6, 1) = '1' then 1
                    else 3
                  end
            ) <= :requestedEnd
              and (
                cast(substring(period.name, 1, 4) as integer) * 4
                + case
                    when substring(period.name, 6, 1) = 'Q' then cast(substring(period.name, 7, 1) as integer)
                    when substring(period.name, 6, 1) = '1' then 2
                    else 4
                  end
            ) >= :requestedStart
            """)
    List<Objective> findDashboardByOverlappingPeriod(
            @Param("requestedStart") int requestedStart,
            @Param("requestedEnd") int requestedEnd
    );

    @EntityGraph(attributePaths = {
            "createdBy",
            "department",
            "academicPeriod",
            "goal",
            "strategicBet",
            "keyResults",
            "keyResults.measurementUnit",
            "keyResults.academicPeriod"
    })
    @Query("""
            select o
            from Objective o
            join o.academicPeriod period
            where (:filterPeriod = false or (
                (
                    cast(substring(period.name, 1, 4) as integer) * 4
                    + case
                        when substring(period.name, 6, 1) = 'Q' then cast(substring(period.name, 7, 1) as integer)
                        when substring(period.name, 6, 1) = '1' then 1
                        else 3
                      end
                ) <= :requestedEnd
                and (
                    cast(substring(period.name, 1, 4) as integer) * 4
                    + case
                        when substring(period.name, 6, 1) = 'Q' then cast(substring(period.name, 7, 1) as integer)
                        when substring(period.name, 6, 1) = '1' then 2
                        else 4
                      end
                ) >= :requestedStart
            ))
              and (:departmentId is null or o.department.id = :departmentId)
              and (:objectiveId is null or o.id = :objectiveId)
            """)
    List<Objective> findReportCandidates(
            @Param("filterPeriod") boolean filterPeriod,
            @Param("requestedStart") int requestedStart,
            @Param("requestedEnd") int requestedEnd,
            @Param("departmentId") Long departmentId,
            @Param("objectiveId") Long objectiveId
    );

    boolean existsByAcademicPeriodId(Long academicPeriodId);

    boolean existsByDepartmentId(Long departmentId);
}
