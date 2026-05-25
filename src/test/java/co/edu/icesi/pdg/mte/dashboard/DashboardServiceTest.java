package co.edu.icesi.pdg.mte.dashboard;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoal;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.Objective;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import co.edu.icesi.pdg.mte.strategy.ObjectiveStatus;
import co.edu.icesi.pdg.mte.strategy.StrategicBet;
import co.edu.icesi.pdg.mte.strategy.StrategicBetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ObjectiveRepository objectiveRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private StrategicBetRepository strategicBetRepository;
    @Mock
    private InstitutionalGoalRepository goalRepository;
    @Mock
    private ProjectKeyResultLinkRepository linkRepository;
    @Mock
    private AcademicPeriodRepository periodRepository;

    private DashboardService service;
    private Department department;
    private StrategicBet bet;
    private InstitutionalGoal goal;
    private Objective objective;
    private KeyResult lowKr;
    private KeyResult atRiskKr;
    private KeyResult onTrackKr;
    private KeyResult completedKr;

    @BeforeEach
    void setUp() {
        service = new DashboardService(projectRepository, objectiveRepository, departmentRepository, strategicBetRepository, goalRepository, linkRepository, periodRepository);
        MeasurementUnit unit = TestFixtures.unit(1L);
        AcademicPeriod period = TestFixtures.period(1L);
        department = TestFixtures.department(1L);
        bet = TestFixtures.strategicBet(1L);
        goal = TestFixtures.goal(1L, unit);
        objective = TestFixtures.objective(1L, unit, period, department, goal, bet);
        objective.getKeyResults().clear();
        lowKr = keyResult(1L, unit, BigDecimal.valueOf(20));
        atRiskKr = keyResult(2L, unit, BigDecimal.valueOf(45));
        onTrackKr = keyResult(3L, unit, BigDecimal.valueOf(75));
        completedKr = keyResult(4L, unit, BigDecimal.valueOf(100));
        objective.addKeyResult(lowKr);
        objective.addKeyResult(atRiskKr);
        objective.addKeyResult(onTrackKr);
        objective.addKeyResult(completedKr);
    }

    @Test
    void buildsDashboardSummaryAndChartsForValidPeriod() {
        Project active = project(1L, ProjectStatus.ACTIVO, department, "2026-1", "2026-2");
        Project completed = project(2L, ProjectStatus.FINALIZADO, department, "2026-Q1", "2026-1");
        Project draft = project(3L, ProjectStatus.BORRADOR, department, "2026-1", null);
        Project suspended = project(4L, ProjectStatus.SUSPENDIDO, null, "2026-1", null);
        Project archivedOutsidePeriod = project(5L, ProjectStatus.ARCHIVADO, department, "2026-2", null);
        Project withoutId = project(null, ProjectStatus.ACTIVO, department, "2026-1", null);
        Project outsidePeriod = project(6L, ProjectStatus.ACTIVO, department, "2026-2", null);
        Objective closedLowObjective = TestFixtures.objective(2L, TestFixtures.unit(1L), TestFixtures.period(1L), department, objective.getGoal(), bet);
        closedLowObjective.getKeyResults().clear();
        closedLowObjective.setStatus(ObjectiveStatus.CERRADO);
        KeyResult nullProgressKr = keyResult(5L, TestFixtures.unit(1L), null);
        closedLowObjective.addKeyResult(nullProgressKr);

        when(projectRepository.findAllByOverlappingPeriod(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(active, completed, draft, suspended, archivedOutsidePeriod, withoutId, outsidePeriod));
        when(objectiveRepository.findAllByOverlappingPeriod(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(objective, closedLowObjective));
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(anyCollection())).thenReturn(List.of(
                link(lowKr, active),
                link(atRiskKr, completed),
                link(atRiskKr, active),
                link(onTrackKr, null),
                link(completedKr, completed),
                link(completedKr, withoutId),
                link(completedKr, outsidePeriod),
                link(nullProgressKr, active)
        ));
        when(periodRepository.findFirstByStatusOrderByStartDateDesc(co.edu.icesi.pdg.mte.catalog.PeriodStatus.ACTIVO))
                .thenReturn(Optional.of(TestFixtures.period(1L)));

        var summary = service.summary("2026-1");
        var byStatus = service.projectsByStatus("2026-1");
        var byStatusForQuarterInsideRange = service.projectsByStatus("2026-Q3");
        var byProgress = service.keyResultsByProgress("2026-1");
        var departments = service.departments("2026-1");
        var bets = service.strategicBets("2026-1");
        var goals = service.goals("2026-1");
        var activePeriodSummary = service.summary(null);
        var dashboard = service.dashboard("2026-1");

        assertThat(summary.activeProjects()).isEqualTo(2);
        assertThat(summary.completedProjects()).isEqualTo(1);
        assertThat(summary.draftProjects()).isEqualTo(1);
        assertThat(summary.suspendedProjects()).isEqualTo(1);
        assertThat(summary.archivedProjects()).isZero();
        assertThat(summary.completedKeyResults()).isEqualTo(1);
        assertThat(summary.inProgressKeyResults()).isEqualTo(4);
        assertThat(summary.objectivesInFollowUp()).isEqualTo(1);
        assertThat(summary.lowCompletionObjectives()).isEqualTo(1);
        assertThat(summary.completedObjectives()).isZero();
        assertThat(summary.objectivesAbove50()).isEqualTo(1);
        assertThat(summary.objectivesBetween0And50()).isZero();
        assertThat(summary.objectivesAtZero()).isEqualTo(1);
        assertThat(summary.averageKeyResultCoverage()).isEqualByComparingTo("48.00");
        assertThat(activePeriodSummary.period()).isEqualTo("2026-1");
        assertThat(byStatus).extracting("status").containsExactly("BORRADOR", "ACTIVO", "FINALIZADO", "SUSPENDIDO", "ARCHIVADO");
        assertThat(byStatus).extracting("count").containsExactly(1L, 2L, 1L, 1L, 0L);
        assertThat(byStatusForQuarterInsideRange).extracting("count").containsExactly(0L, 2L, 0L, 0L, 1L);
        assertThat(byProgress).extracting("bucket").containsExactly("COMPLETED", "ON_TRACK", "AT_RISK", "LOW");
        assertThat(byProgress).extracting("count").containsExactly(1L, 1L, 1L, 2L);
        assertThat(departments.get(0).activeProjects()).isEqualTo(2);
        assertThat(departments.get(0).completedProjects()).isEqualTo(1);
        assertThat(departments.get(0).objectivesAbove50()).isEqualTo(1);
        assertThat(departments.get(0).objectivesAtZero()).isEqualTo(1);
        assertThat(bets.get(0).objectives()).isEqualTo(2);
        assertThat(bets.get(0).objectivesAbove50()).isEqualTo(1);
        assertThat(bets.get(0).objectivesAtZero()).isEqualTo(1);
        assertThat(bets.get(0).keyResults()).isEqualTo(5);
        assertThat(bets.get(0).completedProjects()).isEqualTo(1);
        assertThat(bets.get(0).inProgressProjects()).isEqualTo(1);
        assertThat(goals.get(0).objectives()).isEqualTo(2);
        assertThat(goals.get(0).objectivesAbove50()).isEqualTo(1);
        assertThat(goals.get(0).objectivesAtZero()).isEqualTo(1);
        assertThat(goals.get(0).keyResults()).isEqualTo(5);
        assertThat(dashboard.summary().activeProjects()).isEqualTo(2);
        assertThat(dashboard.projectsByStatus()).hasSize(5);
        assertThat(dashboard.keyResultsByProgress()).hasSize(4);
    }

    @Test
    void returnsEmptyAggregatesWhenThereIsNoData() {
        when(projectRepository.findAll()).thenReturn(List.of());
        when(objectiveRepository.findAll()).thenReturn(List.of());
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(strategicBetRepository.findAll()).thenReturn(List.of());
        when(goalRepository.findAll()).thenReturn(List.of());

        assertThat(service.summary(null).objectivesAtZero()).isZero();
        assertThat(service.summary("   ").averageKeyResultCoverage()).isEqualByComparingTo("0.00");
        assertThat(service.projectsByStatus(null)).extracting("count").containsExactly(0L, 0L, 0L, 0L, 0L);
        assertThat(service.keyResultsByProgress(null)).extracting("count").containsExactly(0L, 0L, 0L, 0L);
        assertThat(service.departments(null)).isEmpty();
        assertThat(service.strategicBets(null)).isEmpty();
        assertThat(service.goals(null)).isEmpty();
    }

    @Test
    void sadPathsRejectMalformedPeriodsForAllDashboardEndpoints() {
        assertThatThrownBy(() -> service.summary("2026-X"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("periodo");
        assertThatThrownBy(() -> service.projectsByStatus("2026-0"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.keyResultsByProgress("abc"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.departments("2026-Q5"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.strategicBets("26-1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.goals("26-1"))
                .isInstanceOf(BusinessException.class);
    }

    private KeyResult keyResult(Long id, MeasurementUnit unit, BigDecimal progress) {
        KeyResult keyResult = TestFixtures.keyResult(id, unit);
        keyResult.setProgressPercentage(progress);
        return keyResult;
    }

    private Project project(Long id, ProjectStatus status, Department department, String startPeriod, String endPeriod) {
        Project project = new Project();
        project.setId(id);
        project.setName("Proyecto " + id);
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setDepartment(department);
        project.setDepartmentName(department == null ? "Sin catalogar" : department.getName());
        project.setStatus(status);
        project.setStartPeriod(startPeriod);
        project.setEndPeriod(endPeriod);
        project.setGlobalProgress(BigDecimal.ZERO);
        return project;
    }

    private ProjectKeyResultLink link(KeyResult keyResult, Project project) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setProject(project);
        link.setContributionWeight(BigDecimal.valueOf(25));
        return link;
    }
}
