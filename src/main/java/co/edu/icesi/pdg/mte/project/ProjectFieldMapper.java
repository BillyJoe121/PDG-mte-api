package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ExternalProjectPayload;
import co.edu.icesi.pdg.mte.integration.IntegrationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Component
class ProjectFieldMapper {
    private final DepartmentRepository departmentRepository;
    private final IntegrationProperties integrationProperties;
    private final ProjectPeriodService periodService;

    ProjectFieldMapper(
            DepartmentRepository departmentRepository,
            IntegrationProperties integrationProperties,
            ProjectPeriodService periodService
    ) {
        this.departmentRepository = departmentRepository;
        this.integrationProperties = integrationProperties;
        this.periodService = periodService;
    }

    void applyLocalFields(
            Project project,
            String name,
            String description,
            ProjectType type,
            Long departmentId,
            String startPeriod,
            String endPeriod,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualEndDate,
            List<String> tutors
    ) {
        validateDateRange(startDate, endDate);
        periodService.validateRange(startPeriod, endPeriod);
        project.setName(name.trim());
        project.setDescription(description.trim());
        project.setType(type);
        project.setDepartment(findDepartment(departmentId));
        project.setDepartmentName(project.getDepartment().getName());
        project.setStartPeriod(startPeriod.trim());
        project.setEndPeriod(endPeriod == null || endPeriod.isBlank() ? startPeriod.trim() : endPeriod.trim());
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        project.setActualEndDate(actualEndDate);
        project.setTutors(cleanTutors(tutors));
    }

    void applyExternalPayload(Project project, ExternalProjectPayload payload) {
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
        project.setEndPeriod(defaultText(payload.endPeriod(), project.getStartPeriod()));
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

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la fecha de inicio.");
        }
    }
}
