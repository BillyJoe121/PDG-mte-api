package co.edu.icesi.pdg.mte.report;

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
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
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
class ReportServiceBranchesTest {
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
        department = TestFixtures.department(1L);
        keyResult = TestFixtures.keyResult(1L, TestFixtures.unit(1L), TestFixtures.period(1L), null, null);
    }

    @Test
    void rankingShowsNoPeriodNameForKeyResultsWithoutAcademicPeriod() {
        keyResult.setAcademicPeriod(null);
        when(projectRepository.findAll()).thenReturn(List.of());
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));

        var ranking = service.keyResultRanking(null, null);

        assertThat(ranking).singleElement().satisfies(item -> {
            assertThat(item.keyResultId()).isEqualTo(1L);
            assertThat(item.period()).isNull();
        });
    }

    @Test
    void onlyProjectsWithBothAKeyResultAndADepartmentAttributeKeyResultsToDepartments() {
        Project withoutDepartment = project(1L, null, "2026-1", null);
        withoutDepartment.setKeyResult(keyResult);
        Project withoutKeyResult = project(2L, department, "2026-1", null);
        Project linkedWithoutDepartment = project(3L, null, "2026-1", null);
        when(projectRepository.findAll()).thenReturn(List.of(withoutDepartment, withoutKeyResult, linkedWithoutDepartment));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, null), link(keyResult, linkedWithoutDepartment)));

        var general = service.general(null, null);
        var scoped = service.general(null, department.getId());

        assertThat(general.totalKeyResults()).isEqualTo(1);
        assertThat(scoped.totalKeyResults()).isZero();
        assertThat(scoped.totalProjects()).isEqualTo(1);
    }

    @Test
    void blankProjectEndPeriodFallsBackToTheStartPeriod() {
        when(projectRepository.findAll()).thenReturn(List.of(
                project(1L, department, "2026-1", "   "),
                project(2L, department, "2026-2", "")));
        when(keyResultRepository.findAll()).thenReturn(List.of());

        var firstSemester = service.general("2026-1", null);
        var secondSemester = service.general("2026-Q4", null);

        assertThat(firstSemester.totalProjects()).isEqualTo(1);
        assertThat(secondSemester.totalProjects()).isEqualTo(1);
    }

    @Test
    void keyResultsWithoutAPeriodNameAreLeftOutWhenAFilterIsApplied() {
        KeyResult withoutPeriod = TestFixtures.keyResult(2L, TestFixtures.unit(1L));
        withoutPeriod.setAcademicPeriod(null);
        KeyResult withoutName = TestFixtures.keyResult(3L, TestFixtures.unit(1L), TestFixtures.period(3L), null, null);
        withoutName.getAcademicPeriod().setName(null);
        when(projectRepository.findAll()).thenReturn(List.of());
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, withoutPeriod, withoutName));

        var general = service.general("2026-1", null);

        assertThat(general.totalKeyResults()).isEqualTo(1);
    }

    @Test
    void projectsMustCarryAWellFormedStartPeriodWhenAFilterIsApplied() {
        when(projectRepository.findAll()).thenReturn(List.of(project(1L, department, " ", null)));
        assertThatThrownBy(() -> service.general("2026-1", null)).isInstanceOf(BusinessException.class);

        when(projectRepository.findAll()).thenReturn(List.of(project(1L, department, null, null)));
        assertThatThrownBy(() -> service.general("2026-1", null)).isInstanceOf(BusinessException.class);

        when(projectRepository.findAll()).thenReturn(List.of(project(1L, department, "inicio", null)));
        assertThatThrownBy(() -> service.general("2026-1", null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void keyResultPeriodNamesMustBeWellFormedWhenAFilterIsApplied() {
        keyResult.getAcademicPeriod().setName("periodo");
        when(projectRepository.findAll()).thenReturn(List.of());
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));

        assertThatThrownBy(() -> service.general("2026-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY");
    }

    private static Project project(Long id, Department department, String startPeriod, String endPeriod) {
        Project project = new Project();
        project.setId(id);
        project.setName("Proyecto " + id);
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setDepartment(department);
        project.setStatus(ProjectStatus.ACTIVO);
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
