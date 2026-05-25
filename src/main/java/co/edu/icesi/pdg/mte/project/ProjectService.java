package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.api.dto.PageDtos;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.CatalogCacheService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.common.Pagination;
import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;
import co.edu.icesi.pdg.mte.integration.IntegrationProperties;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.integration.TrayectoriaProjectClient;
import co.edu.icesi.pdg.mte.people.Professor;
import co.edu.icesi.pdg.mte.people.ProfessorRepository;
import co.edu.icesi.pdg.mte.people.Role;
import co.edu.icesi.pdg.mte.people.RoleRepository;
import co.edu.icesi.pdg.mte.security.AccessControlService;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultProgressService;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.Objective;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectProgressEntryRepository progressRepository;
    private final TrayectoriaProjectClient trayectoriaProjectClient;
    private final IntegrationProperties integrationProperties;
    private final KeyResultProgressService keyResultProgressService;
    private final KeyResultRepository keyResultRepository;
    private final ProjectKeyResultLinkRepository linkRepository;
    private final ProjectTeacherRepository projectTeacherRepository;
    private final ProfessorRepository professorRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final AccessControlService accessControlService;
    private final ProjectPeriodService periodService;
    private final ProjectResponseAssembler responseAssembler;
    private final ProjectFieldMapper fieldMapper;
    private final CatalogCacheService catalogCacheService;
    private final ObjectiveRepository objectiveRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectProgressEntryRepository progressRepository,
            TrayectoriaProjectClient trayectoriaProjectClient,
            IntegrationProperties integrationProperties,
            KeyResultProgressService keyResultProgressService,
            KeyResultRepository keyResultRepository,
            ProjectKeyResultLinkRepository linkRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProfessorRepository professorRepository,
            RoleRepository roleRepository,
            AuditService auditService,
            AccessControlService accessControlService,
            ProjectPeriodService periodService,
            ProjectResponseAssembler responseAssembler,
            ProjectFieldMapper fieldMapper,
            CatalogCacheService catalogCacheService,
            ObjectiveRepository objectiveRepository
    ) {
        this.projectRepository = projectRepository;
        this.progressRepository = progressRepository;
        this.trayectoriaProjectClient = trayectoriaProjectClient;
        this.integrationProperties = integrationProperties;
        this.keyResultProgressService = keyResultProgressService;
        this.keyResultRepository = keyResultRepository;
        this.linkRepository = linkRepository;
        this.projectTeacherRepository = projectTeacherRepository;
        this.professorRepository = professorRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.accessControlService = accessControlService;
        this.periodService = periodService;
        this.responseAssembler = responseAssembler;
        this.fieldMapper = fieldMapper;
        this.catalogCacheService = catalogCacheService;
        this.objectiveRepository = objectiveRepository;
    }

    public ProjectDtos.ProjectResponse create(ProjectDtos.ProjectRequest request) {
        Project project = new Project();
        fieldMapper.applyLocalFields(project, request.name(), request.description(), request.type(), request.departmentId(),
                request.startPeriod(), request.endPeriod(), request.startDate(), request.endDate(), null, request.tutors());
        applyDirectKeyResult(project, request.keyResultId(), request.contributionWeight(), request.linkStatus(), request.jiraKey(), request.keyResultLinks());
        project.setStatus(request.status() == null ? ProjectStatus.BORRADOR : request.status());
        project.setOrigin(ProjectOrigin.LOCAL);
        project.setSyncStatus(ProjectSyncStatus.LOCAL_ONLY);
        Project saved = projectRepository.save(project);
        createImmediateLinks(saved, request.keyResultLinks());
        ProjectDtos.ProjectResponse response = responseAssembler.toProjectResponse(saved);
        auditService.record(AuditAction.CREATE, "PROJECT", response.id(), "Proyecto creado: " + response.name(), null, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectResponse> list(String search, ProjectStatus status, ProjectType type, Long departmentId, String period) {
        return responseAssembler.toProjectResponses(projectsPage(search, status, type, departmentId, period, Pageable.unpaged()).getContent());
    }

    @Transactional(readOnly = true)
    public PageDtos.PageResponse<ProjectDtos.ProjectResponse> listPage(
            String search,
            ProjectStatus status,
            ProjectType type,
            Long departmentId,
            String period,
            Integer page,
            Integer size
    ) {
        PageRequest pageRequest = Pagination.pageRequest(page, size, 25, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Project> projects = projectsPage(search, status, type, departmentId, period, pageRequest);
        return new PageDtos.PageResponse<>(
                responseAssembler.toProjectResponses(projects.getContent()),
                projects.getNumber(),
                projects.getSize(),
                projects.getTotalElements(),
                projects.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectScreenDataResponse screenData(
            String search,
            ProjectStatus status,
            ProjectType type,
            Long departmentId,
            String period
    ) {
        String normalizedPeriod = period == null || period.isBlank() ? null : period.trim();
        return new ProjectDtos.ProjectScreenDataResponse(
                list(search, status, type, departmentId, normalizedPeriod),
                listDepartmentCatalog(),
                listPeriodCatalog(),
                objectiveCards(departmentId, normalizedPeriod)
        );
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectResponse get(Long id) {
        return responseAssembler.toProjectResponse(findProject(id));
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectDetailResponse detail(Long id) {
        Project project = findProject(id);
        List<ProjectDtos.ProjectProgressResponse> history = progressRepository.findByProjectIdOrderByCreatedAtDesc(id)
                .stream()
                .map(Mapper::toResponse)
                .toList();
        List<ProjectKeyResultLink> links = linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(id);
        List<ProjectDtos.ProjectKeyResultLinkResponse> linkedKeyResults = responseAssembler.toLinkResponses(links);
        ProjectDtos.ImpactChainResponse contributionChain = responseAssembler.contributionChain(project, links);
        return new ProjectDtos.ProjectDetailResponse(
                responseAssembler.toProjectResponse(project),
                responseAssembler.kpis(project, history, links),
                history,
                linkedKeyResults,
                contributionChain
        );
    }

    public ProjectDtos.ProjectResponse update(Long id, ProjectDtos.ProjectUpdateRequest request) {
        Project project = findProject(id);
        ProjectDtos.ProjectResponse before = Mapper.toResponse(project);
        fieldMapper.applyLocalFields(project, request.name(), request.description(), request.type(), request.departmentId(),
                request.startPeriod(), request.endPeriod(), request.startDate(), request.endDate(), request.actualEndDate(), request.tutors());
        if (request.keyResultId() != null) {
            project.setKeyResult(findKeyResult(request.keyResultId()));
        }
        project.setContributionWeight(request.contributionWeight());
        project.setLinkStatus(request.linkStatus());
        project.setJiraKey(request.jiraKey());
        ProjectDtos.ProjectResponse response = responseAssembler.toProjectResponse(projectRepository.save(project));
        auditService.record(AuditAction.UPDATE, "PROJECT", response.id(), "Proyecto actualizado: " + response.name(), before, response);
        return response;
    }

    public ProjectDtos.ProjectResponse updateStatus(Long id, ProjectDtos.ProjectStatusRequest request) {
        Project project = findProject(id);
        if (!accessControlService.canChangeProjectStatus(project.getStatus(), request.status(), currentRoles())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "El rol actual no puede ejecutar la transicion de estado solicitada.");
        }
        ProjectDtos.ProjectResponse before = Mapper.toResponse(project);
        project.setStatus(request.status());
        if (request.status() == ProjectStatus.FINALIZADO && project.getActualEndDate() == null) {
            project.setActualEndDate(java.time.LocalDate.now());
        }
        Project saved = projectRepository.save(project);
        keyResultProgressService.recalculateKeyResultsForProject(saved.getId());
        ProjectDtos.ProjectResponse response = responseAssembler.toProjectResponse(saved);
        auditService.record(AuditAction.STATUS_CHANGE, "PROJECT", response.id(), "Estado de proyecto actualizado a " + response.status(), before, response);
        return response;
    }

    public ProjectDtos.ProjectProgressMutationResponse registerProgress(Long id, ProjectDtos.ProjectProgressRequest request) {
        Project project = findProject(id);
        ProjectDtos.ProjectMutationAffectedResponse affected = affectedByProject(project);
        ProjectDtos.ProjectResponse before = Mapper.toResponse(project);
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(request.progressPercent());
        entry.setComment(request.comment().trim());
        entry.setMilestones(request.milestones());
        entry.setCreatedByExternalUserId(currentExternalUserId());

        project.setGlobalProgress(request.progressPercent());
        projectRepository.save(project);
        ProjectDtos.ProjectProgressResponse response = Mapper.toResponse(progressRepository.save(entry));
        auditService.record(AuditAction.PROGRESS_REGISTERED, "PROJECT", id, "Avance de proyecto registrado: " + response.progressPercent() + "%", before, Mapper.toResponse(project));
        return new ProjectDtos.ProjectProgressMutationResponse(
                response.id(),
                response.projectId(),
                response.progressPercent(),
                response.comment(),
                response.milestones(),
                response.createdByExternalUserId(),
                response.createdAt(),
                affected
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectProgressResponse> history(Long projectId) {
        findProject(projectId);
        return progressRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(Mapper::toResponse)
                .toList();
    }

    public ProjectDtos.ProjectSyncResponse syncFromTrayectoria(String authorizationHeader) {
        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int updated = 0;
        int failed = 0;
        List<ExternalProjectPayload> externalProjects = trayectoriaProjectClient.fetchProjects(authorizationHeader);

        for (ExternalProjectPayload payload : externalProjects) {
            try {
                if (payload.externalProjectId() == null) {
                    failed++;
                    warnings.add("Proyecto externo sin id: " + payload.name());
                    continue;
                }
                boolean exists = projectRepository
                        .findByExternalSourceAndExternalProjectId(integrationProperties.getSourceName(), payload.externalProjectId())
                        .isPresent();
                Project project = projectRepository
                        .findByExternalSourceAndExternalProjectId(integrationProperties.getSourceName(), payload.externalProjectId())
                        .orElseGet(Project::new);
                fieldMapper.applyExternalPayload(project, payload);
                if (project.getKeyResult() == null) {
                    keyResultRepository.findAll().stream().findFirst().ifPresent(project::setKeyResult);
                }
                if (project.getKeyResult() == null) {
                    failed++;
                    warnings.add("Proyecto externo " + payload.externalProjectId() + " no fue importado porque el MER exige key_result_id.");
                    continue;
                }
                Project saved = projectRepository.save(project);
                if (saved.getId() != null) {
                    keyResultProgressService.recalculateKeyResultsForProject(saved.getId());
                }
                if (exists) {
                    updated++;
                } else {
                    imported++;
                }
            } catch (RuntimeException exception) {
                failed++;
                warnings.add("No se pudo sincronizar proyecto externo " + payload.externalProjectId() + ": " + exception.getMessage());
            }
        }
        ProjectDtos.ProjectSyncResponse response = new ProjectDtos.ProjectSyncResponse(imported, updated, failed, warnings);
        auditService.record(AuditAction.EXTERNAL_SYNC, "PROJECT_SYNC", integrationProperties.getSourceName(), "Sincronizacion de proyectos externos ejecutada.", null, response);
        return response;
    }

    private void createImmediateLinks(Project project, List<ProjectDtos.ProjectKeyResultDraftRequest> links) {
        if (links == null || links.isEmpty()) {
            return;
        }
        for (ProjectDtos.ProjectKeyResultDraftRequest request : links) {
            KeyResult keyResult = keyResultRepository.findById(request.keyResultId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Key Result no encontrado."));
            if (linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(project.getId(), keyResult.getId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "El proyecto ya esta vinculado a este Key Result.");
            }
            ProjectKeyResultLink link = new ProjectKeyResultLink();
            link.setProject(project);
            link.setKeyResult(keyResult);
            link.setContributionWeight(request.contributionWeight());
            link.setContributionType(request.contributionType());
            linkRepository.save(link);
            keyResultProgressService.recalculateKeyResult(keyResult.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectTeacherResponse> listTeachers(Long projectId) {
        findProject(projectId);
        return projectTeacherRepository.findByProject_Id(projectId).stream()
                .map(Mapper::toResponse)
                .toList();
    }

    public ProjectDtos.ProjectTeacherResponse assignTeacher(Long projectId, ProjectDtos.ProjectTeacherRequest request) {
        Project project = findProject(projectId);
        Professor professor = professorRepository.findById(request.teacherId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Profesor no encontrado."));
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Rol no encontrado."));
        ProjectTeacher projectTeacher = projectTeacherRepository
                .findById(new ProjectTeacherId(project.getId(), professor.getId(), role.getId()))
                .orElseGet(ProjectTeacher::new);
        projectTeacher.setProject(project);
        projectTeacher.setTeacher(professor);
        projectTeacher.setRole(role);
        projectTeacher.setJoinedAt(request.joinedAt());
        projectTeacher.setLeftAt(request.leftAt());
        ProjectDtos.ProjectTeacherResponse response = Mapper.toResponse(projectTeacherRepository.save(projectTeacher));
        auditService.record(AuditAction.UPDATE, "PROJECT_TEACHER", projectId + ":" + professor.getId() + ":" + role.getId(), "Profesor asignado a proyecto.", null, response);
        return response;
    }

    public void removeTeacher(Long projectId, Long teacherId, Long roleId) {
        ProjectTeacher projectTeacher = projectTeacherRepository.findById(new ProjectTeacherId(projectId, teacherId, roleId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Profesor de proyecto no encontrado."));
        projectTeacherRepository.delete(projectTeacher);
    }

    private void applyDirectKeyResult(
            Project project,
            Long keyResultId,
            BigDecimal contributionWeight,
            String linkStatus,
            String jiraKey,
            List<ProjectDtos.ProjectKeyResultDraftRequest> draftLinks
    ) {
        ProjectDtos.ProjectKeyResultDraftRequest primaryLink = draftLinks == null || draftLinks.isEmpty() ? null : draftLinks.get(0);
        Long resolvedKeyResultId = keyResultId != null ? keyResultId : primaryLink == null ? null : primaryLink.keyResultId();
        if (resolvedKeyResultId == null) {
            keyResultRepository.findAll().stream().findFirst().ifPresent(project::setKeyResult);
        } else {
            project.setKeyResult(findKeyResult(resolvedKeyResultId));
        }
        if (project.getKeyResult() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El MER exige asociar el proyecto a un Key Result.");
        }
        project.setContributionWeight(contributionWeight != null ? contributionWeight : primaryLink == null ? null : primaryLink.contributionWeight());
        project.setLinkStatus(linkStatus == null || linkStatus.isBlank() ? "ACTIVO" : linkStatus.trim());
        project.setJiraKey(jiraKey);
    }

    private List<CatalogDtos.DepartmentResponse> listDepartmentCatalog() {
        return catalogCacheService.listDepartments();
    }

    private Page<Project> projectsPage(
            String search,
            ProjectStatus status,
            ProjectType type,
            Long departmentId,
            String period,
            Pageable pageable
    ) {
        ProjectPeriodService.PeriodRange requestedPeriod = period == null || period.isBlank() ? null : periodService.parse(period.trim());
        String normalizedSearch = search == null || search.isBlank()
                ? null
                : "%" + search.toLowerCase(Locale.ROOT).trim() + "%";
        return projectRepository.findPage(
                normalizedSearch,
                status,
                type,
                departmentId,
                requestedPeriod != null,
                requestedPeriod == null ? 0 : requestedPeriod.startIndex(),
                requestedPeriod == null ? 0 : requestedPeriod.endIndex(),
                pageable
        );
    }

    private List<CatalogDtos.AcademicPeriodResponse> listPeriodCatalog() {
        return catalogCacheService.listPeriods();
    }

    private List<StrategyDtos.ObjectiveCardResponse> objectiveCards(Long departmentId, String period) {
        ProjectPeriodService.PeriodRange requestedPeriod = period == null ? null : periodService.parse(period);
        List<Objective> objectives = period == null && departmentId == null
                ? objectiveRepository.findAll()
                : objectiveRepository.findReportCandidates(
                        requestedPeriod != null,
                        requestedPeriod == null ? 0 : requestedPeriod.startIndex(),
                        requestedPeriod == null ? 0 : requestedPeriod.endIndex(),
                        departmentId,
                        null
                );
        return objectives.stream()
                .filter(objective -> objectivePeriodMatches(objective, requestedPeriod))
                .sorted(Comparator.comparing(Objective::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toObjectiveCard)
                .toList();
    }

    private boolean objectivePeriodMatches(Objective objective, ProjectPeriodService.PeriodRange requestedPeriod) {
        if (requestedPeriod == null) {
            return true;
        }
        if (objective.getAcademicPeriod() == null || objective.getAcademicPeriod().getName() == null) {
            return false;
        }
        ProjectPeriodService.PeriodRange objectivePeriod = periodService.parse(objective.getAcademicPeriod().getName());
        return objectivePeriod.startIndex() <= requestedPeriod.endIndex()
                && requestedPeriod.startIndex() <= objectivePeriod.endIndex();
    }

    private StrategyDtos.ObjectiveCardResponse toObjectiveCard(Objective objective) {
        BigDecimal completion = objective.completionPercentage();
        return new StrategyDtos.ObjectiveCardResponse(
                objective.getId(),
                objective.getName(),
                objective.getDescription(),
                objective.getStrategicBet().getId(),
                objective.getStrategicBet().getName(),
                objective.getGoal().getId(),
                objective.getGoal().getName(),
                objective.getDepartment().getId(),
                objective.getDepartment().getName(),
                objective.getAcademicPeriod().getId(),
                objective.getAcademicPeriod().getName(),
                completion,
                completion.compareTo(BigDecimal.valueOf(30)) < 0,
                objective.getKeyResults().stream().map(Mapper::toResponse).toList()
        );
    }

    private Project findProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));
    }

    private KeyResult findKeyResult(Long id) {
        return keyResultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Key Result no encontrado."));
    }

    private ProjectDtos.ProjectMutationAffectedResponse affectedByProject(Project project) {
        Set<Long> keyResultIds = new LinkedHashSet<>();
        Set<Long> objectiveIds = new LinkedHashSet<>();
        Set<Long> goalIds = new LinkedHashSet<>();
        Set<Long> strategicBetIds = new LinkedHashSet<>();
        Set<String> periods = new LinkedHashSet<>();

        addProjectPeriod(project, periods);
        addAffectedKeyResult(project.getKeyResult(), keyResultIds, objectiveIds, goalIds, strategicBetIds, periods);
        linkRepository.findByProjectIdAndActiveTrueOrderByIdAsc(project.getId())
                .forEach(link -> addAffectedKeyResult(link.getKeyResult(), keyResultIds, objectiveIds, goalIds, strategicBetIds, periods));

        return new ProjectDtos.ProjectMutationAffectedResponse(
                project.getId(),
                List.copyOf(keyResultIds),
                List.copyOf(objectiveIds),
                List.copyOf(goalIds),
                List.copyOf(strategicBetIds),
                List.copyOf(periods)
        );
    }

    private void addAffectedKeyResult(
            KeyResult keyResult,
            Set<Long> keyResultIds,
            Set<Long> objectiveIds,
            Set<Long> goalIds,
            Set<Long> strategicBetIds,
            Set<String> periods
    ) {
        if (keyResult == null) {
            return;
        }
        if (keyResult.getId() != null) {
            keyResultIds.add(keyResult.getId());
        }
        if (keyResult.getAcademicPeriod() != null && keyResult.getAcademicPeriod().getName() != null) {
            periods.add(keyResult.getAcademicPeriod().getName());
        }
        var objective = keyResult.getObjective();
        if (objective == null) {
            return;
        }
        if (objective.getId() != null) {
            objectiveIds.add(objective.getId());
        }
        if (objective.getAcademicPeriod() != null && objective.getAcademicPeriod().getName() != null) {
            periods.add(objective.getAcademicPeriod().getName());
        }
        if (objective.getGoal() != null && objective.getGoal().getId() != null) {
            goalIds.add(objective.getGoal().getId());
        }
        if (objective.getStrategicBet() != null && objective.getStrategicBet().getId() != null) {
            strategicBetIds.add(objective.getStrategicBet().getId());
        }
    }

    private void addProjectPeriod(Project project, Set<String> periods) {
        if (project.getStartPeriod() != null && !project.getStartPeriod().isBlank()) {
            periods.add(project.getStartPeriod());
        }
        if (project.getEndPeriod() != null && !project.getEndPeriod().isBlank()) {
            periods.add(project.getEndPeriod());
        }
    }

    private Long currentExternalUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ExternalUserContext context) {
            return context.externalUserId();
        }
        return null;
    }

    private List<String> currentRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ExternalUserContext context) {
            return context.roles();
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList();
    }

    public String bearerValue(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader;
    }

}
