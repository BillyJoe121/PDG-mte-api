package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;
import co.edu.icesi.pdg.mte.integration.IntegrationProperties;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.integration.TrayectoriaProjectClient;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.people.RoleRepository;
import co.edu.icesi.pdg.mte.security.AccessControlService;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultProgressService;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.Objective;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

abstract class ProjectServiceTestSupport {
    @Mock
    protected ProjectRepository projectRepository;
    @Mock
    protected ProjectProgressEntryRepository progressRepository;
    @Mock
    protected DepartmentRepository departmentRepository;
    @Mock
    protected TrayectoriaProjectClient trayectoriaProjectClient;
    @Mock
    protected KeyResultProgressService keyResultProgressService;
    @Mock
    protected KeyResultRepository keyResultRepository;
    @Mock
    protected ProjectKeyResultLinkRepository linkRepository;
    @Mock
    protected ProjectTeacherRepository projectTeacherRepository;
    @Mock
    protected ProfessorRepository professorRepository;
    @Mock
    protected RoleRepository roleRepository;
    @Mock
    protected AuditService auditService;

    protected IntegrationProperties integrationProperties;
    protected ProjectService service;
    protected Department department;
    protected KeyResult keyResult;

    @BeforeEach
    void setUpProjectService() {
        integrationProperties = new IntegrationProperties();
        ProjectPeriodService periodService = new ProjectPeriodService();
        ProjectResponseAssembler responseAssembler = new ProjectResponseAssembler(linkRepository);
        ProjectFieldMapper fieldMapper = new ProjectFieldMapper(departmentRepository, integrationProperties, periodService);
        service = new ProjectService(
                projectRepository,
                progressRepository,
                trayectoriaProjectClient,
                integrationProperties,
                keyResultProgressService,
                keyResultRepository,
                linkRepository,
                projectTeacherRepository,
                professorRepository,
                roleRepository,
                auditService,
                new AccessControlService(),
                periodService,
                responseAssembler,
                fieldMapper
        );
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
        org.mockito.Mockito.lenient().when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        org.mockito.Mockito.lenient().when(keyResultRepository.findAll()).thenReturn(List.of(keyResult));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ExternalUserContext.mock(),
                null,
                List.of()
        ));
    }

    protected ProjectDtos.ProjectRequest validRequest() {
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
                1L,
                BigDecimal.valueOf(25),
                "ACTIVO",
                null,
                List.of(" Tutora Uno ", ""),
                List.of()
        );
    }

    protected ProjectDtos.ProjectRequest requestWithImmediateLink(Long keyResultId) {
        return new ProjectDtos.ProjectRequest(
                "Proyecto MSP",
                "Descripcion",
                ProjectType.INVESTIGACION,
                1L,
                ProjectStatus.ACTIVO,
                "2026-1",
                "2026-2",
                null,
                null,
                keyResultId,
                BigDecimal.valueOf(25),
                "ACTIVO",
                null,
                List.of(),
                List.of(new ProjectDtos.ProjectKeyResultDraftRequest(
                        keyResultId,
                        BigDecimal.valueOf(25),
                        ContributionType.DIRECTA
                ))
        );
    }

    protected void setRoles(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ExternalUserContext(
                        99L,
                        "test",
                        "test@icesi.edu.co",
                        List.of(roles),
                        List.of(),
                        99L,
                        "Usuario Test",
                        "Departamento de TIC"
                ),
                null,
                List.of()
        ));
    }

    protected Project localProject() {
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
        project.setKeyResult(keyResult);
        project.setContributionWeight(BigDecimal.valueOf(25));
        project.setLinkStatus("ACTIVO");
        project.setTutors(List.of("Tutora Uno"));
        return project;
    }

    protected ProjectKeyResultLink link(Project project, KeyResult keyResult, int weight) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setProject(project);
        link.setKeyResult(keyResult);
        link.setContributionWeight(BigDecimal.valueOf(weight));
        link.setContributionType(ContributionType.DIRECTA);
        return link;
    }

    protected ExternalProjectPayload externalPayload(
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
