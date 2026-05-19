package co.edu.icesi.pdg.mte.people;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.PeopleDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.catalog.Department;
import co.edu.icesi.pdg.mte.catalog.DepartmentRepository;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.ProjectTeacherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PeopleService {
    private final RoleRepository roleRepository;
    private final PositionRepository positionRepository;
    private final ProfessorRepository professorRepository;
    private final TeacherPositionRepository teacherPositionRepository;
    private final DepartmentRepository departmentRepository;
    private final ProjectTeacherRepository projectTeacherRepository;
    private final AuditService auditService;

    public PeopleService(
            RoleRepository roleRepository,
            PositionRepository positionRepository,
            ProfessorRepository professorRepository,
            TeacherPositionRepository teacherPositionRepository,
            DepartmentRepository departmentRepository,
            ProjectTeacherRepository projectTeacherRepository,
            AuditService auditService
    ) {
        this.roleRepository = roleRepository;
        this.positionRepository = positionRepository;
        this.professorRepository = professorRepository;
        this.teacherPositionRepository = teacherPositionRepository;
        this.departmentRepository = departmentRepository;
        this.projectTeacherRepository = projectTeacherRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PeopleDtos.RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    public PeopleDtos.RoleResponse createRole(PeopleDtos.RoleRequest request) {
        String name = request.name().trim();
        if (roleRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un rol con ese nombre.");
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(request.description());
        PeopleDtos.RoleResponse response = Mapper.toResponse(roleRepository.save(role));
        auditService.record(AuditAction.CREATE, "ROLE", response.id(), "Rol creado: " + response.name(), null, response);
        return response;
    }

    public PeopleDtos.RoleResponse updateRole(Long id, PeopleDtos.RoleRequest request) {
        Role role = findRole(id);
        PeopleDtos.RoleResponse before = Mapper.toResponse(role);
        Optional<Role> existing = roleRepository.findByNameIgnoreCase(request.name().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un rol con ese nombre.");
        }
        role.setName(request.name().trim());
        role.setDescription(request.description());
        PeopleDtos.RoleResponse response = Mapper.toResponse(roleRepository.save(role));
        auditService.record(AuditAction.UPDATE, "ROLE", response.id(), "Rol actualizado: " + response.name(), before, response);
        return response;
    }

    public void deleteRole(Long id) {
        Role role = findRole(id);
        if (projectTeacherRepository.existsByRole_Id(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un rol en uso.");
        }
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<PeopleDtos.PositionResponse> listPositions() {
        return positionRepository.findAll().stream()
                .sorted(Comparator.comparing(Position::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    public PeopleDtos.PositionResponse createPosition(PeopleDtos.PositionRequest request) {
        String name = request.name().trim();
        if (positionRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un cargo con ese nombre.");
        }
        Position position = new Position();
        position.setName(name);
        position.setDescription(request.description());
        PeopleDtos.PositionResponse response = Mapper.toResponse(positionRepository.save(position));
        auditService.record(AuditAction.CREATE, "POSITION", response.id(), "Cargo creado: " + response.name(), null, response);
        return response;
    }

    public PeopleDtos.PositionResponse updatePosition(Long id, PeopleDtos.PositionRequest request) {
        Position position = findPosition(id);
        PeopleDtos.PositionResponse before = Mapper.toResponse(position);
        Optional<Position> existing = positionRepository.findByNameIgnoreCase(request.name().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un cargo con ese nombre.");
        }
        position.setName(request.name().trim());
        position.setDescription(request.description());
        PeopleDtos.PositionResponse response = Mapper.toResponse(positionRepository.save(position));
        auditService.record(AuditAction.UPDATE, "POSITION", response.id(), "Cargo actualizado: " + response.name(), before, response);
        return response;
    }

    public void deletePosition(Long id) {
        Position position = findPosition(id);
        if (teacherPositionRepository.existsByPosition_Id(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un cargo en uso.");
        }
        positionRepository.delete(position);
    }

    @Transactional(readOnly = true)
    public List<PeopleDtos.ProfessorResponse> listProfessors(Long departmentId) {
        return professorRepository.findAll().stream()
                .filter(professor -> departmentId == null || professor.getDepartment().getId().equals(departmentId))
                .sorted(Comparator.comparing(Professor::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    public PeopleDtos.ProfessorResponse createProfessor(PeopleDtos.ProfessorRequest request) {
        String email = request.email().trim().toLowerCase();
        if (professorRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un profesor con ese correo.");
        }
        Professor professor = new Professor();
        professor.setName(request.name().trim());
        professor.setEmail(email);
        professor.setDepartment(findDepartment(request.departmentId()));
        PeopleDtos.ProfessorResponse response = Mapper.toResponse(professorRepository.save(professor));
        auditService.record(AuditAction.CREATE, "PROFESSOR", response.id(), "Profesor creado: " + response.email(), null, response);
        return response;
    }

    public PeopleDtos.ProfessorResponse updateProfessor(Long id, PeopleDtos.ProfessorRequest request) {
        Professor professor = findProfessor(id);
        PeopleDtos.ProfessorResponse before = Mapper.toResponse(professor);
        String email = request.email().trim().toLowerCase();
        Optional<Professor> existing = professorRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un profesor con ese correo.");
        }
        professor.setName(request.name().trim());
        professor.setEmail(email);
        professor.setDepartment(findDepartment(request.departmentId()));
        PeopleDtos.ProfessorResponse response = Mapper.toResponse(professorRepository.save(professor));
        auditService.record(AuditAction.UPDATE, "PROFESSOR", response.id(), "Profesor actualizado: " + response.email(), before, response);
        return response;
    }

    public void deleteProfessor(Long id) {
        Professor professor = findProfessor(id);
        if (teacherPositionRepository.existsByTeacher_Id(id) || projectTeacherRepository.existsByTeacher_Id(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un profesor en uso.");
        }
        professorRepository.delete(professor);
    }

    @Transactional(readOnly = true)
    public List<PeopleDtos.TeacherPositionResponse> listTeacherPositions(Long professorId) {
        findProfessor(professorId);
        return teacherPositionRepository.findAll().stream()
                .filter(teacherPosition -> teacherPosition.getTeacher().getId().equals(professorId))
                .map(Mapper::toResponse)
                .toList();
    }

    public PeopleDtos.TeacherPositionResponse assignPosition(Long professorId, PeopleDtos.TeacherPositionRequest request) {
        Professor professor = findProfessor(professorId);
        Position position = findPosition(request.positionId());
        TeacherPosition teacherPosition = teacherPositionRepository
                .findById(new TeacherPositionId(position.getId(), professor.getId()))
                .orElseGet(TeacherPosition::new);
        teacherPosition.setPosition(position);
        teacherPosition.setTeacher(professor);
        teacherPosition.setIsActive(request.active() == null ? Boolean.TRUE : request.active());
        PeopleDtos.TeacherPositionResponse response = Mapper.toResponse(teacherPositionRepository.save(teacherPosition));
        auditService.record(AuditAction.UPDATE, "TEACHER_POSITION", professorId + ":" + position.getId(), "Cargo asignado a profesor.", null, response);
        return response;
    }

    public void removePosition(Long professorId, Long positionId) {
        TeacherPosition teacherPosition = teacherPositionRepository.findById(new TeacherPositionId(positionId, professorId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Cargo de profesor no encontrado."));
        teacherPositionRepository.delete(teacherPosition);
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Rol no encontrado."));
    }

    private Position findPosition(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Cargo no encontrado."));
    }

    private Professor findProfessor(Long id) {
        return professorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Profesor no encontrado."));
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Departamento no encontrado."));
    }
}
