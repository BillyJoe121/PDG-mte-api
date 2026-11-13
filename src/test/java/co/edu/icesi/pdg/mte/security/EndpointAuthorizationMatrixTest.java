package co.edu.icesi.pdg.mte.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Checks every API endpoint against an explicit role table: without a session every protected
 * endpoint answers 401, visitors cannot write, and each role-restricted handler refuses the roles
 * it does not list. Denied calls stop before the handler runs, so nothing is changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndpointAuthorizationMatrixTest {
    private static final List<String> ROLES = List.of("ADMIN", "MANAGER", "CONTRIBUTOR", "VISITOR");
    private static final Set<String> ALL = Set.copyOf(ROLES);
    private static final Set<String> ADMIN = Set.of("ADMIN");
    private static final Set<String> ADMIN_MANAGER = Set.of("ADMIN", "MANAGER");
    private static final Set<String> WORKING_ROLES = Set.of("ADMIN", "MANAGER", "CONTRIBUTOR");
    private static final Set<String> PUBLIC = Set.of(
            "GET /api/v1/health", "POST /api/v1/auth/login", "POST /api/v1/auth/set-password");
    private static final Map<String, Set<String>> EXPECTED = expectedRoles();

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext context;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyHandlerHasAnExpectedRoleEntry() {
        Set<String> mapped = new TreeSet<>();
        apiHandlers().forEach((info, handler) -> mapped.add(key(handler)));

        assertThat(mapped).containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());
    }

    @Test
    void protectedEndpointsRequireASession() throws Exception {
        List<String> open = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            if (PUBLIC.contains(endpoint.method() + " " + endpoint.pattern())) {
                continue;
            }
            int status = perform(endpoint, "");
            if (status != 401) {
                open.add(endpoint + " -> " + status);
            }
        }
        assertThat(open).isEmpty();
    }

    @Test
    void visitorsCannotReachAnyWriteEndpoint() throws Exception {
        List<String> reachable = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            boolean write = endpoint.method() != HttpMethod.GET;
            boolean ownSession = endpoint.pattern().startsWith("/api/v1/auth/");
            if (!write || ownSession) {
                continue;
            }
            int status = perform(endpoint, "Bearer test-visitor");
            if (status != 403) {
                reachable.add(endpoint + " -> " + status);
            }
        }
        assertThat(reachable).isEmpty();
    }

    @Test
    void roleRestrictedHandlersRefuseEveryUnlistedRole() throws Exception {
        List<String> leaks = new ArrayList<>();
        for (HandlerMethod handler : apiHandlers().values()) {
            Set<String> allowed = EXPECTED.get(key(handler));
            if (allowed == null) {
                continue;
            }
            Object bean = context.getBean((String) handler.getBean());
            for (String role : ROLES) {
                if (!allowed.contains(role) && !isDenied(bean, handler.getMethod(), role)) {
                    leaks.add(key(handler) + " allowed " + role);
                }
            }
        }
        assertThat(leaks).isEmpty();
    }

    private boolean isDenied(Object bean, Method method, String role) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ExternalUserContext(900L, "persona", "persona@icesi.edu.co", List.of(role), List.of(),
                        900L, "Persona de prueba", null),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        method.setAccessible(true);
        try {
            method.invoke(bean, defaultArguments(method));
            return false;
        } catch (InvocationTargetException exception) {
            return exception.getCause() instanceof AccessDeniedException;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Object[] defaultArguments(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] arguments = new Object[types.length];
        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            if (type == boolean.class) {
                arguments[index] = false;
            } else if (type == long.class) {
                arguments[index] = 0L;
            } else if (type == int.class) {
                arguments[index] = 0;
            } else if (type == double.class) {
                arguments[index] = 0d;
            }
        }
        return arguments;
    }

    private int perform(Endpoint endpoint, String authorization) throws Exception {
        String url = endpoint.pattern().replaceAll("\\{[^}]+}", "1");
        return mockMvc.perform(request(endpoint.method(), url)
                        .header("Authorization", authorization)
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        apiHandlers().forEach((info, handler) -> {
            for (String pattern : info.getPatternValues()) {
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    endpoints.add(new Endpoint(HttpMethod.valueOf(method.name()), pattern));
                }
            }
        });
        assertThat(endpoints).hasSizeGreaterThan(50);
        return endpoints;
    }

    private Map<RequestMappingInfo, HandlerMethod> apiHandlers() {
        Map<RequestMappingInfo, HandlerMethod> handlers = new HashMap<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("co.edu.icesi.pdg.mte")) {
                handlers.put(info, handler);
            }
        });
        return handlers;
    }

    private static String key(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }

    private static Map<String, Set<String>> expectedRoles() {
        Map<String, Set<String>> expected = new HashMap<>();
        allow(expected, ALL, "HealthController", "health");
        allow(expected, ALL, "AuthController", "login", "logout", "changePassword", "setPassword");
        allow(expected, ALL, "AuthContextController", "me");
        allow(expected, ADMIN, "AuditController", "list", "summary");
        allow(expected, ALL, "CatalogController", "listUnits", "listPeriods", "listDepartments", "listSchools");
        allow(expected, ADMIN, "CatalogController", "createUnit", "updateUnit", "updateUnitActive", "deleteUnit",
                "createPeriod", "updatePeriod", "updatePeriodStatus", "updatePeriodActive", "deletePeriod",
                "createSchool", "updateSchool", "deleteSchool",
                "createDepartment", "updateDepartment", "deleteDepartment");
        allow(expected, ADMIN_MANAGER, "ConsistencyController", "check", "csv");
        allow(expected, ALL, "DashboardController", "summary", "projectsByStatus", "keyResultsByStatus",
                "departments", "strategicBets", "goals");
        allow(expected, ALL, "ProjectKeyResultLinkController", "list");
        allow(expected, ADMIN_MANAGER, "ProjectKeyResultLinkController", "link", "unlink");
        allow(expected, ALL, "PeopleController", "listRoles", "listPositions", "listProfessors", "listTeacherPositions");
        allow(expected, ADMIN, "PeopleController", "listDirectoryUsers", "createDirectoryUser", "updateDirectoryUser",
                "updateDirectoryUserActive", "createAccessLink", "deleteDirectoryUser",
                "createRole", "updateRole", "deleteRole", "createPosition", "updatePosition", "deletePosition",
                "createProfessor", "updateProfessor", "deleteProfessor", "assignPosition", "removePosition");
        allow(expected, ALL, "PresentationController", "presentation");
        allow(expected, ALL, "ProjectController", "list", "get", "detail", "history", "impactChain",
                "contributionChain", "listTeachers");
        allow(expected, ADMIN_MANAGER, "ProjectController", "create", "update", "updateStatus",
                "assignTeacher", "removeTeacher");
        allow(expected, WORKING_ROLES, "ProjectController", "registerProgress");
        allow(expected, ADMIN, "ProjectController", "syncTrayectoria");
        allow(expected, ADMIN_MANAGER, "ReportController", "general", "departments", "keyResultRanking",
                "periodComparison", "consolidated", "csv", "pdf");
        allow(expected, ALL, "GoalController", "list", "get");
        allow(expected, ADMIN_MANAGER, "GoalController", "create", "update", "setActive", "attachPeriod", "detachPeriod");
        allow(expected, ALL, "KeyResultController", "list", "get", "progressRecords");
        allow(expected, ADMIN_MANAGER, "KeyResultController", "create", "update", "updateProgress", "setStatus", "setActive");
        allow(expected, ALL, "StrategicBetController", "list", "get");
        allow(expected, ADMIN_MANAGER, "StrategicBetController", "create", "update", "setActive");
        allow(expected, ALL, "StrategicHierarchyController", "tree");
        allow(expected, ALL, "WorldController", "list");
        allow(expected, ADMIN, "WorldController", "create", "update", "delete");
        return expected;
    }

    private static void allow(Map<String, Set<String>> expected, Set<String> roles, String controller, String... methods) {
        for (String method : methods) {
            assertThat(expected.put(controller + "#" + method, roles)).as(controller + "#" + method).isNull();
        }
    }

    private record Endpoint(HttpMethod method, String pattern) {
        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }
}
