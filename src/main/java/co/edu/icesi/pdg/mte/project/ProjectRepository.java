package co.edu.icesi.pdg.mte.project;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    @Override
    @EntityGraph(attributePaths = {"department", "keyResult", "tutors"})
    List<Project> findAll();

    @Override
    @EntityGraph(attributePaths = {"department", "keyResult", "tutors"})
    List<Project> findAll(Specification<Project> specification);

    @EntityGraph(attributePaths = {"department", "keyResult", "tutors"})
    @Query("""
            select p
            from Project p
            where (
                cast(substring(p.startPeriod, 1, 4) as integer) * 4
                + case
                    when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                    when substring(p.startPeriod, 6, 1) = '1' then 1
                    else 3
                  end
            ) <= :requestedEnd
              and case
                    when p.endPeriod is null or p.endPeriod = '' then (
                        cast(substring(p.startPeriod, 1, 4) as integer) * 4
                        + case
                            when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                            when substring(p.startPeriod, 6, 1) = '1' then 2
                            else 4
                          end
                    )
                    else (
                        cast(substring(p.endPeriod, 1, 4) as integer) * 4
                        + case
                            when substring(p.endPeriod, 6, 1) = 'Q' then cast(substring(p.endPeriod, 7, 1) as integer)
                            when substring(p.endPeriod, 6, 1) = '1' then 2
                            else 4
                          end
                    )
                  end >= :requestedStart
            """)
    List<Project> findAllByOverlappingPeriod(
            @Param("requestedStart") int requestedStart,
            @Param("requestedEnd") int requestedEnd
    );

    @EntityGraph(attributePaths = {"department", "keyResult", "tutors"})
    @Query("""
            select p
            from Project p
            where (:filterPeriod = false or (
                (
                    cast(substring(p.startPeriod, 1, 4) as integer) * 4
                    + case
                        when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                        when substring(p.startPeriod, 6, 1) = '1' then 1
                        else 3
                      end
                ) <= :requestedEnd
                and case
                      when p.endPeriod is null or p.endPeriod = '' then (
                          cast(substring(p.startPeriod, 1, 4) as integer) * 4
                          + case
                              when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                              when substring(p.startPeriod, 6, 1) = '1' then 2
                              else 4
                            end
                      )
                      else (
                          cast(substring(p.endPeriod, 1, 4) as integer) * 4
                          + case
                              when substring(p.endPeriod, 6, 1) = 'Q' then cast(substring(p.endPeriod, 7, 1) as integer)
                              when substring(p.endPeriod, 6, 1) = '1' then 2
                              else 4
                            end
                      )
                    end >= :requestedStart
            ))
              and (:departmentId is null or p.department.id = :departmentId)
              and (:filterProjectIds = false or p.id in :projectIds)
            """)
    List<Project> findReportCandidates(
            @Param("filterPeriod") boolean filterPeriod,
            @Param("requestedStart") int requestedStart,
            @Param("requestedEnd") int requestedEnd,
            @Param("departmentId") Long departmentId,
            @Param("filterProjectIds") boolean filterProjectIds,
            @Param("projectIds") Collection<Long> projectIds
    );

    @Override
    @EntityGraph(attributePaths = {
            "department",
            "keyResult",
            "keyResult.academicPeriod",
            "keyResult.objective",
            "keyResult.objective.academicPeriod",
            "keyResult.objective.goal",
            "keyResult.objective.strategicBet",
            "tutors"
    })
    Optional<Project> findById(Long id);

    Optional<Project> findByExternalSourceAndExternalProjectId(String externalSource, Long externalProjectId);

    @EntityGraph(attributePaths = {"department", "keyResult", "tutors"})
    List<Project> findByStatus(ProjectStatus status);

    @Query(
            value = """
                    select p
                    from Project p
                    left join fetch p.department
                    left join fetch p.keyResult
                    where (:search is null
                        or lower(p.name) like :search
                        or lower(p.description) like :search)
                      and (:status is null or p.status = :status)
                      and (:type is null or p.type = :type)
                      and (:departmentId is null or p.department.id = :departmentId)
                      and (:filterPeriod = false or (
                          (
                              cast(substring(p.startPeriod, 1, 4) as integer) * 4
                              + case
                                  when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                                  when substring(p.startPeriod, 6, 1) = '1' then 1
                                  else 3
                                end
                          ) <= :requestedEnd
                          and case
                                when p.endPeriod is null or p.endPeriod = '' then (
                                    cast(substring(p.startPeriod, 1, 4) as integer) * 4
                                    + case
                                        when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                                        when substring(p.startPeriod, 6, 1) = '1' then 2
                                        else 4
                                      end
                                )
                                else (
                                    cast(substring(p.endPeriod, 1, 4) as integer) * 4
                                    + case
                                        when substring(p.endPeriod, 6, 1) = 'Q' then cast(substring(p.endPeriod, 7, 1) as integer)
                                        when substring(p.endPeriod, 6, 1) = '1' then 2
                                        else 4
                                      end
                                )
                              end >= :requestedStart
                      ))
                    """,
            countQuery = """
                    select count(p)
                    from Project p
                    where (:search is null
                        or lower(p.name) like :search
                        or lower(p.description) like :search)
                      and (:status is null or p.status = :status)
                      and (:type is null or p.type = :type)
                      and (:departmentId is null or p.department.id = :departmentId)
                      and (:filterPeriod = false or (
                          (
                              cast(substring(p.startPeriod, 1, 4) as integer) * 4
                              + case
                                  when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                                  when substring(p.startPeriod, 6, 1) = '1' then 1
                                  else 3
                                end
                          ) <= :requestedEnd
                          and case
                                when p.endPeriod is null or p.endPeriod = '' then (
                                    cast(substring(p.startPeriod, 1, 4) as integer) * 4
                                    + case
                                        when substring(p.startPeriod, 6, 1) = 'Q' then cast(substring(p.startPeriod, 7, 1) as integer)
                                        when substring(p.startPeriod, 6, 1) = '1' then 2
                                        else 4
                                      end
                                )
                                else (
                                    cast(substring(p.endPeriod, 1, 4) as integer) * 4
                                    + case
                                        when substring(p.endPeriod, 6, 1) = 'Q' then cast(substring(p.endPeriod, 7, 1) as integer)
                                        when substring(p.endPeriod, 6, 1) = '1' then 2
                                        else 4
                                      end
                                )
                              end >= :requestedStart
                      ))
                    """
    )
    Page<Project> findPage(
            @Param("search") String search,
            @Param("status") ProjectStatus status,
            @Param("type") ProjectType type,
            @Param("departmentId") Long departmentId,
            @Param("filterPeriod") boolean filterPeriod,
            @Param("requestedStart") int requestedStart,
            @Param("requestedEnd") int requestedEnd,
            Pageable pageable
    );

    @Query("""
            select distinct p.keyResult.id
            from Project p
            where p.status = :status
              and p.keyResult.id is not null
            """)
    List<Long> findDistinctKeyResultIdsByStatus(@Param("status") ProjectStatus status);

    boolean existsByKeyResultIdAndStatus(Long keyResultId, ProjectStatus status);

    boolean existsByStartPeriodOrEndPeriod(String startPeriod, String endPeriod);

    boolean existsByDepartmentId(Long departmentId);
}
