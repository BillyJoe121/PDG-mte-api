package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnitRepository;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.security.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

abstract class StrategyServiceTestSupport {
    @Mock
    protected StrategicBetRepository strategicBetRepository;
    @Mock
    protected InstitutionalGoalRepository goalRepository;
    @Mock
    protected KeyResultRepository keyResultRepository;
    @Mock
    protected WorldRepository worldRepository;
    @Mock
    protected MeasurementUnitRepository unitRepository;
    @Mock
    protected AcademicPeriodRepository periodRepository;
    @Mock
    protected ProjectKeyResultLinkRepository linkRepository;
    @Mock
    protected AuditService auditService;
    @Mock
    protected KeyResultProgressRecordRepository progressRecordRepository;
    @Mock
    protected ProjectRepository projectRepository;

    protected StrategyService service;
    protected KeyResultService keyResultService;
    protected MeasurementUnit unit;
    protected AcademicPeriod period;
    protected StrategicBet bet;
    protected InstitutionalGoal goal;
    protected KeyResult keyResult;

    @BeforeEach
    void setUpStrategyService() {
        StrategyExecutionSummaryService executionSummaryService = new StrategyExecutionSummaryService(keyResultRepository, linkRepository);
        StrategicHierarchyTreeService hierarchyTreeService = new StrategicHierarchyTreeService(
                strategicBetRepository,
                goalRepository,
                keyResultRepository,
                periodRepository,
                linkRepository,
                executionSummaryService
        );
        service = new StrategyService(
                strategicBetRepository,
                goalRepository,
                worldRepository,
                unitRepository,
                periodRepository,
                auditService,
                executionSummaryService,
                hierarchyTreeService
        );
        keyResultService = new KeyResultService(
                keyResultRepository,
                goalRepository,
                strategicBetRepository,
                unitRepository,
                periodRepository,
                linkRepository,
                auditService,
                progressRecordRepository,
                projectRepository
        );
        unit = TestFixtures.unit(1L);
        period = TestFixtures.period(1L);
        bet = TestFixtures.strategicBet(1L);
        goal = TestFixtures.goal(1L, unit);
        keyResult = TestFixtures.keyResult(1L, unit, period, goal, bet);
        org.mockito.Mockito.lenient().when(worldRepository.findAll()).thenReturn(List.of(TestFixtures.world(1L)));
        org.mockito.Mockito.lenient().when(worldRepository.findById(1L)).thenReturn(Optional.of(TestFixtures.world(1L)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                TestUsers.admin(),
                null,
                List.of()
        ));
    }

    protected StrategyDtos.KeyResultRequest keyResultRequest(List<Long> goalIds, List<Long> strategicBetIds) {
        return keyResultRequest("KR", BigDecimal.valueOf(100), goalIds, strategicBetIds);
    }

    protected StrategyDtos.KeyResultRequest keyResultRequest(
            String name,
            BigDecimal targetValue,
            List<Long> goalIds,
            List<Long> strategicBetIds
    ) {
        return keyResultRequest(name, targetValue, goalIds, strategicBetIds, null);
    }

    protected StrategyDtos.KeyResultRequest keyResultRequest(
            String name,
            BigDecimal targetValue,
            List<Long> goalIds,
            List<Long> strategicBetIds,
            String plannerUrl
    ) {
        return new StrategyDtos.KeyResultRequest(
                name,
                "Descripcion KR",
                "Cobertura",
                BigDecimal.ZERO,
                targetValue,
                1L,
                1L,
                goalIds,
                strategicBetIds,
                plannerUrl
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
}
