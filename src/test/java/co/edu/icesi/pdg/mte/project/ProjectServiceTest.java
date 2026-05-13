package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;
import co.edu.icesi.pdg.mte.integration.IntegrationProperties;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.integration.TrayectoriaProjectClient;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultProgressService;
import co.edu.icesi.pdg.mte.strategy.Objective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectProgressEntryRepository progressRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private TrayectoriaProjectClient trayectoriaProjectClient;
    @Mock
    private KeyResultProgressService keyResultProgressService;
    @Mock
    private ProjectKeyResultLinkRepository linkRepository;
    @Mock
    private AuditService auditService;

    private IntegrationProperties integrationProperties;
    private ProjectService service;
    private Department department;
    private KeyResult keyResult;

    @BeforeEach
    void setUp() {
        integrationProperties = new IntegrationProperties();
        service = new ProjectService(projectRepository, progressRepository, departmentRepository, trayectoriaProjectClient,
                integrationProperties, keyResultProgressService, linkRepository, auditService);
        department = TestFixtures.department(1L);
        Objective objective = TestFixtures.objective(
                1L,
                TestFixtures.unit(1L),
                TestFixtures.period(1L),
                department,
                TestFixtures.goal(1L, TestFixtures.unit(1L)),
                TestFixtures.strategicBet(1L)
        );
        keyResult = objective.getKeyResults().get(0);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ExternalUserContext.mock(),
                null,
                List.of()
        ));
    }

    @Test
    void createsLocalProjectWhenPayloadIsValid() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(validRequest());

        assertThat(response.name()).isEqualTo("Proyecto MSP");
        assertThat(response.origin()).isEqualTo(ProjectOrigin.LOCAL);
        assertThat(response.syncStatus()).isEqualTo(ProjectSyncStatus.LOCAL_ONLY);
        assertThat(response.tutors()).containsExactly("Tutora Uno");
    }

    @Test
    void managesStatusProgressHistoryAndDetailForExistingProject() {
        Project project = localProject();
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(BigDecimal.valueOf(45));
        entry.setComment("Avance registrado");

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(progressRepository.save(any(ProjectProgressEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(progressRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(entry));

        assertThat(service.get(1L).name()).isEqualTo("Proyecto MSP");
        assertThat(service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.FINALIZADO)).status())
                .isEqualTo(ProjectStatus.FINALIZADO);
        assertThat(service.registerProgress(1L, new ProjectDtos.ProjectProgressRequest(BigDecimal.valueOf(45), "Avance registrado", "Hito")).progressPercent())
                .isEqualByComparingTo("45");
        assertThat(service.history(1L)).hasSize(1);
        assertThat(project.getActualEndDate()).isNotNull();
    }

    @Test
    void syncsExternalProjectsAsInternalProjects() {
        ExternalProjectPayload imported = new ExternalProjectPayload(
                77L,
                "Proyecto externo",
                "Descripcion externa",
                "EN_CURSO",
                "extension",
                "Departamento de TIC",
                "2026-1",
                "2026-2",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1),
                List.of("Tutor Externo"),
                "{\"id_proyecto\":77}"
        );
        ExternalProjectPayload withoutId = new ExternalProjectPayload(
                null,
                "Proyecto sin id",
                "No debe importarse",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "{}"
        );

        when(trayectoriaProjectClient.fetchProjects("Bearer token")).thenReturn(List.of(imported, withoutId));
        when(projectRepository.findByExternalSourceAndExternalProjectId("TRAYECTORIA_DOCENTE", 77L)).thenReturn(Optional.empty());
        when(departmentRepository.findByNameIgnoreCase("Departamento de TIC")).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.syncFromTrayectoria("Bearer token");

        assertThat(response.imported()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.warnings()).hasSize(1);
        verify(projectRepository).save(argThat(project -> project.getOrigin() == ProjectOrigin.SYNCED
                && project.getType() == ProjectType.EXTENSION
                && project.getStatus() == ProjectStatus.ACTIVO));
    }

    @Test
    void syncMapsExternalTypeStatusDefaultsAndFailures() {
        List<ExternalProjectPayload> payloads = List.of(
                externalPayload(101L, "grado", null, null, null, null, null, "Proyecto grado"),
                externalPayload(102L, "extension social", "FINALIZADO", "", "", "", "", ""),
                externalPayload(103L, "macro", "SUSPENDIDO", "Departamento de TIC", "2026-1", "2026-2", "{}", "Proyecto macro"),
                externalPayload(104L, "otro", "ARCHIVADO", "Departamento de TIC", "2026-1", "2026-2", "{}", "Proyecto otro"),
                externalPayload(105L, null, "desconocido", "Departamento de TIC", "2026-1", "2026-2", "{}", "Proyecto default")
        );

        when(trayectoriaProjectClient.fetchProjects(null)).thenReturn(payloads);
        for (ExternalProjectPayload payload : payloads) {
            when(projectRepository.findByExternalSourceAndExternalProjectId("TRAYECTORIA_DOCENTE", payload.externalProjectId()))
                    .thenReturn(Optional.empty());
        }
        when(departmentRepository.findByNameIgnoreCase("Departamento de TIC")).thenReturn(Optional.of(department));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            if (project.getExternalProjectId().equals(104L)) {
                throw new IllegalStateException("fallo controlado");
            }
            return project;
        });

        var response = service.syncFromTrayectoria(null);

        assertThat(response.imported()).isEqualTo(4);
        assertThat(response.failed()).isEqualTo(1);
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(101L)
                && project.getType() == ProjectType.GRADO
                && project.getStatus() == ProjectStatus.BORRADOR
                && project.getDepartment() == null
                && project.getStartPeriod().equals("2026-1")));
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(102L)
                && project.getType() == ProjectType.EXTENSION
                && project.getStatus() == ProjectStatus.FINALIZADO
                && project.getName().equals("Proyecto externo 102")));
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(103L)
                && project.getType() == ProjectType.MACROPROYECTO
                && project.getStatus() == ProjectStatus.SUSPENDIDO));
        verify(projectRepository).save(argThat(project -> project.getExternalProjectId().equals(105L)
                && project.getType() == ProjectType.INVESTIGACION
                && project.getStatus() == ProjectStatus.BORRADOR));
    }

    @Test
    void updatesExistingExternalProjectDuringSync() {
        Project existing = localProject();
        existing.setExternalSource("TRAYECTORIA_DOCENTE");
        existing.setExternalProjectId(88L);
        ExternalProjectPayload payload = new ExternalProjectPayload(
                88L,
                null,
                null,
                "FINALIZADO",
                "macroproyecto",
                "Departamento no catalogado",
                null,
                null,
                null,
                null,
                null,
                "{}"
        );

        when(trayectoriaProjectClient.fetchProjects(null)).thenReturn(List.of(payload));
        when(projectRepository.findByExternalSourceAndExternalProjectId("TRAYECTORIA_DOCENTE", 88L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByNameIgnoreCase("Departamento no catalogado")).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.syncFromTrayectoria(null);

        assertThat(response.updated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Proyecto externo 88");
        assertThat(existing.getType()).isEqualTo(ProjectType.MACROPROYECTO);
        assertThat(existing.getStatus()).isEqualTo(ProjectStatus.FINALIZADO);
    }

    @Test
    void sadPathsRejectInvalidProjectOperations() {
        Project project = localProject();
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.create(new ProjectDtos.ProjectRequest(
                "Proyecto",
                "Descripcion",
                ProjectType.GRADO,
                99L,
                ProjectStatus.ACTIVO,
                "2026-1",
                null,
                null,
                null,
                List.of()
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("Departamento");
        assertThatThrownBy(() -> service.create(new ProjectDtos.ProjectRequest(
                "Proyecto",
                "Descripcion",
                ProjectType.GRADO,
                1L,
                ProjectStatus.ACTIVO,
                "2026-1",
                null,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 1, 1),
                List.of()
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("fecha de fin");
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateStatus(404L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.ACTIVO))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.registerProgress(404L, new ProjectDtos.ProjectProgressRequest(BigDecimal.TEN, "x", null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.history(404L)).isInstanceOf(BusinessException.class);

        SecurityContextHolder.clearContext();
        when(progressRepository.save(any(ProjectProgressEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.registerProgress(1L, new ProjectDtos.ProjectProgressRequest(BigDecimal.ONE, "Sin principal", null)).createdByExternalUserId())
                .isNull();
    }

    @Test
    void coversOptionalBranchesForCreateUpdateAndStatus() {
        Project project = localProject();
        project.setActualEndDate(LocalDate.of(2026, 12, 1));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new ProjectDtos.ProjectRequest(
                "Proyecto borrador",
                "Descripcion",
                ProjectType.GRADO,
                1L,
                null,
                "2026-1",
                null,
                null,
                null,
                null
        ));
        var updated = service.update(1L, new ProjectDtos.ProjectUpdateRequest(
                "Proyecto actualizado",
                "Descripcion",
                ProjectType.EXTENSION,
                1L,
                "2026-1",
                null,
                null,
                null,
                null,
                new ArrayList<>()
        ));
        var active = service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.ACTIVO));
        project.setActualEndDate(LocalDate.of(2026, 12, 1));
        var finalized = service.updateStatus(1L, new ProjectDtos.ProjectStatusRequest(ProjectStatus.FINALIZADO));

        assertThat(created.status()).isEqualTo(ProjectStatus.BORRADOR);
        assertThat(updated.endPeriod()).isNull();
        assertThat(active.status()).isEqualTo(ProjectStatus.ACTIVO);
        assertThat(finalized.actualEndDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void returnsCompleteProjectDetailWithHistoryLinksContributionChainAndKpis() {
        Project project = localProject();
        project.setStatus(ProjectStatus.FINALIZADO);
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(BigDecimal.valueOf(80));
        entry.setComment("Avance");
        ProjectKeyResultLink link = link(project, keyResult, 60);
        ProjectKeyResultLink overweightLink = link(project, keyResult, 50);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(progressRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(entry));
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link, overweightLink));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link, overweightLink));

        var detail = service.detail(1L);

        assertThat(detail.project().name()).isEqualTo("Proyecto MSP");
        assertThat(detail.history()).hasSize(1);
        assertThat(detail.linkedKeyResults()).hasSize(2);
        assertThat(detail.contributionChain().impacts()).hasSize(2);
        assertThat(detail.kpis().progressEntries()).isEqualTo(1);
        assertThat(detail.kpis().linkedKeyResults()).isEqualTo(2);
        assertThat(detail.kpis().declaredContributionWeight()).isEqualByComparingTo("110.00");
        assertThat(detail.kpis().appliedContribution()).isEqualByComparingTo("110.00");
        assertThat(detail.kpis().completed()).isTrue();
        assertThat(detail.kpis().overweightWarning()).isTrue();
    }

    @Test
    void returnsProjectDetailWithZeroAppliedContributionForActiveProjectAndNullExternalLinkProject() {
        Project project = localProject();
        ProjectKeyResultLink linkWithoutLocalProject = link(null, keyResult, 40);

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(progressRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(linkWithoutLocalProject));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(linkWithoutLocalProject));

        var detail = service.detail(1L);

        assertThat(detail.kpis().declaredContributionWeight()).isEqualByComparingTo("40.00");
        assertThat(detail.kpis().appliedContribution()).isEqualByComparingTo("0.00");
        assertThat(detail.kpis().completed()).isFalse();
        assertThat(detail.kpis().overweightWarning()).isFalse();
        assertThat(detail.linkedKeyResults().get(0).projectId()).isNull();
        assertThat(detail.linkedKeyResults().get(0).overweightWarning()).isFalse();
        assertThat(detail.contributionChain().impacts().get(0).period()).isNull();
        assertThat(detail.contributionChain().impacts().get(0).projectCompleted()).isFalse();
    }

    @Test
    void listsProjectsWithNoFiltersAndBlankSearch() {
        when(projectRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(localProject()));

        assertThat(service.list(null, null, null, null, null)).hasSize(1);
        assertThat(service.list("   ", null, null, null, "   ")).hasSize(1);
    }

    @Test
    void extractsBearerOnlyWhenHeaderIsValid() {
        assertThat(service.bearerValue("Bearer abc")).isEqualTo("Bearer abc");
        assertThat(service.bearerValue("Basic abc")).isNull();
        assertThat(service.bearerValue(null)).isNull();
    }

    private ProjectDtos.ProjectRequest validRequest() {
        return new ProjectDtos.ProjectRequest(
                "Proyecto MSP",
                "Descripcion",
                ProjectType.INVESTIGACION,
                1L,
                ProjectStatus.ACTIVO,
                "2026-1",
                "2026-2",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1),
                List.of(" Tutora Uno ", "")
        );
    }

    private Project localProject() {
        Project project = new Project();
        project.setId(1L);
        project.setName("Proyecto MSP");
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setDepartment(department);
        project.setDepartmentName(department.getName());
        project.setStatus(ProjectStatus.ACTIVO);
        project.setStartPeriod("2026-1");
        project.setEndPeriod("2026-2");
        project.setGlobalProgress(BigDecimal.ZERO);
        project.setTutors(List.of("Tutora Uno"));
        return project;
    }

    private ProjectKeyResultLink link(Project project, KeyResult keyResult, int weight) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setKeyResult(keyResult);
        link.setContributionWeight(BigDecimal.valueOf(weight));
        return link;
    }

    private ExternalProjectPayload externalPayload(
            Long id,
            String type,
            String status,
            String departmentName,
            String startPeriod,
            String endPeriod,
            String rawPayload,
            String name
    ) {
        return new ExternalProjectPayload(
                id,
                name,
                "",
                status,
                type,
                departmentName,
                startPeriod,
                endPeriod,
                null,
                null,
                null,
                rawPayload
        );
    }
}
