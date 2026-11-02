package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.api.dto.PeopleDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.ProjectTeacherRepository;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeopleServiceAdminGuardTest {

    private static final long ACTING_ID = 1L;
    private static final long OTHER_ID = 2L;

    @Mock RoleRepository roleRepository;
    @Mock PositionRepository positionRepository;
    @Mock ProfessorRepository professorRepository;
    @Mock TeacherPositionRepository teacherPositionRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ProjectTeacherRepository projectTeacherRepository;
    @Mock AuditService auditService;
    @InjectMocks PeopleService service;

    private final Department department = department();

    @BeforeEach
    void authenticateAsAdmin() {
        ExternalUserContext context = new ExternalUserContext(ACTING_ID, "admin", "admin@icesi.edu.co",
                List.of("ADMIN"), List.of(), ACTING_ID, "Admin", null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(context, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesToDeactivateOwnAccount() {
        Professor self = user(ACTING_ID, "ADMIN", true);
        when(professorRepository.findById(ACTING_ID)).thenReturn(Optional.of(self));

        assertConflict(() -> service.updateDirectoryUserActive(ACTING_ID, false), "tu propia cuenta");
        assertThat(self.isActive()).isTrue();
        verify(professorRepository, never()).save(any());
    }

    @Test
    void refusesToDeactivateOwnAccountThroughUpdate() {
        when(professorRepository.findById(ACTING_ID)).thenReturn(Optional.of(user(ACTING_ID, "ADMIN", true)));
        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.empty());

        assertConflict(() -> service.updateDirectoryUser(ACTING_ID, request("ADMIN", false)), "tu propia cuenta");
        verify(professorRepository, never()).save(any());
    }

    @Test
    void refusesToDemoteOwnAccount() {
        Professor self = user(ACTING_ID, "ADMIN", true);
        when(professorRepository.findById(ACTING_ID)).thenReturn(Optional.of(self));
        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.empty());

        assertConflict(() -> service.updateDirectoryUser(ACTING_ID, request("MANAGER", true)), "rol de administrador");
        assertThat(self.getAccessRole()).isEqualTo("ADMIN");
        verify(professorRepository, never()).save(any());
    }

    @Test
    void refusesToDeleteOwnAccount() {
        Professor self = user(ACTING_ID, "ADMIN", true);
        when(professorRepository.findById(ACTING_ID)).thenReturn(Optional.of(self));

        assertConflict(() -> service.deleteDirectoryUser(ACTING_ID), "tu propia cuenta");
        verify(professorRepository, never()).delete(any());
    }

    @Test
    void refusesToDeactivateLastActiveAdmin() {
        Professor last = user(OTHER_ID, "ADMIN", true);
        when(professorRepository.findById(OTHER_ID)).thenReturn(Optional.of(last));
        when(professorRepository.countByAccessRoleAndActiveTrue("ADMIN")).thenReturn(1L);

        assertConflict(() -> service.updateDirectoryUserActive(OTHER_ID, false), "al menos un administrador activo");
        assertThat(last.isActive()).isTrue();
        verify(professorRepository, never()).save(any());
    }

    @Test
    void refusesToDemoteLastActiveAdmin() {
        Professor last = user(OTHER_ID, "ADMIN", true);
        when(professorRepository.findById(OTHER_ID)).thenReturn(Optional.of(last));
        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.empty());
        when(professorRepository.countByAccessRoleAndActiveTrue("ADMIN")).thenReturn(1L);

        assertConflict(() -> service.updateDirectoryUser(OTHER_ID, request("MANAGER", true)), "al menos un administrador activo");
        assertThat(last.getAccessRole()).isEqualTo("ADMIN");
        verify(professorRepository, never()).save(any());
    }

    @Test
    void refusesToDeleteLastActiveAdmin() {
        Professor last = user(OTHER_ID, "ADMIN", true);
        when(professorRepository.findById(OTHER_ID)).thenReturn(Optional.of(last));
        when(professorRepository.countByAccessRoleAndActiveTrue("ADMIN")).thenReturn(1L);

        assertConflict(() -> service.deleteDirectoryUser(OTHER_ID), "al menos un administrador activo");
        verify(professorRepository, never()).delete(any());
    }

    @Test
    void allowsRemovingAnotherAdminWhenOthersRemainActive() {
        Professor other = user(OTHER_ID, "ADMIN", true);
        when(professorRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(professorRepository.countByAccessRoleAndActiveTrue("ADMIN")).thenReturn(2L);
        when(professorRepository.save(other)).thenReturn(other);

        assertThat(service.updateDirectoryUserActive(OTHER_ID, false).active()).isFalse();

        other.setActive(true);
        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.empty());
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        assertThat(service.updateDirectoryUser(OTHER_ID, request("MANAGER", true)).accessRole()).isEqualTo("MANAGER");

        other.setAccessRole("ADMIN");
        service.deleteDirectoryUser(OTHER_ID);
        verify(professorRepository).delete(other);
    }

    @Test
    void allowsChangesOnNonAdminsAndInactiveAdmins() {
        Professor contributor = user(OTHER_ID, "CONTRIBUTOR", true);
        when(professorRepository.findById(OTHER_ID)).thenReturn(Optional.of(contributor));
        when(professorRepository.save(contributor)).thenReturn(contributor);

        assertThat(service.updateDirectoryUserActive(OTHER_ID, false).active()).isFalse();
        service.deleteDirectoryUser(OTHER_ID);
        verify(professorRepository).delete(contributor);

        Professor inactiveAdmin = user(3L, "ADMIN", false);
        when(professorRepository.findById(3L)).thenReturn(Optional.of(inactiveAdmin));
        service.deleteDirectoryUser(3L);
        verify(professorRepository).delete(inactiveAdmin);
        verify(professorRepository, never()).countByAccessRoleAndActiveTrue(any());
    }

    @Test
    void allowsKeepingOwnAdminRoleAndActivation() {
        Professor self = user(ACTING_ID, "ADMIN", true);
        when(professorRepository.findById(ACTING_ID)).thenReturn(Optional.of(self));
        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.empty());
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(professorRepository.save(self)).thenReturn(self);

        var updated = service.updateDirectoryUser(ACTING_ID, request("administrador", null));
        assertThat(updated.accessRole()).isEqualTo("ADMIN");
        assertThat(updated.active()).isTrue();
        assertThat(service.updateDirectoryUserActive(ACTING_ID, true).active()).isTrue();
    }

    @Test
    void refusesToDeleteLastActiveAdminThroughProfessorEndpoint() {
        Professor last = user(OTHER_ID, "ADMIN", true);
        when(professorRepository.findById(OTHER_ID)).thenReturn(Optional.of(last));
        when(professorRepository.countByAccessRoleAndActiveTrue("ADMIN")).thenReturn(1L);

        assertConflict(() -> service.deleteProfessor(OTHER_ID), "al menos un administrador activo");
        verify(professorRepository, never()).delete(any());
    }

    private static void assertConflict(ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining(message);
    }

    private PeopleDtos.DirectoryUserRequest request(String role, Boolean active) {
        return new PeopleDtos.DirectoryUserRequest("Ana", "ana@icesi.edu.co", 1L, role, active);
    }

    private Professor user(Long id, String role, boolean active) {
        Professor value = new Professor();
        value.setId(id);
        value.setName("Persona " + id);
        value.setEmail("persona" + id + "@icesi.edu.co");
        value.setDepartment(department);
        value.setAccessRole(role);
        value.setActive(active);
        return value;
    }

    private static Department department() {
        Department value = new Department();
        value.setId(1L);
        value.setName("DCSI");
        return value;
    }
}
