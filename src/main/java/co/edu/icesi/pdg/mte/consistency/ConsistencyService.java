package co.edu.icesi.pdg.mte.consistency;

import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencyEntityType;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencyFindingResponse;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencyFindingType;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencyModule;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencySeverity;
import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos.ConsistencySummaryResponse;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntryRepository;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.strategy.KeyResult;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ConsistencyService {
    private static final int DEFAULT_STALE_DAYS = 15;

    private final KeyResultRepository keyResultRepository;
    private final ProjectRepository projectRepository;
    private final ProjectKeyResultLinkRepository linkRepository;
    private final ProjectProgressEntryRepository progressRepository;

    public ConsistencyService(
            KeyResultRepository keyResultRepository,
            ProjectRepository projectRepository,
            ProjectKeyResultLinkRepository linkRepository,
            ProjectProgressEntryRepository progressRepository
    ) {
        this.keyResultRepository = keyResultRepository;
        this.projectRepository = projectRepository;
        this.linkRepository = linkRepository;
        this.progressRepository = progressRepository;
    }

    public ConsistencyDtos.ConsistencyCheckResponse check(String severity, String module, Integer staleDays) {
        ConsistencySeverity severityFilter = parseSeverity(severity);
        ConsistencyModule moduleFilter = parseModule(module);
        int normalizedStaleDays = normalizeStaleDays(staleDays);
        Instant detectedAt = Instant.now();
        Instant staleLimit = detectedAt.minus(Duration.ofDays(normalizedStaleDays));
        List<Project> activeProjects = projectRepository.findByStatus(ProjectStatus.ACTIVO);

        List<ConsistencyFindingResponse> findings = java.util.stream.Stream.of(
                        keyResultsWithoutActiveProjects(detectedAt).stream(),
                        activeProjectsWithoutKr(activeProjects, detectedAt).stream(),
                        activeProjectsWithoutRecentProgress(activeProjects, detectedAt, staleLimit, normalizedStaleDays).stream()
                )
                .flatMap(stream -> stream)
                .filter(finding -> severityFilter == null || finding.severity() == severityFilter)
                .filter(finding -> moduleFilter == null || finding.module() == moduleFilter)
                .sorted(Comparator
                        .comparing(ConsistencyFindingResponse::severity, Comparator.comparingInt(this::severityRank))
                        .thenComparing(ConsistencyFindingResponse::module)
                        .thenComparing(ConsistencyFindingResponse::entityCode))
                .toList();

        return new ConsistencyDtos.ConsistencyCheckResponse(summary(findings), findings);
    }

    public String csv(String severity, String module, Integer staleDays) {
        List<ConsistencyFindingResponse> findings = check(severity, module, staleDays).findings();
        StringBuilder csv = new StringBuilder();
        csv.append("id,type,severity,module,entityType,entityCode,entityName,description,recommendedAction,actionLabel,actionUrl,detectedAt\n");
        for (ConsistencyFindingResponse finding : findings) {
            csv.append(escapeCsv(finding.id())).append(',')
                    .append(finding.type()).append(',')
                    .append(finding.severity()).append(',')
                    .append(finding.module()).append(',')
                    .append(finding.entityType()).append(',')
                    .append(escapeCsv(finding.entityCode())).append(',')
                    .append(escapeCsv(finding.entityName())).append(',')
                    .append(escapeCsv(finding.description())).append(',')
                    .append(escapeCsv(finding.recommendedAction())).append(',')
                    .append(escapeCsv(finding.actionLabel())).append(',')
                    .append(escapeCsv(finding.actionUrl())).append(',')
                    .append(finding.detectedAt())
                    .append('\n');
        }
        return csv.toString();
    }

    private List<ConsistencyFindingResponse> keyResultsWithoutActiveProjects(Instant detectedAt) {
        List<KeyResult> keyResults = keyResultRepository.findAll();
        Set<Long> keyResultIds = keyResults.stream()
                .map(KeyResult::getId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Long> keyResultIdsWithActiveProjects = new LinkedHashSet<>();
        if (!keyResultIds.isEmpty()) {
            keyResultIdsWithActiveProjects.addAll(projectRepository.findDistinctKeyResultIdsByStatus(ProjectStatus.ACTIVO));
            keyResultIdsWithActiveProjects.addAll(linkRepository.findActiveKeyResultIdsByProjectStatus(ProjectStatus.ACTIVO));
        }
        return keyResults
                .stream()
                .filter(keyResult -> keyResult.getId() == null || !keyResultIdsWithActiveProjects.contains(keyResult.getId()))
                .map(keyResult -> finding(
                        ConsistencyFindingType.KR_WITHOUT_ACTIVE_PROJECTS,
                        ConsistencySeverity.ALTA,
                        ConsistencyModule.INDICADORES,
                        ConsistencyEntityType.KEY_RESULT,
                        keyResult.getId(),
                        "KR-" + keyResult.getId(),
                        keyResult.getName(),
                        "El KR no tiene proyectos activos vinculados.",
                        "Vincular al menos un proyecto activo al KR antes de generar reportes o presentaciones.",
                        "Abrir KR",
                        keyResultActionUrl(keyResult),
                        detectedAt
                ))
                .toList();
    }

    private List<ConsistencyFindingResponse> activeProjectsWithoutKr(List<Project> activeProjects, Instant detectedAt) {
        Set<Long> activeProjectIdsWithLinks = activeProjectIdsWithLinks(activeProjects);
        return activeProjects
                .stream()
                .filter(project -> project.getKeyResult() == null)
                .filter(project -> project.getId() == null || !activeProjectIdsWithLinks.contains(project.getId()))
                .map(project -> finding(
                        ConsistencyFindingType.ACTIVE_PROJECT_WITHOUT_KR,
                        ConsistencySeverity.ALTA,
                        ConsistencyModule.PROYECTOS,
                        ConsistencyEntityType.PROJECT,
                        project.getId(),
                        projectCode(project),
                        project.getName(),
                        "El proyecto activo no tiene ningun KR asociado.",
                        "Asociar el proyecto a un KR o crear un vinculo proyecto-KR.",
                        "Abrir proyecto",
                        projectActionUrl(project),
                        detectedAt
                ))
                .toList();
    }

    private List<ConsistencyFindingResponse> activeProjectsWithoutRecentProgress(
            List<Project> activeProjects,
            Instant detectedAt,
            Instant staleLimit,
            int staleDays
    ) {
        Map<Long, ProjectProgressEntry> latestProgressByProjectId = latestProgressByProjectId(activeProjects);
        return activeProjects
                .stream()
                .map(project -> staleProgressFinding(project, latestProgressByProjectId.get(project.getId()), detectedAt, staleLimit, staleDays))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ConsistencyFindingResponse> staleProgressFinding(
            Project project,
            ProjectProgressEntry latestEntry,
            Instant detectedAt,
            Instant staleLimit,
            int staleDays
    ) {
        Optional<ProjectProgressEntry> latest = Optional.ofNullable(latestEntry);
        if (latest.isPresent() && !latest.get().getCreatedAt().isBefore(staleLimit)) {
            return Optional.empty();
        }

        String description = latest
                .map(entry -> "El proyecto activo no registra avances en los ultimos " + staleDays + " dias.")
                .orElse("El proyecto activo no tiene avances registrados.");
        String action = latest
                .map(entry -> "Registrar una nueva entrada de avance para actualizar la trazabilidad del proyecto.")
                .orElse("Registrar la primera entrada de avance del proyecto.");

        return Optional.of(finding(
                ConsistencyFindingType.ACTIVE_PROJECT_WITHOUT_RECENT_PROGRESS,
                ConsistencySeverity.MEDIA,
                ConsistencyModule.PROYECTOS,
                ConsistencyEntityType.PROJECT,
                project.getId(),
                projectCode(project),
                project.getName(),
                description,
                action,
                "Abrir proyecto",
                projectActionUrl(project),
                detectedAt
        ));
    }

    private Set<Long> activeProjectIdsWithLinks(List<Project> activeProjects) {
        List<Long> projectIds = activeProjects.stream()
                .map(Project::getId)
                .filter(id -> id != null)
                .toList();
        if (projectIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(linkRepository.findActiveProjectIdsByProjectIds(projectIds));
    }

    private Map<Long, ProjectProgressEntry> latestProgressByProjectId(List<Project> activeProjects) {
        List<Long> projectIds = activeProjects.stream()
                .map(Project::getId)
                .filter(id -> id != null)
                .toList();
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProjectProgressEntry> latestByProjectId = new LinkedHashMap<>();
        progressRepository.findByProjectIdsOrderByProjectIdAscCreatedAtDesc(projectIds)
                .forEach(entry -> latestByProjectId.putIfAbsent(entry.getProject().getId(), entry));
        return latestByProjectId;
    }

    private ConsistencyFindingResponse finding(
            ConsistencyFindingType type,
            ConsistencySeverity severity,
            ConsistencyModule module,
            ConsistencyEntityType entityType,
            Long entityId,
            String entityCode,
            String entityName,
            String description,
            String recommendedAction,
            String actionLabel,
            String actionUrl,
            Instant detectedAt
    ) {
        return new ConsistencyFindingResponse(
                type + "-" + entityCode,
                type,
                severity,
                module,
                entityType,
                entityId,
                entityCode,
                entityName,
                description,
                recommendedAction,
                actionLabel,
                actionUrl,
                detectedAt
        );
    }

    private ConsistencySummaryResponse summary(List<ConsistencyFindingResponse> findings) {
        return new ConsistencySummaryResponse(
                findings.size(),
                findings.stream().filter(finding -> finding.severity() == ConsistencySeverity.ALTA).count(),
                findings.stream().filter(finding -> finding.severity() == ConsistencySeverity.MEDIA).count(),
                findings.stream().filter(finding -> finding.severity() == ConsistencySeverity.BAJA).count()
        );
    }

    private String keyResultActionUrl(KeyResult keyResult) {
        if (keyResult.getObjective() == null || keyResult.getObjective().getId() == null) {
            return "/key-results/" + keyResult.getId();
        }
        return "/objectives/" + keyResult.getObjective().getId() + "/key-results/" + keyResult.getId();
    }

    private String projectActionUrl(Project project) {
        return "/projects/" + project.getId();
    }

    private String projectCode(Project project) {
        return "PRJ-" + project.getId();
    }

    private int normalizeStaleDays(Integer staleDays) {
        if (staleDays == null) {
            return DEFAULT_STALE_DAYS;
        }
        if (staleDays < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "staleDays debe ser mayor o igual a 1.");
        }
        return staleDays;
    }

    private ConsistencySeverity parseSeverity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ConsistencySeverity.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "severity debe ser ALTA, MEDIA o BAJA.");
        }
    }

    private ConsistencyModule parseModule(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ConsistencyModule.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "module debe ser OKRS, INDICADORES o PROYECTOS.");
        }
    }

    private int severityRank(ConsistencySeverity severity) {
        return switch (severity) {
            case ALTA -> 0;
            case MEDIA -> 1;
            case BAJA -> 2;
        };
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
