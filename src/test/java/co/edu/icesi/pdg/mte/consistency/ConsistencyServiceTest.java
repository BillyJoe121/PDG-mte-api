package co.edu.icesi.pdg.mte.consistency;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencyFindingType;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencyModule;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencySeverity;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnit;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntryRepository;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoal;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.Objective;
import co.edu.icesi.pdg.mte.strategy.StrategicBet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsistencyServiceTest {
    @Mock
    private KeyResultRepository keyResultRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectKeyResultLinkRepository linkRepository;
    @Mock
    private ProjectProgressEntryRepository progressRepository;

    private ConsistencyService service;
    private Department department;
    private KeyResult linkedKr;
    private KeyResult unlinkedKr;
    private Project linkedActiveProject;
    private Project projectWithoutKr;
    private Project staleProject;
    private Project recentProject;

    @BeforeEach
    void setUp() {
        service = new ConsistencyService(keyResultRepository, projectRepository, linkRepository, progressRepository);

        MeasurementUnit unit = TestFixtures.unit(1L);
        AcademicPeriod period = TestFixtures.period(1L);
        department = TestFixtures.department(1L);
        InstitutionalGoal goal = TestFixtures.goal(1L, unit);
        StrategicBet bet = TestFixtures.strategicBet(1L);
        Objective objective = TestFixtures.objective(1L, unit, period, department, goal, bet);
        linkedKr = objective.getKeyResults().get(0);
        linkedKr.setId(1L);
        linkedKr.setName("KR vinculado");
        unlinkedKr = TestFixtures.keyResult(2L, unit);
        unlinkedKr.setName("KR sin proyecto");
        unlinkedKr.setObjective(objective);

        linkedActiveProject = project(1L, "Proyecto vinculado", ProjectStatus.ACTIVO, linkedKr);
        projectWithoutKr = project(2L, "Proyecto sin KR", ProjectStatus.ACTIVO, null);
        staleProject = project(3L, "Proyecto sin avance reciente", ProjectStatus.ACTIVO, linkedKr);
        recentProject = project(4L, "Proyecto con avance reciente", ProjectStatus.ACTIVO, linkedKr);
    }

    @Test
    void reportsTheThreeConsistencyFindingTypesAndCounters() {
        when(keyResultRepository.findAll()).thenReturn(List.of(linkedKr, unlinkedKr));
        when(projectRepository.existsByKeyResultIdAndStatus(1L, ProjectStatus.ACTIVO)).thenReturn(true);
        when(projectRepository.findByStatus(ProjectStatus.ACTIVO))
                .thenReturn(List.of(linkedActiveProject, projectWithoutKr, staleProject, recentProject));
        when(linkRepository.existsByProjectIdAndActiveTrue(1L)).thenReturn(true);
        when(linkRepository.existsByProjectIdAndActiveTrue(2L)).thenReturn(false);
        when(linkRepository.existsByProjectIdAndActiveTrue(3L)).thenReturn(true);
        when(linkRepository.existsByProjectIdAndActiveTrue(4L)).thenReturn(true);
        when(progressRepository.findFirstByProjectIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(progressEntry(linkedActiveProject, Instant.now())));
        when(progressRepository.findFirstByProjectIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());
        when(progressRepository.findFirstByProjectIdOrderByCreatedAtDesc(3L))
                .thenReturn(Optional.of(progressEntry(staleProject, Instant.now().minusSeconds(20L * 24 * 60 * 60))));
        when(progressRepository.findFirstByProjectIdOrderByCreatedAtDesc(4L))
                .thenReturn(Optional.of(progressEntry(recentProject, Instant.now())));

        var response = service.check(null, null, 15);

        assertThat(response.summary().total()).isEqualTo(4);
        assertThat(response.summary().high()).isEqualTo(2);
        assertThat(response.summary().medium()).isEqualTo(2);
        assertThat(response.summary().low()).isZero();
        assertThat(response.findings()).extracting("type")
                .contains(
                        ConsistencyFindingType.KR_WITHOUT_ACTIVE_PROJECTS,
                        ConsistencyFindingType.ACTIVE_PROJECT_WITHOUT_KR,
                        ConsistencyFindingType.ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS
                );
        assertThat(response.findings()).extracting("entityCode")
                .contains("KR-2", "PRJ-2", "PRJ-3");
    }

    @Test
    void filtersBySeverityAndModuleAndExportsCsv() {
        when(keyResultRepository.findAll()).thenReturn(List.of(unlinkedKr));
        when(projectRepository.findByStatus(ProjectStatus.ACTIVO)).thenReturn(List.of(staleProject));
        when(linkRepository.existsByProjectIdAndActiveTrue(3L)).thenReturn(true);
        when(progressRepository.findFirstByProjectIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.empty());

        var onlyHigh = service.check("alta", null, null);
        var onlyProjects = service.check(null, "proyectos", null);
        String csv = service.csv("media", "proyectos", null);

        assertThat(onlyHigh.findings()).hasSize(1);
        assertThat(onlyHigh.findings().get(0).severity()).isEqualTo(ConsistencySeverity.ALTA);
        assertThat(onlyProjects.findings()).allMatch(finding -> finding.module() == ConsistencyModule.PROYECTOS);
        assertThat(csv).contains("id,type,severity,module", "ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS", "PRJ-3");
    }

    @Test
    void rejectsInvalidFilters() {
        assertThatThrownBy(() -> service.check("urgente", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("severity");
        assertThatThrownBy(() -> service.check(null, "finanzas", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("module");
        assertThatThrownBy(() -> service.check(null, null, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("staleDays");
    }

    private Project project(Long id, String name, ProjectStatus status, KeyResult keyResult) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setDepartment(department);
        project.setStatus(status);
        project.setStartPeriod("2026-1");
        project.setEndPeriod("2026-1");
        project.setKeyResult(keyResult);
        project.setGlobalProgress(BigDecimal.ZERO);
        return project;
    }

    private ProjectProgressEntry progressEntry(Project project, Instant createdAt) {
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(BigDecimal.TEN);
        entry.setComment("Avance");
        ReflectionTestUtils.setField(entry, "createdAt", createdAt);
        return entry;
    }

    private ProjectKeyResultLink link(KeyResult keyResult, Project project) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setProject(project);
        link.setContributionWeight(BigDecimal.valueOf(100));
        return link;
    }
}
