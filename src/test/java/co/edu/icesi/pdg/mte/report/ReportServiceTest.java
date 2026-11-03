package co.edu.icesi.pdg.mte.report;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.catalog.PeriodStatus;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultStatus;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private KeyResultRepository keyResultRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private ProjectKeyResultLinkRepository linkRepository;
    @Mock
    private AcademicPeriodRepository periodRepository;

    private ReportService service;
    private Department department;
    private KeyResult keyResult;

    @BeforeEach
    void setUp() {
        service = new ReportService(projectRepository, keyResultRepository, departmentRepository, linkRepository, periodRepository);
        var unit = TestFixtures.unit(1L);
        department = TestFixtures.department(1L);
        keyResult = TestFixtures.keyResult(
                1L,
                unit,
                TestFixtures.period(1L),
                TestFixtures.goal(1L, unit),
                TestFixtures.strategicBet(1L)
        );
        keyResult.setProgressPercentage(BigDecimal.valueOf(60));
    }

    @Test
    void buildsJsonReportsAndExportsWithFilters() {
        Project firstProject = project(1L, ProjectStatus.ACTIVO, department, "2026-1", "2026-2");
        Project secondProject = project(2L, ProjectStatus.FINALIZADO, department, "2026-Q1", "2026-1");
        Project unlinkedProject = project(3L, ProjectStatus.BORRADOR, null, "2026-1", null);
        when(projectRepository.findAll()).thenReturn(List.of(firstProject, secondProject, unlinkedProject));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, firstProject),
                link(keyResult, secondProject)
        ));

        var general = service.general("2026-1", 1L);
        var quarterlyGeneral = service.general("2026-Q3", 1L);
        var departments = service.departments("2026-1");
        var ranking = service.keyResultRanking("2026-1", 1L);
        var comparison = service.periodComparison("2026-1", "2026-1", 1L);
        var consolidated = service.consolidated("2026-1", 1L);
        String csv = service.csv("2026-1", 1L);
        byte[] pdf = service.pdf("2026-1", 1L);

        assertThat(general.totalProjects()).isEqualTo(2);
        assertThat(general.activeProjects()).isEqualTo(1);
        assertThat(general.completedProjects()).isEqualTo(1);
        assertThat(general.totalKeyResults()).isEqualTo(1);
        assertThat(quarterlyGeneral.totalProjects()).isEqualTo(1);
        assertThat(quarterlyGeneral.totalKeyResults()).isZero();
        assertThat(general.activeKeyResults()).isEqualTo(1);
        assertThat(general.closedKeyResults()).isZero();
        assertThat(departments).singleElement().satisfies(item -> {
            assertThat(item.projects()).isEqualTo(2);
            assertThat(item.keyResults()).isEqualTo(1);
            assertThat(item.closedKeyResults()).isZero();
        });
        assertThat(ranking).singleElement().satisfies(item -> {
            assertThat(item.keyResultId()).isEqualTo(1L);
            assertThat(item.goalNames()).containsExactly("Meta");
            assertThat(item.strategicBetNames()).containsExactly("Apuesta");
            assertThat(item.period()).isEqualTo("2026-1");
            assertThat(item.linkedProjects()).isEqualTo(2);
            assertThat(item.progressPercentage()).isEqualByComparingTo("60");
        });
        assertThat(comparison.base()).isEqualTo(comparison.compare());
        assertThat(consolidated.keyResultRanking()).hasSize(1);
        assertThat(csv).contains("seccion,nombre,valor", "filtro,periodo,2026-1", "filtro,departamentoId,1",
                "general,totalProyectos,2", "general,totalKRs,1", "general,krsActivos,1", "general,krsCerrados,0",
                "departamentoProyectos,\"" + department.getName() + "\",2",
                "departamentoKRs,\"" + department.getName() + "\",1",
                "departamentoKRsCerrados,\"" + department.getName() + "\",0", "krAvanceRegistrado,\"KR\",60");
        assertThat(csv).doesNotContain("cobertura", "Promedio");
        assertThat(csv.toLowerCase()).doesNotContain("objective", "objetivo");
        assertThat(new String(pdf)).contains("%PDF-1.4", "Reporte consolidado MSP");
    }

    @Test
    void handlesEmptyAndInvalidReportInputs() {
        when(projectRepository.findAll()).thenReturn(List.of());
        when(keyResultRepository.findAll()).thenReturn(List.of());
        when(departmentRepository.findAll()).thenReturn(List.of());

        assertThat(service.general("   ", null).totalKeyResults()).isZero();
        assertThat(service.general(null, null).closedKeyResults()).isZero();
        assertThat(service.departments(null)).isEmpty();
        assertThat(service.keyResultRanking(null, null)).isEmpty();
        assertThatThrownBy(() -> service.general("2026-X", null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.periodComparison(null, "2026-1", null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void periodComparisonReturnsBothPeriodsWithoutDeltaAndCsvEscapesNames() {
        Department quotedDepartment = TestFixtures.department(2L);
        quotedDepartment.setName("Depto \"Especial\"");
        var comparePeriod = TestFixtures.period(2L);
        comparePeriod.setName("2026-2");
        KeyResult compareKeyResult = TestFixtures.keyResult(
                2L,
                TestFixtures.unit(1L),
                comparePeriod,
                TestFixtures.goal(2L, TestFixtures.unit(1L)),
                null
        );
        keyResult.setProgressPercentage(BigDecimal.valueOf(40));
        compareKeyResult.setProgressPercentage(BigDecimal.valueOf(90));
        compareKeyResult.setStatus(KeyResultStatus.CERRADO);

        when(projectRepository.findAll()).thenReturn(List.of(
                project(1L, ProjectStatus.ACTIVO, department, "2026-1", "2026-1"),
                project(2L, ProjectStatus.FINALIZADO, quotedDepartment, "2026-2", "2026-2")
        ));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, compareKeyResult));
        when(departmentRepository.findAll()).thenReturn(List.of(department, quotedDepartment));

        var comparison = service.periodComparison("2026-1", "2026-2", null);
        String csv = service.csv("2026-2", null);

        assertThat(comparison.base().totalKeyResults()).isEqualTo(1);
        assertThat(comparison.compare().totalKeyResults()).isEqualTo(1);
        assertThat(comparison.basePeriod()).isEqualTo("2026-1");
        assertThat(comparison.comparePeriod()).isEqualTo("2026-2");
        assertThat(comparison.base().closedKeyResults()).isZero();
        assertThat(comparison.compare().closedKeyResults()).isEqualTo(1);
        assertThat(csv).contains("\"Depto \"\"Especial\"\"\"");
    }

    @Test
    void withoutPeriodReportsAndExportsUseTheMostRecentActivePeriod() {
        var activePeriod = TestFixtures.period(2L);
        activePeriod.setName("2026-2");
        KeyResult activeKeyResult = TestFixtures.keyResult(
                2L,
                TestFixtures.unit(1L),
                activePeriod,
                TestFixtures.goal(2L, TestFixtures.unit(1L)),
                null
        );
        activeKeyResult.setStatus(KeyResultStatus.CERRADO);
        when(periodRepository.findFirstByStatusOrderByStartDateDesc(PeriodStatus.ACTIVO)).thenReturn(Optional.of(activePeriod));
        when(projectRepository.findAll()).thenReturn(List.of(
                project(1L, ProjectStatus.ACTIVO, department, "2026-1", null),
                project(2L, ProjectStatus.FINALIZADO, department, "2026-2", null)
        ));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, activeKeyResult));
        when(departmentRepository.findAll()).thenReturn(List.of(department));

        var general = service.general(null, null);
        var ranking = service.keyResultRanking("  ", null);
        var departments = service.departments(null);
        String csv = service.csv(null, null);
        byte[] pdf = service.pdf("", null);

        assertThat(general.period()).isEqualTo("2026-2");
        assertThat(general.totalProjects()).isEqualTo(1);
        assertThat(general.completedProjects()).isEqualTo(1);
        assertThat(general.totalKeyResults()).isEqualTo(1);
        assertThat(general.closedKeyResults()).isEqualTo(1);
        assertThat(ranking).extracting(item -> item.keyResultId()).containsExactly(2L);
        assertThat(departments).singleElement().satisfies(item -> assertThat(item.projects()).isEqualTo(1));
        assertThat(csv).contains("filtro,periodo,2026-2", "general,totalProyectos,1", "general,totalKRs,1");
        assertThat(new String(pdf)).contains("filtro  periodo  2026-2");
        assertThat(service.general("2026-1", null).period()).isEqualTo("2026-1");
        assertThatThrownBy(() -> service.periodComparison(" ", "2026-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("basePeriod");
    }

    @Test
    void withoutPeriodCountsAllPeriodsWhenThereIsNoValidActivePeriod() {
        var legacyPeriod = TestFixtures.period(3L);
        legacyPeriod.setName("Periodo heredado");
        when(periodRepository.findFirstByStatusOrderByStartDateDesc(PeriodStatus.ACTIVO))
                .thenReturn(Optional.empty(), Optional.of(legacyPeriod));
        when(projectRepository.findAll()).thenReturn(List.of(project(1L, ProjectStatus.ACTIVO, department, "2025-1", null)));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));

        var withoutActivePeriod = service.general(null, null);
        var withLegacyActivePeriod = service.general(null, null);

        assertThat(withoutActivePeriod.period()).isNull();
        assertThat(withoutActivePeriod.totalProjects()).isEqualTo(1);
        assertThat(withoutActivePeriod.totalKeyResults()).isEqualTo(1);
        assertThat(withLegacyActivePeriod.period()).isNull();
        assertThat(withLegacyActivePeriod.totalProjects()).isEqualTo(1);
    }

    @Test
    void attributesKeyResultsToDepartmentsThroughProjectsAndHandlesNullNamesAndProgress() {
        Department otherDepartment = TestFixtures.department(2L);
        otherDepartment.setName("Otro departamento");
        KeyResult namelessKeyResult = TestFixtures.keyResult(
                2L,
                TestFixtures.unit(1L),
                TestFixtures.period(1L),
                null,
                TestFixtures.strategicBet(2L)
        );
        namelessKeyResult.setName(null);
        namelessKeyResult.setProgressPercentage(null);
        Project primaryProject = project(1L, ProjectStatus.ACTIVO, otherDepartment, "2026-Q2", "2026-Q3");
        primaryProject.setKeyResult(namelessKeyResult);
        Project linkedProject = project(2L, ProjectStatus.SUSPENDIDO, department, "2025-1", "2025-2");

        when(projectRepository.findAll()).thenReturn(List.of(primaryProject, linkedProject));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, namelessKeyResult));
        when(departmentRepository.findAll()).thenReturn(List.of(department, otherDepartment));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, linkedProject)
        ));

        var general = service.general(null, null);
        var otherDepartmentGeneral = service.general(null, 2L);
        var ranking = service.keyResultRanking(null, null);
        var departments = service.departments(null);
        String csv = service.csv(null, null);

        assertThat(general.period()).isNull();
        assertThat(general.totalProjects()).isEqualTo(2);
        assertThat(general.totalKeyResults()).isEqualTo(2);
        assertThat(general.activeKeyResults()).isEqualTo(2);
        assertThat(otherDepartmentGeneral.totalKeyResults()).isEqualTo(1);
        assertThat(departments).extracting(item -> item.keyResults()).containsExactly(1L, 1L);
        assertThat(ranking).extracting(item -> item.keyResultId()).containsExactly(1L, 2L);
        assertThat(ranking.get(1).keyResultName()).isNull();
        assertThat(ranking.get(1).goalNames()).isEmpty();
        assertThat(ranking.get(1).linkedProjects()).isZero();
        assertThat(ranking.get(1).progressPercentage()).isNull();
        assertThat(csv).contains("krAvanceRegistrado,,\n");
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

    private ProjectKeyResultLink link(KeyResult keyResult, Project project) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setProject(project);
        link.setActive(true);
        return link;
    }
}
