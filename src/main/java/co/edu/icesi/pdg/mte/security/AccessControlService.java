package co.edu.icesi.pdg.mte.security;

import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AccessControlService {
    public static final String ADMIN = "ADMIN";
    public static final String DECANO = "DECANO";
    public static final String DIRECTOR_ESCUELA = "DIRECTOR_ESCUELA";
    public static final String JEFE_DPTO = "JEFE_DPTO";
    public static final String PROFESOR = "PROFESOR";

    public static final List<String> SUPPORTED_ROLES = List.of(
            ADMIN,
            DECANO,
            DIRECTOR_ESCUELA,
            JEFE_DPTO,
            PROFESOR
    );

    public Map<String, Boolean> capabilities(Collection<String> roles) {
        Set<String> normalizedRoles = normalize(roles);
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("viewDashboard", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR));
        capabilities.put("viewStrategy", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR));
        capabilities.put("viewProjects", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR));
        capabilities.put("manageCatalogs", hasAny(normalizedRoles, ADMIN));
        capabilities.put("manageStrategicBets", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA));
        capabilities.put("manageGoals", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA));
        capabilities.put("manageObjectives", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO));
        capabilities.put("manageKeyResults", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO));
        capabilities.put("createProjects", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR));
        capabilities.put("updateProjects", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO));
        capabilities.put("registerProjectProgress", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO, PROFESOR));
        capabilities.put("changeProjectStatus", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA));
        capabilities.put("syncExternalProjects", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA));
        capabilities.put("linkProjectsToKeyResults", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO));
        capabilities.put("viewReports", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA, JEFE_DPTO));
        capabilities.put("viewPresentation", hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA));
        capabilities.put("viewAuditLogs", hasAny(normalizedRoles, ADMIN));
        return capabilities;
    }

    public List<String> normalizeToList(Collection<String> roles) {
        return normalize(roles).stream().toList();
    }

    public boolean canChangeProjectStatus(ProjectStatus currentStatus, ProjectStatus requestedStatus, Collection<String> roles) {
        Set<String> normalizedRoles = normalize(roles);
        if (currentStatus == null || requestedStatus == null) {
            return false;
        }
        if (currentStatus == requestedStatus) {
            return hasAny(normalizedRoles, ADMIN, DECANO, DIRECTOR_ESCUELA);
        }
        if (normalizedRoles.contains(ADMIN)) {
            return true;
        }
        if (!hasAny(normalizedRoles, DECANO, DIRECTOR_ESCUELA)) {
            return false;
        }
        return switch (currentStatus) {
            case BORRADOR -> requestedStatus == ProjectStatus.ACTIVO
                    || requestedStatus == ProjectStatus.SUSPENDIDO
                    || requestedStatus == ProjectStatus.ARCHIVADO;
            case ACTIVO -> requestedStatus == ProjectStatus.FINALIZADO
                    || requestedStatus == ProjectStatus.SUSPENDIDO
                    || requestedStatus == ProjectStatus.ARCHIVADO;
            case SUSPENDIDO -> requestedStatus == ProjectStatus.ACTIVO
                    || requestedStatus == ProjectStatus.ARCHIVADO;
            case FINALIZADO -> requestedStatus == ProjectStatus.ARCHIVADO;
            case ARCHIVADO -> false;
        };
    }

    private Set<String> normalize(Collection<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean hasAny(Set<String> roles, String... allowed) {
        for (String role : allowed) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
