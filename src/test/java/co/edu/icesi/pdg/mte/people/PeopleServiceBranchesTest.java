package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.api.dto.PeopleDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.ProjectTeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeopleServiceBranchesTest {

    @Mock RoleRepository roleRepository;
    @Mock PositionRepository positionRepository;
    @Mock ProfessorRepository professorRepository;
    @Mock TeacherPositionRepository teacherPositionRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ProjectTeacherRepository projectTeacherRepository;
    @Mock AuditService auditService;
    @InjectMocks PeopleService service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void positionRenameIsRejectedWhenAnotherPositionUsesTheName() {
        Position current = position(1L, "Director");
        when(positionRepository.findById(1L)).thenReturn(Optional.of(current));
        when(positionRepository.findByNameIgnoreCase("Tutor")).thenReturn(Optional.of(position(2L, "Tutor")));

        assertThatThrownBy(() -> service.updatePosition(1L, new PeopleDtos.PositionRequest("Tutor", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));

        when(positionRepository.findByNameIgnoreCase("Director")).thenReturn(Optional.of(current));
        when(positionRepository.save(current)).thenReturn(current);
        assertThat(service.updatePosition(1L, new PeopleDtos.PositionRequest("Director", "Nueva")).id()).isEqualTo(1L);

        when(positionRepository.findByNameIgnoreCase("Libre")).thenReturn(Optional.empty());
        assertThat(service.updatePosition(1L, new PeopleDtos.PositionRequest(" Libre ", null)).name()).isEqualTo("Libre");
    }

    @Test
    void professorUpdateRejectsEmailOfAnotherPerson() {
        Professor current = person(1L, "ana@icesi.edu.co", null);
        when(professorRepository.findById(1L)).thenReturn(Optional.of(current));
        when(professorRepository.findByEmailIgnoreCase("otra@icesi.edu.co"))
                .thenReturn(Optional.of(person(2L, "otra@icesi.edu.co", null)));

        assertThatThrownBy(() -> service.updateProfessor(1L,
                new PeopleDtos.ProfessorRequest("Ana", "otra@icesi.edu.co", 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("correo");
    }

    @Test
    void linkedAccountsKeepTheirEmailButStillAllowOtherChanges() {
        Professor linked = person(1L, "ana@icesi.edu.co", UUID.randomUUID());
        when(professorRepository.findById(1L)).thenReturn(Optional.of(linked));
        when(professorRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department()));
        when(professorRepository.save(linked)).thenReturn(linked);

        assertThatThrownBy(() -> service.updateProfessor(1L,
                new PeopleDtos.ProfessorRequest("Ana", "nueva@icesi.edu.co", 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cuenta de acceso");
        assertThatThrownBy(() -> service.updateDirectoryUser(1L,
                new PeopleDtos.DirectoryUserRequest("Ana", "nueva@icesi.edu.co", 1L, "ADMIN", true)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cuenta de acceso");

        var updated = service.updateProfessor(1L, new PeopleDtos.ProfessorRequest(" Ana Maria ", "ANA@icesi.edu.co", 1L));
        assertThat(updated.name()).isEqualTo("Ana Maria");
        assertThat(updated.email()).isEqualTo("ana@icesi.edu.co");
        verify(professorRepository).save(linked);
    }

    @Test
    void unlinkedAccountsMayChangeEmail() {
        Professor unlinked = person(1L, "ana@icesi.edu.co", null);
        when(professorRepository.findById(1L)).thenReturn(Optional.of(unlinked));
        when(professorRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department()));
        when(professorRepository.save(unlinked)).thenReturn(unlinked);

        var updated = service.updateProfessor(1L, new PeopleDtos.ProfessorRequest("Ana", "nueva@icesi.edu.co", 1L));

        assertThat(updated.email()).isEqualTo("nueva@icesi.edu.co");
    }

    @Test
    void directoryFiltersApplyEachCriterionIndependently() {
        Department dcsi = department();
        Department design = department(2L, "Diseno");
        Professor ana = person(1L, "ana@icesi.edu.co", null);
        ana.setName("Ana Maria");
        ana.setAccessRole("ADMIN");
        Professor bruno = person(2L, "bruno@icesi.edu.co", null);
        bruno.setName("Bruno");
        bruno.setDepartment(design);
        bruno.setAccessRole("MANAGER");
        bruno.setActive(false);
        when(professorRepository.findAll()).thenReturn(List.of(bruno, ana));

        assertThat(ids(service.listDirectoryUsers(null, null, "   ", null))).containsExactly(1L, 2L);
        assertThat(ids(service.listDirectoryUsers("BRUNO@", null, null, null))).containsExactly(2L);
        assertThat(ids(service.listDirectoryUsers("maria", null, null, null))).containsExactly(1L);
        assertThat(ids(service.listDirectoryUsers(null, dcsi.getId(), null, null))).containsExactly(1L);
        assertThat(ids(service.listDirectoryUsers(null, null, "gestor", null))).containsExactly(2L);
        assertThat(ids(service.listDirectoryUsers(null, null, null, true))).containsExactly(1L);
        assertThat(ids(service.listDirectoryUsers(null, null, null, false))).containsExactly(2L);
        assertThat(ids(service.listDirectoryUsers(null, 2L, "ADMIN", null))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN,ADMIN", "administrador,ADMIN",
            "MANAGER,MANAGER", "gestor,MANAGER", "decano,MANAGER", "director,MANAGER", "director_escuela,MANAGER",
            "jefe,MANAGER", "jefe_dpto,MANAGER", "user,MANAGER", "usuario,MANAGER",
            "CONTRIBUTOR,CONTRIBUTOR", "colaborador,CONTRIBUTOR", "profesor,CONTRIBUTOR", "tutor,CONTRIBUTOR",
            "VISITOR,VISITOR", "visitante,VISITOR"
    })
    void accessRoleAliasesAreNormalized(String raw, String expected) {
        when(professorRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department()));
        when(professorRepository.save(any(Professor.class))).thenAnswer(invocation -> {
            Professor saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        var created = service.createDirectoryUser(new PeopleDtos.DirectoryUserRequest(
                "Persona", "persona@icesi.edu.co", 1L, " " + raw + " ", null));

        assertThat(created.accessRole()).isEqualTo(expected);
    }

    @Test
    void unknownAccessRoleIsRejected() {
        when(professorRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department()));

        assertThatThrownBy(() -> service.createDirectoryUser(new PeopleDtos.DirectoryUserRequest(
                "Persona", "persona@icesi.edu.co", 1L, "SUPERUSER", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminRemovalGuardWorksWithoutAnExternalActor() {
        Professor admin = person(5L, "admin@icesi.edu.co", null);
        admin.setAccessRole("ADMIN");
        Professor otherAdmin = person(6L, "otro@icesi.edu.co", null);
        otherAdmin.setAccessRole("ADMIN");
        when(professorRepository.findById(5L)).thenReturn(Optional.of(admin));
        when(professorRepository.findByAccessRoleAndActiveTrueOrderByIdAsc("ADMIN")).thenReturn(List.of(admin, otherAdmin));

        // No authentication at all: nobody is "self", so only the last-administrator rule applies.
        service.deleteDirectoryUser(5L);

        // An authentication whose principal is not an external user behaves the same.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-user", null, List.of()));
        service.deleteDirectoryUser(5L);

        verify(professorRepository, org.mockito.Mockito.times(2)).delete(admin);
    }

    @Test
    void adminWithoutPersistedIdIsOnlyProtectedByTheLastAdminRule() {
        Professor transientAdmin = person(null, "admin@icesi.edu.co", null);
        transientAdmin.setAccessRole("ADMIN");
        when(professorRepository.findById(9L)).thenReturn(Optional.of(transientAdmin));
        when(professorRepository.findByAccessRoleAndActiveTrueOrderByIdAsc("ADMIN")).thenReturn(List.of(transientAdmin));

        assertThatThrownBy(() -> service.deleteProfessor(9L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("al menos un administrador");
    }

    private static List<Long> ids(List<PeopleDtos.DirectoryUserResponse> users) {
        return users.stream().map(PeopleDtos.DirectoryUserResponse::id).toList();
    }

    private static Position position(Long id, String name) {
        Position value = new Position();
        value.setId(id);
        value.setName(name);
        return value;
    }

    private static Department department() {
        return department(1L, "DCSI");
    }

    private static Department department(Long id, String name) {
        Department value = new Department();
        value.setId(id);
        value.setName(name);
        return value;
    }

    private static Professor person(Long id, String email, UUID authUserId) {
        Professor value = new Professor();
        value.setId(id);
        value.setName("Persona");
        value.setEmail(email);
        value.setDepartment(department());
        value.setAuthUserId(authUserId);
        return value;
    }
}
