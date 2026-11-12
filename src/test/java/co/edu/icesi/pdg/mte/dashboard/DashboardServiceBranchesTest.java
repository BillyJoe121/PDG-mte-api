package co.edu.icesi.pdg.mte.dashboard;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.StrategicBet;
import co.edu.icesi.pdg.mte.strategy.StrategicBetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceBranchesTest {
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private KeyResultRepository keyResultRepository;
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
    private KeyResult keyResult;

    @BeforeEach
    void setUp() {
        service = new DashboardService(projectRepository, keyResultRepository, departmentRepository,
                strategicBetRepository, goalRepository, linkRepository, periodRepository);
        department = TestFixtures.department(1L);
        bet = TestFixtures.strategicBet(1L);
        keyResult = TestFixtures.keyResult(1L, TestFixtures.unit(1L), TestFixtures.period(1L),
                TestFixtures.goal(1L, TestFixtures.unit(1L)), bet);
    }

    @Test
    void onlyProjectsWithBothAKeyResultAndADepartmentAttributeKeyResultsToDepartments() {
        Project withoutDepartment = project(1L, null, "2026-1", null, ProjectStatus.ACTIVO);
        withoutDepartment.setKeyResult(keyResult);
        Project withoutKeyResult = project(2L, department, "2026-1", null, ProjectStatus.ACTIVO);
        Project linkedWithoutDepartment = project(3L, null, "2026-1", null, ProjectStatus.ACTIVO);
        when(projectRepository.findAll()).thenReturn(List.of(withoutDepartment, withoutKeyResult, linkedWithoutDepartment));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, null), link(keyResult, linkedWithoutDepartment)));

        var departments = service.departments(null);

        assertThat(departments).singleElement().satisfies(item -> {
            assertThat(item.keyResults()).isZero();
            assertThat(item.activeProjects()).isEqualTo(1);
        });
    }

    @Test
    void blankProjectEndPeriodFallsBackToTheStartPeriod() {
        when(projectRepository.findAll()).thenReturn(List.of(
                project(1L, department, "2026-1", "   ", ProjectStatus.ACTIVO),
                project(2L, department, "2026-2", "", ProjectStatus.ACTIVO)));
        when(keyResultRepository.findAll()).thenReturn(List.of());

        assertThat(service.summary("2026-1").activeProjects()).isEqualTo(1);
        assertThat(service.summary("2026-Q4").activeProjects()).isEqualTo(1);
    }

    @Test
    void keyResultsWithoutAPeriodNameAreLeftOutWhenAFilterIsApplied() {
        KeyResult withoutPeriod = TestFixtures.keyResult(2L, TestFixtures.unit(1L));
        withoutPeriod.setAcademicPeriod(null);
        KeyResult withoutName = TestFixtures.keyResult(3L, TestFixtures.unit(1L), TestFixtures.period(3L), null, null);
        withoutName.getAcademicPeriod().setName(null);
        when(projectRepository.findAll()).thenReturn(List.of());
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, withoutPeriod, withoutName));

        var summary = service.summary("2026-1");

        assertThat(summary.activeKeyResults() + summary.draftKeyResults() + summary.closedKeyResults()).isEqualTo(1);
    }

    @Test
    void storedPeriodsMustBeWellFormedWhenAFilterIsApplied() {
        when(projectRepository.findAll()).thenReturn(List.of(project(1L, department, " ", null, ProjectStatus.ACTIVO)));
        assertThatThrownBy(() -> service.summary("2026-1")).isInstanceOf(BusinessException.class);

        when(projectRepository.findAll()).thenReturn(List.of(project(1L, department, null, null, ProjectStatus.ACTIVO)));
        assertThatThrownBy(() -> service.summary("2026-1")).isInstanceOf(BusinessException.class);

        when(projectRepository.findAll()).thenReturn(List.of(project(1L, department, "inicio", null, ProjectStatus.ACTIVO)));
        assertThatThrownBy(() -> service.summary("2026-1")).isInstanceOf(BusinessException.class);

        when(projectRepository.findAll()).thenReturn(List.of());
        keyResult.getAcademicPeriod().setName("periodo");
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        assertThatThrownBy(() -> service.summary("2026-1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void executionCountsSkipUnusableLinksAndLetFinishedProjectsWin() {
        Project active = project(10L, department, "2026-1", null, ProjectStatus.ACTIVO);
        Project finishedCopy = project(10L, department, "2026-1", null, ProjectStatus.FINALIZADO);
        Project other = project(11L, department, "2026-1", null, ProjectStatus.SUSPENDIDO);
        Project withoutId = project(null, department, "2026-1", null, ProjectStatus.ACTIVO);
        Project outsideThePeriod = project(12L, department, "2025-1", null, ProjectStatus.ACTIVO);
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, null),
                link(keyResult, withoutId),
                link(keyResult, outsideThePeriod),
                link(keyResult, active),
                link(keyResult, finishedCopy),
                link(keyResult, active),
                link(keyResult, other)));

        var bets = service.strategicBets("2026-1");

        assertThat(bets).singleElement().satisfies(item -> {
            assertThat(item.completedProjects()).isEqualTo(1);
            assertThat(item.inProgressProjects()).isEqualTo(1);
        });
    }

    private static Project project(Long id, Department department, String startPeriod, String endPeriod, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setName("Proyecto " + id);
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setDepartment(department);
        project.setStatus(status);
        project.setStartPeriod(startPeriod);
        project.setEndPeriod(endPeriod);
        return project;
    }

    private static ProjectKeyResultLink link(KeyResult keyResult, Project project) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setProject(project);
        link.setActive(true);
        return link;
    }
}
