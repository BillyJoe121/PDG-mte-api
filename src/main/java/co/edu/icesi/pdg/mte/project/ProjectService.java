package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.ProjectDtos;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;
import co.edu.icesi.pdg.mte.integration.IntegrationProperties;
import co.edu.icesi.pdg.mte.integration.TrayectoriaProjectClient;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import co.edu.icesi.pdg.mte.strategy.KeyResultProgressService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectProgressEntryRepository progressRepository;
    private final DepartmentRepository departmentRepository;
    private final TrayectoriaProjectClient trayectoriaProjectClient;
    private final IntegrationProperties integrationProperties;
    private final KeyResultProgressService keyResultProgressService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectProgressEntryRepository progressRepository,
            DepartmentRepository departmentRepository,
            TrayectoriaProjectClient trayectoriaProjectClient,
            IntegrationProperties integrationProperties,
            KeyResultProgressService keyResultProgressService
    ) {
        this.projectRepository = projectRepository;
        this.progressRepository = progressRepository;
        this.departmentRepository = departmentRepository;
        this.trayectoriaProjectClient = trayectoriaProjectClient;
        this.integrationProperties = integrationProperties;
        this.keyResultProgressService = keyResultProgressService;
    }

    public ProjectDtos.ProjectResponse create(ProjectDtos.ProjectRequest request) {
        Project project = new Project();
        applyLocalFields(project, request.name(), request.description(), request.type(), request.departmentId(),
                request.startPeriod(), request.endPeriod(), request.startDate(), request.endDate(), null, request.tutors());
        project.setStatus(request.status() == null ? ProjectStatus.BORRADOR : request.status());
        project.setOrigin(ProjectOrigin.LOCAL);
        project.setSyncStatus(ProjectSyncStatus.LOCAL_ONLY);
        return Mapper.toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.ProjectResponse> list(String search, ProjectStatus status, ProjectType type, Long departmentId, String period) {
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
            if (period != null && !period.isBlank()) {
                predicates.add(builder.or(
                        builder.equal(root.get("startPeriod"), period),
                        builder.equal(root.get("endPeriod"), period)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return projectRepository.findAll(specification)
                .stream()
                .map(Mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectResponse get(Long id) {
        return Mapper.toResponse(findProject(id));
    }

    public ProjectDtos.ProjectResponse update(Long id, ProjectDtos.ProjectUpdateRequest request) {
        Project project = findProject(id);
        applyLocalFields(project, request.name(), request.description(), request.type(), request.departmentId(),
                request.startPeriod(), request.endPeriod(), request.startDate(), request.endDate(), request.actualEndDate(), request.tutors());
        return Mapper.toResponse(projectRepository.save(project));
    }

    public ProjectDtos.ProjectResponse updateStatus(Long id, ProjectDtos.ProjectStatusRequest request) {
        Project project = findProject(id);
        project.setStatus(request.status());
        if (request.status() == ProjectStatus.FINALIZADO && project.getActualEndDate() == null) {
            project.setActualEndDate(java.time.LocalDate.now());
        }
        Project saved = projectRepository.save(project);
        keyResultProgressService.recalculateKeyResultsForProject(saved.getId());
        return Mapper.toResponse(saved);
    }

    public ProjectDtos.ProjectProgressResponse registerProgress(Long id, ProjectDtos.ProjectProgressRequest request) {
        Project project = findProject(id);
        ProjectProgressEntry entry = new ProjectProgressEntry();
        entry.setProject(project);
        entry.setProgressPercent(request.progressPercent());
        entry.setComment(request.comment().trim());
        entry.setMilestones(request.milestones());
        entry.setCreatedByExternalUserId(currentExternalUserId());

        project.setGlobalProgress(request.progressPercent());
        projectRepository.save(project);
        return Mapper.toResponse(progressRepository.save(entry));
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
                applyExternalPayload(project, payload);
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
        return new ProjectDtos.ProjectSyncResponse(imported, updated, failed, warnings);
    }

    private void applyLocalFields(
            Project project,
            String name,
            String description,
            ProjectType type,
            Long departmentId,
            String startPeriod,
            String endPeriod,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            java.time.LocalDate actualEndDate,
            List<String> tutors
    ) {
        validateDateRange(startDate, endDate);
        project.setName(name.trim());
        project.setDescription(description.trim());
        project.setType(type);
        project.setDepartment(findDepartment(departmentId));
        project.setDepartmentName(project.getDepartment().getName());
        project.setStartPeriod(startPeriod.trim());
        project.setEndPeriod(endPeriod == null ? null : endPeriod.trim());
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        project.setActualEndDate(actualEndDate);
        project.setTutors(cleanTutors(tutors));
    }

    private void applyExternalPayload(Project project, ExternalProjectPayload payload) {
        project.setExternalProjectId(payload.externalProjectId());
        project.setExternalSource(integrationProperties.getSourceName());
        project.setName(defaultText(payload.name(), "Proyecto externo " + payload.externalProjectId()));
        project.setDescription(defaultText(payload.description(), "Proyecto sincronizado desde Trayectoria Docente."));
        project.setType(mapType(payload.type()));
        Department department = findDepartmentByName(payload.departmentName());
        project.setDepartment(department);
        project.setDepartmentName(department == null ? payload.departmentName() : department.getName());
        project.setStatus(mapStatus(payload.status()));
        project.setStartPeriod(defaultText(payload.startPeriod(), "2026-1"));
        project.setEndPeriod(payload.endPeriod());
        project.setStartDate(payload.startDate());
        project.setEndDate(payload.endDate());
        project.setTutors(cleanTutors(payload.tutors()));
        project.setOrigin(ProjectOrigin.SYNCED);
        project.setSyncStatus(ProjectSyncStatus.SYNCED);
        project.setLastSyncedAt(Instant.now());
        project.setRawExternalPayload(payload.rawPayload());
    }

    private ProjectType mapType(String rawType) {
        if (rawType == null) {
            return ProjectType.INVESTIGACION;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("GRADO")) {
            return ProjectType.GRADO;
        }
        if (normalized.contains("EXTENSION") || normalized.contains("EXTENS")) {
            return ProjectType.EXTENSION;
        }
        if (normalized.contains("MACRO")) {
            return ProjectType.MACROPROYECTO;
        }
        return ProjectType.INVESTIGACION;
    }

    private ProjectStatus mapStatus(String rawStatus) {
        if (rawStatus == null) {
            return ProjectStatus.BORRADOR;
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("CURSO") || normalized.contains("ACTIVO")) {
            return ProjectStatus.ACTIVO;
        }
        if (normalized.contains("FINAL")) {
            return ProjectStatus.FINALIZADO;
        }
        if (normalized.contains("SUSP")) {
            return ProjectStatus.SUSPENDIDO;
        }
        if (normalized.contains("ARCH")) {
            return ProjectStatus.ARCHIVADO;
        }
        return ProjectStatus.BORRADOR;
    }

    private List<String> cleanTutors(List<String> tutors) {
        if (tutors == null) {
            return List.of();
        }
        return tutors.stream()
                .filter(tutor -> tutor != null && !tutor.isBlank())
                .map(String::trim)
                .toList();
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Departamento no encontrado."));
    }

    private Department findDepartmentByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return departmentRepository.findByNameIgnoreCase(name).orElse(null);
    }

    private Project findProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Proyecto no encontrado."));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void validateDateRange(java.time.LocalDate startDate, java.time.LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la fecha de inicio.");
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

    public String bearerValue(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader;
    }
}
