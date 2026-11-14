package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.common.PlannerLinks;
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
import co.edu.icesi.pdg.mte.strategy.KeyResultArchivePolicy;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectProgressEntryRepository progressRepository;
    private final TrayectoriaProjectClient trayectoriaProjectClient;
    private final IntegrationProperties integrationProperties;
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

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectProgressEntryRepository progressRepository,
            TrayectoriaProjectClient trayectoriaProjectClient,
            IntegrationProperties integrationProperties,
            KeyResultRepository keyResultRepository,
            ProjectKeyResultLinkRepository linkRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProfessorRepository professorRepository,
            RoleRepository roleRepository,
            AuditService auditService,
            AccessControlService accessControlService,
            ProjectPeriodService periodService,
            ProjectResponseAssembler responseAssembler,
            ProjectFieldMapper fieldMapper
    ) {
        this.projectRepository = projectRepository;
        this.progressRepository = progressRepository;
        this.trayectoriaProjectClient = trayectoriaProjectClient;
        this.integrationProperties = integrationProperties;
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
    }

    public ProjectDtos.ProjectResponse create(ProjectDtos.ProjectRequest request) {
        String plannerUrl = PlannerLinks.normalize(request.plannerUrl());
        Project project = new Project();
        fieldMapper.applyLocalFields(project, request.name(), request.description(), request.type(), request.departmentId(),
                request.startPeriod(), request.endPeriod(), request.startDate(), request.endDate(), null, request.tutors());
        applyDirectKeyResult(project, request.keyResultId(), request.linkStatus(), request.jiraKey(), request.keyResultLinks());
        project.setPlannerUrl(plannerUrl);
        boolean choosesStatus = request.status() != null && accessControlService.canChooseInitialProjectStatus(currentRoles());
        project.setStatus(choosesStatus ? request.status() : ProjectStatus.BORRADOR);
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
        ProjectPeriodService.PeriodRange requestedPeriod = period == null || period.isBlank() ? null : periodService.parse(period.trim());
        Specification<Project> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase(Locale.ROOT).trim() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), like),
                        builder.like(builder.lower(root.get("description")), like)
                ));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (departmentId != null) {
                predicates.add(builder.equal(root.get("department").get("id"), departmentId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return responseAssembler.toProjectResponses(projectRepository.findAll(specification)
                .stream()
                .filter(project -> requestedPeriod == null || periodService.overlaps(project, requestedPeriod))
                .toList());
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
        List<ProjectDtos.ProjectKeyResultLinkResponse> linkedKeyResults = links.stream()
                .map(responseAssembler::toLinkResponse)
                .toList();
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
        String plannerUrl = PlannerLinks.normalize(request.plannerUrl());
        Project project = findProject(id);
        ProjectDtos.ProjectResponse before = Mapper.toResponse(project);
        fieldMapper.applyLocalFields(project, request.name(), request.description(), request.type(), request.departmentId(),
                request.startPeriod(), request.endPeriod(), request.startDate(), request.endDate(), request.actualEndDate(), request.tutors());
        if (request.keyResultId() != null) {
            project.setKeyResult(resolveKeyResultForUpdate(project, request.keyResultId()));
        }
        project.setLinkStatus(request.linkStatus());
        project.setJiraKey(request.jiraKey());
        project.setPlannerUrl(plannerUrl);
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
        ProjectDtos.ProjectResponse response = responseAssembler.toProjectResponse(saved);
        auditService.record(AuditAction.STATUS_CHANGE, "PROJECT", response.id(), "Estado de proyecto actualizado a " + response.status(), before, response);
        return response;
    }

    public ProjectDtos.ProjectProgressResponse registerProgress(Long id, ProjectDtos.ProjectProgressRequest request) {
        Project project = findProject(id);
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
        return response;
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
                    keyResultRepository.findAll().stream().filter(keyResult -> !keyResult.isArchived()).findFirst().ifPresent(project::setKeyResult);
                }
                if (project.getKeyResult() == null) {
                    failed++;
                    warnings.add("Proyecto externo " + payload.externalProjectId() + " no fue importado porque el MER exige key_result_id.");
                    continue;
                }
                projectRepository.save(project);
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
            KeyResultArchivePolicy.requireNotArchived(keyResult);
            if (linkRepository.existsByProjectIdAndKeyResultIdAndActiveTrue(project.getId(), keyResult.getId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "El proyecto ya esta vinculado a este Key Result.");
            }
            ProjectKeyResultLink link = new ProjectKeyResultLink();
            link.setProject(project);
            link.setKeyResult(keyResult);
            linkRepository.save(link);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectTeacherResponse> listTeachers(Long projectId) {
        findProject(projectId);
        return projectTeacherRepository.findAll().stream()
                .filter(projectTeacher -> projectTeacher.getProject().getId().equals(projectId))
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
        ProjectDtos.ProjectTeacherResponse before = Mapper.toResponse(projectTeacher);
        projectTeacherRepository.delete(projectTeacher);
        auditService.record(AuditAction.DELETE, "PROJECT_TEACHER", projectId + ":" + teacherId + ":" + roleId,
                "Profesor retirado de proyecto.", before, null);
    }

    private void applyDirectKeyResult(
            Project project,
            Long keyResultId,
            String linkStatus,
            String jiraKey,
            List<ProjectDtos.ProjectKeyResultDraftRequest> draftLinks
    ) {
        ProjectDtos.ProjectKeyResultDraftRequest primaryLink = draftLinks == null || draftLinks.isEmpty() ? null : draftLinks.get(0);
        Long resolvedKeyResultId = keyResultId != null ? keyResultId : primaryLink == null ? null : primaryLink.keyResultId();
        // The main KR is always the user's explicit choice; it is never picked on their behalf.
        if (resolvedKeyResultId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Selecciona al menos un Key Result para la iniciativa.");
        }
        project.setKeyResult(findLinkableKeyResult(resolvedKeyResultId));
        project.setLinkStatus(linkStatus == null || linkStatus.isBlank() ? "ACTIVO" : linkStatus.trim());
        project.setJiraKey(jiraKey);
    }

    private Project findProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));
    }

    private KeyResult findKeyResult(Long id) {
        return keyResultRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Key Result no encontrado."));
    }

    private KeyResult findLinkableKeyResult(Long id) {
        KeyResult keyResult = findKeyResult(id);
        KeyResultArchivePolicy.requireNotArchived(keyResult);
        return keyResult;
    }

    /** Keeping the project's current Key Result is not a new link, so only a change of target is checked. */
    private KeyResult resolveKeyResultForUpdate(Project project, Long keyResultId) {
        KeyResult current = project.getKeyResult();
        if (current != null && current.getId().equals(keyResultId)) {
            return current;
        }
        return findLinkableKeyResult(keyResultId);
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
