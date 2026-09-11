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
    public static final String USER = "USER";

    public static final List<String> SUPPORTED_ROLES = List.of(ADMIN, USER);

    public Map<String, Boolean> capabilities(Collection<String> roles) {
        Set<String> normalizedRoles = normalize(roles);
        Map<String, Boolean> capabilities = new LinkedHashMap<>();
        capabilities.put("viewDashboard", hasAny(normalizedRoles, ADMIN, USER));
        capabilities.put("viewStrategy", hasAny(normalizedRoles, ADMIN, USER));
        capabilities.put("viewProjects", hasAny(normalizedRoles, ADMIN));
        capabilities.put("manageCatalogs", hasAny(normalizedRoles, ADMIN));
        capabilities.put("manageStrategicBets", hasAny(normalizedRoles, ADMIN));
        capabilities.put("manageGoals", hasAny(normalizedRoles, ADMIN));
        capabilities.put("manageObjectives", hasAny(normalizedRoles, ADMIN, USER));
        capabilities.put("manageKeyResults", hasAny(normalizedRoles, ADMIN, USER));
        capabilities.put("createProjects", hasAny(normalizedRoles, ADMIN));
        capabilities.put("updateProjects", hasAny(normalizedRoles, ADMIN));
        capabilities.put("registerProjectProgress", hasAny(normalizedRoles, ADMIN));
        capabilities.put("changeProjectStatus", hasAny(normalizedRoles, ADMIN));
        capabilities.put("syncExternalProjects", hasAny(normalizedRoles, ADMIN));
        capabilities.put("linkProjectsToKeyResults", hasAny(normalizedRoles, ADMIN));
        capabilities.put("viewReports", hasAny(normalizedRoles, ADMIN, USER));
        capabilities.put("viewPresentation", hasAny(normalizedRoles, ADMIN, USER));
        capabilities.put("viewConsistency", hasAny(normalizedRoles, ADMIN));
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
        return normalizedRoles.contains(ADMIN);
    }

    private Set<String> normalize(Collection<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .map(this::normalizeAlias)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String normalizeAlias(String role) {
        return switch (role) {
            case "ADMINISTRADOR" -> ADMIN;
            case "USUARIO", "DECANO", "DIRECTOR", "DIRECTOR_ESCUELA", "JEFE", "JEFE_DPTO", "TUTOR", "PROFESOR" -> USER;
            default -> role;
        };
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
