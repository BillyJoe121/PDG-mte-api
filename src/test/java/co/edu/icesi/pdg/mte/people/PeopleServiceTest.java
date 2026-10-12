package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.api.dto.PeopleDtos;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.ProjectTeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeopleServiceTest {

    @Mock RoleRepository roleRepository;
    @Mock PositionRepository positionRepository;
    @Mock ProfessorRepository professorRepository;
    @Mock TeacherPositionRepository teacherPositionRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ProjectTeacherRepository projectTeacherRepository;
    @Mock AuditService auditService;
    @InjectMocks PeopleService service;

    @Test
    void listsRolesAndPositionsAlphabetically() {
        when(roleRepository.findAll()).thenReturn(List.of(role(2L, "Zeta"), role(1L, "alpha")));
        when(positionRepository.findAll()).thenReturn(List.of(position(2L, "Tutor"), position(1L, "Director")));

        assertThat(service.listRoles()).extracting(PeopleDtos.RoleResponse::name).containsExactly("alpha", "Zeta");
        assertThat(service.listPositions()).extracting(PeopleDtos.PositionResponse::name).containsExactly("Director", "Tutor");
    }

    @Test
    void createsAndUpdatesRoleWithTrimmedNames() {
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role saved = invocation.getArgument(0); saved.setId(1L); return saved;
        });
        var created = service.createRole(new PeopleDtos.RoleRequest("  Responsable  ", "Desc"));
        assertThat(created.name()).isEqualTo("Responsable");

        Role current = role(1L, "Responsable");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(current));
        when(roleRepository.findByNameIgnoreCase("Líder")).thenReturn(Optional.empty());
        var updated = service.updateRole(1L, new PeopleDtos.RoleRequest(" Líder ", "Nueva"));
        assertThat(updated.name()).isEqualTo("Líder");
    }

    @Test
    void rejectsDuplicateRoleOnCreateAndUpdateButAllowsSameRecord() {
        when(roleRepository.existsByNameIgnoreCase("Responsable")).thenReturn(true);
        assertThatThrownBy(() -> service.createRole(new PeopleDtos.RoleRequest("Responsable", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ya existe");

        Role current = role(1L, "Actual");
        Role other = role(2L, "Duplicado");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(current));
        when(roleRepository.findByNameIgnoreCase("Duplicado")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateRole(1L, new PeopleDtos.RoleRequest("Duplicado", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ya existe");

        when(roleRepository.findByNameIgnoreCase("Actual")).thenReturn(Optional.of(current));
        when(roleRepository.save(current)).thenReturn(current);
        assertThat(service.updateRole(1L, new PeopleDtos.RoleRequest("Actual", null)).id()).isEqualTo(1L);
    }

    @Test
    void deletesOnlyUnusedRoles() {
        Role value = role(1L, "Responsable");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(value));
        when(projectTeacherRepository.existsByRole_Id(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deleteRole(1L)).isInstanceOf(BusinessException.class).hasMessageContaining("en uso");
        service.deleteRole(1L);
        verify(roleRepository).delete(value);
    }

    @Test
    void createsUpdatesAndProtectsPositions() {
        when(positionRepository.save(any(Position.class))).thenAnswer(invocation -> {
            Position saved = invocation.getArgument(0); saved.setId(1L); return saved;
        });
        assertThat(service.createPosition(new PeopleDtos.PositionRequest(" Tutor ", "Desc")).name()).isEqualTo("Tutor");
        when(positionRepository.existsByNameIgnoreCase("Tutor")).thenReturn(true);
        assertThatThrownBy(() -> service.createPosition(new PeopleDtos.PositionRequest("Tutor", null)))
                .isInstanceOf(BusinessException.class);

        Position current = position(1L, "Tutor");
        Position other = position(2L, "Director");
        when(positionRepository.findById(1L)).thenReturn(Optional.of(current));
        when(positionRepository.findByNameIgnoreCase("Director")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updatePosition(1L, new PeopleDtos.PositionRequest("Director", null)))
                .isInstanceOf(BusinessException.class);
        when(positionRepository.findByNameIgnoreCase("Tutor")).thenReturn(Optional.of(current));
        assertThat(service.updatePosition(1L, new PeopleDtos.PositionRequest("Tutor", "Nueva")).description()).isEqualTo("Nueva");

        when(teacherPositionRepository.existsByPosition_Id(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deletePosition(1L)).isInstanceOf(BusinessException.class).hasMessageContaining("en uso");
        service.deletePosition(1L);
        verify(positionRepository).delete(current);
    }

    @Test
    void filtersProfessorsByDepartmentAndSortsThem() {
        Department first = department(1L, "DCSI");
        Department second = department(2L, "Diseño");
        when(professorRepository.findAll()).thenReturn(List.of(
                professor(2L, "Zoe", "z@icesi.edu.co", second),
                professor(1L, "ana", "a@icesi.edu.co", first)));

        assertThat(service.listProfessors(null)).extracting(PeopleDtos.ProfessorResponse::name).containsExactly("ana", "Zoe");
        assertThat(service.listProfessors(1L)).extracting(PeopleDtos.ProfessorResponse::departmentId).containsExactly(1L);
    }

    @Test
    void createsUpdatesAndProtectsProfessors() {
        Department department = department(1L, "DCSI");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(professorRepository.save(any(Professor.class))).thenAnswer(invocation -> {
            Professor saved = invocation.getArgument(0); if (saved.getId() == null) saved.setId(1L); return saved;
        });
        var created = service.createProfessor(new PeopleDtos.ProfessorRequest(" Ana ", " ANA@ICESI.EDU.CO ", 1L));
        assertThat(created.email()).isEqualTo("ana@icesi.edu.co");
        when(professorRepository.existsByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(true);
        assertThatThrownBy(() -> service.createProfessor(new PeopleDtos.ProfessorRequest("Ana", "ana@icesi.edu.co", 1L)))
                .isInstanceOf(BusinessException.class);

        Professor current = professor(1L, "Ana", "old@icesi.edu.co", department);
        Professor other = professor(2L, "Otro", "used@icesi.edu.co", department);
        when(professorRepository.findById(1L)).thenReturn(Optional.of(current));
        when(professorRepository.findByEmailIgnoreCase("used@icesi.edu.co")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateProfessor(1L, new PeopleDtos.ProfessorRequest("Ana", "used@icesi.edu.co", 1L)))
                .isInstanceOf(BusinessException.class);
        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.of(current));
        assertThat(service.updateProfessor(1L, new PeopleDtos.ProfessorRequest("Ana", "ana@icesi.edu.co", 1L)).email())
                .isEqualTo("ana@icesi.edu.co");

        when(teacherPositionRepository.existsByTeacher_Id(1L)).thenReturn(true, false, false);
        when(projectTeacherRepository.existsByTeacher_Id(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deleteProfessor(1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deleteProfessor(1L)).isInstanceOf(BusinessException.class);
        service.deleteProfessor(1L);
        verify(professorRepository).delete(current);
    }

    @Test
    void filtersDirectoryAcrossSearchDepartmentRoleAndActiveState() {
        Department dcsi = department(1L, "DCSI");
        Department design = department(2L, "Diseño");
        Professor ana = professor(1L, "Ana María", "ana@icesi.edu.co", dcsi);
        ana.setAccessRole("ADMIN"); ana.setActive(true);
        Professor bruno = professor(2L, "Bruno", "bruno@icesi.edu.co", design);
        bruno.setAccessRole("MANAGER"); bruno.setActive(false);
        when(professorRepository.findAll()).thenReturn(List.of(bruno, ana));

        assertThat(service.listDirectoryUsers(null, null, null, null)).hasSize(2);
        assertThat(service.listDirectoryUsers("  ANA@  ", null, null, null)).extracting(PeopleDtos.DirectoryUserResponse::id).containsExactly(1L);
        assertThat(service.listDirectoryUsers("Bruno", 2L, "director", false)).extracting(PeopleDtos.DirectoryUserResponse::id).containsExactly(2L);
        assertThat(service.listDirectoryUsers("missing", 1L, "ADMIN", true)).isEmpty();
    }

    @Test
    void createsDirectoryUsersAndNormalizesEveryRoleFamily() {
        Department department = department(1L, "DCSI");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(professorRepository.save(any(Professor.class))).thenAnswer(invocation -> {
            Professor saved = invocation.getArgument(0); saved.setId(10L); return saved;
        });

        assertThat(createDirectory("administrador", null).accessRole()).isEqualTo("ADMIN");
        assertThat(createDirectory("decano", false)).extracting(PeopleDtos.DirectoryUserResponse::accessRole, PeopleDtos.DirectoryUserResponse::active)
                .containsExactly("MANAGER", false);
        assertThat(createDirectory("profesor", true).accessRole()).isEqualTo("CONTRIBUTOR");
        assertThatThrownBy(() -> createDirectory("desconocido", true)).isInstanceOf(BusinessException.class).hasMessageContaining("no es valido");
    }

    @Test
    void rejectsDuplicateDirectoryUsersAndConflictingUpdates() {
        when(professorRepository.existsByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(true);
        var request = directoryRequest("ADMIN", true);
        assertThatThrownBy(() -> service.createDirectoryUser(request)).isInstanceOf(BusinessException.class);

        Department department = department(1L, "DCSI");
        Professor current = professor(1L, "Ana", "old@icesi.edu.co", department);
        Professor other = professor(2L, "Otro", "ana@icesi.edu.co", department);
        when(professorRepository.findById(1L)).thenReturn(Optional.of(current));
        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateDirectoryUser(1L, request)).isInstanceOf(BusinessException.class);

        when(professorRepository.findByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(Optional.of(current));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(professorRepository.save(current)).thenReturn(current);
        assertThat(service.updateDirectoryUser(1L, request).accessRole()).isEqualTo("ADMIN");
    }

    @Test
    void activatesDeactivatesAndDeletesOnlyUnassignedDirectoryUsers() {
        Professor current = professor(1L, "Ana", "ana@icesi.edu.co", department(1L, "DCSI"));
        when(professorRepository.findById(1L)).thenReturn(Optional.of(current));
        when(professorRepository.save(current)).thenReturn(current);
        assertThat(service.updateDirectoryUserActive(1L, false).active()).isFalse();
        assertThat(service.updateDirectoryUserActive(1L, true).active()).isTrue();

        when(teacherPositionRepository.existsByTeacher_Id(1L)).thenReturn(true, false, false);
        when(projectTeacherRepository.existsByTeacher_Id(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deleteDirectoryUser(1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deleteDirectoryUser(1L)).isInstanceOf(BusinessException.class);
        service.deleteDirectoryUser(1L);
        verify(professorRepository).delete(current);
    }

    @Test
    void listsAssignsReassignsAndRemovesTeacherPositions() {
        Professor teacher = professor(1L, "Ana", "ana@icesi.edu.co", department(1L, "DCSI"));
        Position position = position(2L, "Tutor");
        TeacherPosition assignment = assignment(position, teacher, true);
        when(professorRepository.findById(1L)).thenReturn(Optional.of(teacher));
        when(positionRepository.findById(2L)).thenReturn(Optional.of(position));
        when(teacherPositionRepository.findAll()).thenReturn(List.of(assignment, assignment(position, professor(3L, "Otro", "o@i.edu.co", department(1L, "DCSI")), true)));
        assertThat(service.listTeacherPositions(1L)).hasSize(1);

        when(teacherPositionRepository.findById(new TeacherPositionId(2L, 1L))).thenReturn(Optional.empty(), Optional.of(assignment), Optional.of(assignment));
        when(teacherPositionRepository.save(any(TeacherPosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.assignPosition(1L, new PeopleDtos.TeacherPositionRequest(2L, null)).active()).isTrue();
        assertThat(service.assignPosition(1L, new PeopleDtos.TeacherPositionRequest(2L, false)).active()).isFalse();

        service.removePosition(1L, 2L);
        verify(teacherPositionRepository).delete(assignment);
    }

    @Test
    void reportsMissingEntitiesAndAssignments() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());
        when(positionRepository.findById(99L)).thenReturn(Optional.empty());
        when(professorRepository.findById(99L)).thenReturn(Optional.empty());
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteRole(99L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deletePosition(99L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deleteProfessor(99L)).isInstanceOf(BusinessException.class);

        Professor teacher = professor(1L, "Ana", "ana@icesi.edu.co", department(1L, "DCSI"));
        when(professorRepository.findById(1L)).thenReturn(Optional.of(teacher));
        assertThatThrownBy(() -> service.assignPosition(1L, new PeopleDtos.TeacherPositionRequest(99L, true))).isInstanceOf(BusinessException.class);
        when(teacherPositionRepository.findById(new TeacherPositionId(2L, 1L))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.removePosition(1L, 2L)).isInstanceOf(BusinessException.class);

        when(professorRepository.existsByEmailIgnoreCase("ana@icesi.edu.co")).thenReturn(false);
        assertThatThrownBy(() -> service.createProfessor(new PeopleDtos.ProfessorRequest("Ana", "ana@icesi.edu.co", 99L)))
                .isInstanceOf(BusinessException.class);
    }

    private PeopleDtos.DirectoryUserResponse createDirectory(String role, Boolean active) {
        return service.createDirectoryUser(directoryRequest(role, active));
    }

    private PeopleDtos.DirectoryUserRequest directoryRequest(String role, Boolean active) {
        return new PeopleDtos.DirectoryUserRequest(" Ana ", " ANA@ICESI.EDU.CO ", 1L, role, active);
    }

    private static Role role(Long id, String name) {
        Role value = new Role(); value.setId(id); value.setName(name); value.setDescription("Desc"); return value;
    }

    private static Position position(Long id, String name) {
        Position value = new Position(); value.setId(id); value.setName(name); value.setDescription("Desc"); return value;
    }

    private static Department department(Long id, String name) {
        Department value = new Department(); value.setId(id); value.setName(name); return value;
    }

    private static Professor professor(Long id, String name, String email, Department department) {
        Professor value = new Professor(); value.setId(id); value.setName(name); value.setEmail(email); value.setDepartment(department); return value;
    }

    private static TeacherPosition assignment(Position position, Professor teacher, boolean active) {
        TeacherPosition value = new TeacherPosition(); value.setPosition(position); value.setTeacher(teacher); value.setIsActive(active); return value;
    }
}
