package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnitRepository;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntryRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

abstract class StrategyServiceTestSupport {
    @Mock
    protected StrategicBetRepository strategicBetRepository;
    @Mock
    protected InstitutionalGoalRepository goalRepository;
    @Mock
    protected ObjectiveRepository objectiveRepository;
    @Mock
    protected KeyResultRepository keyResultRepository;
    @Mock
    protected MeasurementUnitRepository unitRepository;
    @Mock
    protected AcademicPeriodRepository periodRepository;
    @Mock
    protected DepartmentRepository departmentRepository;
    @Mock
    protected ProjectKeyResultLinkRepository linkRepository;
    @Mock
    protected ProjectProgressEntryRepository progressRepository;
    @Mock
    protected AuditService auditService;

    protected StrategyService service;
    protected MeasurementUnit unit;
    protected AcademicPeriod period;
    protected Department department;
    protected StrategicBet bet;
    protected InstitutionalGoal goal;
    protected Objective objective;

    @BeforeEach
    void setUpStrategyService() {
        StrategyExecutionSummaryService executionSummaryService = new StrategyExecutionSummaryService(objectiveRepository, linkRepository);
        StrategicHierarchyTreeService hierarchyTreeService = new StrategicHierarchyTreeService(
                strategicBetRepository,
                goalRepository,
                objectiveRepository,
                linkRepository,
                executionSummaryService
        );
        ObjectiveCoverageTrendService coverageTrendService = new ObjectiveCoverageTrendService(
                objectiveRepository,
                linkRepository,
                progressRepository
        );
        service = new StrategyService(
                strategicBetRepository,
                goalRepository,
                objectiveRepository,
                keyResultRepository,
                unitRepository,
                periodRepository,
                departmentRepository,
                linkRepository,
                auditService,
                executionSummaryService,
                hierarchyTreeService,
                coverageTrendService
        );
        unit = TestFixtures.unit(1L);
        period = TestFixtures.period(1L);
        department = TestFixtures.department(1L);
        bet = TestFixtures.strategicBet(1L);
        goal = TestFixtures.goal(1L, unit);
        objective = TestFixtures.objective(1L, unit, period, department, goal, bet);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ExternalUserContext.mock(),
                null,
                List.of()
        ));
    }

    protected StrategyDtos.ObjectiveRequest objectiveRequest(
            Long departmentId,
            Long periodId,
            Long goalId,
            Long strategicBetId,
            Long unitId
    ) {
        return new StrategyDtos.ObjectiveRequest(
                "Objetivo",
                "Descripcion",
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                List.of(new StrategyDtos.KeyResultRequest(
                        "KR",
                        "Descripcion KR",
                        "Porcentaje",
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(100),
                        unitId
                ))
        );
    }

    protected ProjectKeyResultLink link(KeyResult keyResult, Project project, int weight) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setProject(project);
        link.setContributionWeight(BigDecimal.valueOf(weight));
        link.setContributionType(ContributionType.DIRECTA);
        return link;
    }

    protected Project project(Long id, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setName("Proyecto " + id);
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setStatus(status);
        project.setStartPeriod("2026-1");
        project.setEndPeriod("2026-1");
        return project;
    }

    protected ProjectProgressEntry progressEntry(Project project, BigDecimal progress, Instant createdAt) {
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(progress);
        entry.setComment("Avance");
        ReflectionTestUtils.setField(entry, "createdAt", createdAt);
        return entry;
    }
}
