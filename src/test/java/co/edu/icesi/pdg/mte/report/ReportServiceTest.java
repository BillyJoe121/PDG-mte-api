package co.edu.icesi.pdg.mte.report;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.Objective;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ObjectiveRepository objectiveRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private ProjectKeyResultLinkRepository linkRepository;

    private ReportService service;
    private Department department;
    private Objective objective;

    @BeforeEach
    void setUp() {
        service = new ReportService(projectRepository, objectiveRepository, departmentRepository, linkRepository);
        var unit = TestFixtures.unit(1L);
        department = TestFixtures.department(1L);
        objective = TestFixtures.objective(
                1L,
                unit,
                TestFixtures.period(1L),
                department,
                TestFixtures.goal(1L, unit),
                TestFixtures.strategicBet(1L)
        );
        objective.getKeyResults().get(0).setProgressPercentage(BigDecimal.valueOf(60));
    }

    @Test
    void buildsJsonReportsAndExportsWithFilters() {
        Project firstProject = project(1L, ProjectStatus.ACTIVO, department, "2026-1", "2026-2");
        Project secondProject = project(2L, ProjectStatus.FINALIZADO, department, "2026-Q1", "2026-1");
        Project unlinkedProject = project(3L, ProjectStatus.BORRADOR, null, "2026-1", null);
        when(projectRepository.findAll()).thenReturn(List.of(firstProject, secondProject, unlinkedProject));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(
                link(firstProject),
                link(secondProject)
        ));

        var general = service.general("2026-1", 1L, 1L);
        var quarterlyGeneral = service.general("2026-Q3", 1L, 1L);
        var departments = service.departments("2026-1");
        var ranking = service.objectiveRanking("2026-1", 1L);
        var comparison = service.periodComparison("2026-1", "2026-1", 1L, 1L);
        var consolidated = service.consolidated("2026-1", 1L, 1L);
        String csv = service.csv("2026-1", 1L, 1L);
        byte[] pdf = service.pdf("2026-1", 1L, 1L);

        assertThat(general.totalProjects()).isEqualTo(2);
        assertThat(general.activeProjects()).isEqualTo(1);
        assertThat(general.completedProjects()).isEqualTo(1);
        assertThat(quarterlyGeneral.totalProjects()).isEqualTo(1);
        assertThat(general.averageKeyResultCoverage()).isEqualByComparingTo("60.00");
        assertThat(departments).hasSize(1);
        assertThat(ranking.get(0).objectiveId()).isEqualTo(1L);
        assertThat(comparison.objectiveCoverageDelta()).isEqualByComparingTo("0.00");
        assertThat(consolidated.objectiveRanking()).hasSize(1);
        assertThat(csv).contains("filter,period,2026-1", "filter,departmentId,1", "filter,objectiveId,1", "general,totalProjects");
        assertThat(new String(pdf)).contains("%PDF-1.4");
    }

    @Test
    void handlesEmptyAndInvalidReportInputs() {
        when(projectRepository.findAll()).thenReturn(List.of());
        when(objectiveRepository.findAll()).thenReturn(List.of());
        when(departmentRepository.findAll()).thenReturn(List.of());

        assertThat(service.general("   ", null, null).averageObjectiveCoverage()).isEqualByComparingTo("0.00");
        assertThat(service.departments(null)).isEmpty();
        assertThat(service.objectiveRanking(null, null)).isEmpty();
        assertThatThrownBy(() -> service.general("2026-X", null, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.periodComparison(null, "2026-1", null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void periodComparisonCalculatesDeltasAndCsvEscapesNames() {
        Department quotedDepartment = TestFixtures.department(2L);
        quotedDepartment.setName("Depto \"Especial\"");
        var comparePeriod = TestFixtures.period(2L);
        comparePeriod.setName("2026-2");
        Objective baseObjective = objective;
        Objective compareObjective = TestFixtures.objective(
                2L,
                TestFixtures.unit(1L),
                comparePeriod,
                quotedDepartment,
                TestFixtures.goal(2L, TestFixtures.unit(1L)),
                TestFixtures.strategicBet(2L)
        );
        baseObjective.getKeyResults().get(0).setProgressPercentage(BigDecimal.valueOf(40));
        compareObjective.getKeyResults().get(0).setProgressPercentage(BigDecimal.valueOf(90));

        when(projectRepository.findAll()).thenReturn(List.of(
                project(1L, ProjectStatus.ACTIVO, department, "2026-1", "2026-1"),
                project(2L, ProjectStatus.FINALIZADO, quotedDepartment, "2026-2", "2026-2")
        ));
        when(objectiveRepository.findAll()).thenReturn(List.of(baseObjective, compareObjective));
        when(departmentRepository.findAll()).thenReturn(List.of(department, quotedDepartment));

        var comparison = service.periodComparison("2026-1", "2026-2", null);
        String csv = service.csv("2026-2", null, null);

        assertThat(comparison.objectiveCoverageDelta()).isEqualByComparingTo("50.00");
        assertThat(comparison.keyResultCoverageDelta()).isEqualByComparingTo("50.00");
        assertThat(csv).contains("\"Depto \"\"Especial\"\"\"");
    }

    @Test
    void unfilteredReportsIncludeAllDataAndHandleNullObjectiveNamesAndProgress() {
        Department otherDepartment = TestFixtures.department(2L);
        var namelessObjective = TestFixtures.objective(
                2L,
                TestFixtures.unit(1L),
                TestFixtures.period(1L),
                otherDepartment,
                TestFixtures.goal(2L, TestFixtures.unit(1L)),
                TestFixtures.strategicBet(2L)
        );
        namelessObjective.setName(null);
        namelessObjective.getKeyResults().get(0).setProgressPercentage(null);

        when(projectRepository.findAll()).thenReturn(List.of(
                project(1L, ProjectStatus.ACTIVO, null, "2026-Q2", "2026-Q3"),
                project(2L, ProjectStatus.SUSPENDIDO, department, "2025-1", "2025-2")
        ));
        when(objectiveRepository.findAll()).thenReturn(List.of(namelessObjective));
        when(departmentRepository.findAll()).thenReturn(List.of(otherDepartment));

        var general = service.general(null, null, null);
        var ranking = service.objectiveRanking(null, null);
        String csv = service.csv(null, null, null);

        assertThat(general.period()).isNull();
        assertThat(general.totalProjects()).isEqualTo(2);
        assertThat(general.totalObjectives()).isEqualTo(1);
        assertThat(general.averageKeyResultCoverage()).isEqualByComparingTo("0.00");
        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).objectiveName()).isNull();
        assertThat(csv).contains("objective,,");
    }

    private Project project(Long id, ProjectStatus status, Department department, String startPeriod, String endPeriod) {
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

    private ProjectKeyResultLink link(Project project) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setActive(true);
        return link;
    }
}
