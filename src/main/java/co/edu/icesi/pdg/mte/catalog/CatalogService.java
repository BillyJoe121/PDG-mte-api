package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.audit.AuditService;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.ProjectRepository;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CatalogService {

    private final MeasurementUnitRepository unitRepository;
    private final AcademicPeriodRepository periodRepository;
    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final KeyResultRepository keyResultRepository;
    private final ObjectiveRepository objectiveRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public CatalogService(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            SchoolRepository schoolRepository,
            DepartmentRepository departmentRepository,
            InstitutionalGoalRepository goalRepository,
            KeyResultRepository keyResultRepository,
            ObjectiveRepository objectiveRepository,
            ProjectRepository projectRepository,
            AuditService auditService
    ) {
        this.unitRepository = unitRepository;
        this.periodRepository = periodRepository;
        this.schoolRepository = schoolRepository;
        this.departmentRepository = departmentRepository;
        this.goalRepository = goalRepository;
        this.keyResultRepository = keyResultRepository;
        this.objectiveRepository = objectiveRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.MeasurementUnitResponse> listUnits() {
        return unitRepository.findAll().stream()
                .sorted(Comparator.comparing(MeasurementUnit::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    public CatalogDtos.MeasurementUnitResponse createUnit(CatalogDtos.MeasurementUnitRequest request) {
        String name = request.name().trim();
        if (unitRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe una unidad de medida con ese nombre.");
        }
        MeasurementUnit unit = new MeasurementUnit();
        unit.setName(name);
        unit.setType(request.type());
        unit.setDescription(request.description());
        CatalogDtos.MeasurementUnitResponse response = Mapper.toResponse(unitRepository.save(unit));
        auditService.record(AuditAction.CREATE, "MEASUREMENT_UNIT", response.id(), "Unidad de medida creada: " + response.name(), null, response);
        return response;
    }

    public CatalogDtos.MeasurementUnitResponse updateUnit(Long id, CatalogDtos.MeasurementUnitRequest request) {
        MeasurementUnit unit = findUnit(id);
        CatalogDtos.MeasurementUnitResponse before = Mapper.toResponse(unit);
        assertUnitNameIsAvailable(request.name(), id);
        unit.setName(request.name().trim());
        unit.setType(request.type());
        unit.setDescription(request.description());
        CatalogDtos.MeasurementUnitResponse response = Mapper.toResponse(unitRepository.save(unit));
        auditService.record(AuditAction.UPDATE, "MEASUREMENT_UNIT", response.id(), "Unidad de medida actualizada: " + response.name(), before, response);
        return response;
    }

    public CatalogDtos.MeasurementUnitResponse updateUnitActive(Long id, CatalogDtos.MeasurementUnitActiveRequest request) {
        MeasurementUnit unit = findUnit(id);
        CatalogDtos.MeasurementUnitResponse before = Mapper.toResponse(unit);
        unit.setActive(request.active());
        CatalogDtos.MeasurementUnitResponse response = Mapper.toResponse(unitRepository.save(unit));
        auditService.record(AuditAction.STATUS_CHANGE, "MEASUREMENT_UNIT", response.id(), "Estado de unidad actualizado a " + response.active(), before, response);
        return response;
    }

    public void deleteUnit(Long id) {
        MeasurementUnit unit = findUnit(id);
        if (isUnitInUse(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar una unidad de medida en uso; marquela como inactiva.");
        }
        unitRepository.delete(unit);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.AcademicPeriodResponse> listPeriods() {
        return periodRepository.findAll().stream()
                .sorted(Comparator.comparing(AcademicPeriod::getStartDate).thenComparing(AcademicPeriod::getName))
                .map(Mapper::toResponse)
                .toList();
    }

    public CatalogDtos.AcademicPeriodResponse createPeriod(CatalogDtos.AcademicPeriodRequest request) {
        String name = request.name().trim();
        assertPeriodDatesAreValid(request);
        if (periodRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un periodo academico con ese nombre.");
        }

        AcademicPeriod period = new AcademicPeriod();
        period.setName(name);
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setStatus(request.status());
        CatalogDtos.AcademicPeriodResponse response = Mapper.toResponse(periodRepository.save(period));
        auditService.record(AuditAction.CREATE, "ACADEMIC_PERIOD", response.id(), "Periodo academico creado: " + response.name(), null, response);
        return response;
    }

    public CatalogDtos.AcademicPeriodResponse updatePeriod(Long id, CatalogDtos.AcademicPeriodRequest request) {
        AcademicPeriod period = findPeriod(id);
        CatalogDtos.AcademicPeriodResponse before = Mapper.toResponse(period);
        assertPeriodDatesAreValid(request);
        assertPeriodNameIsAvailable(request.name(), id);
        period.setName(request.name().trim());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setStatus(request.status());
        CatalogDtos.AcademicPeriodResponse response = Mapper.toResponse(periodRepository.save(period));
        auditService.record(AuditAction.UPDATE, "ACADEMIC_PERIOD", response.id(), "Periodo academico actualizado: " + response.name(), before, response);
        return response;
    }

    public CatalogDtos.AcademicPeriodResponse updatePeriodStatus(Long id, CatalogDtos.AcademicPeriodStatusRequest request) {
        AcademicPeriod period = findPeriod(id);
        CatalogDtos.AcademicPeriodResponse before = Mapper.toResponse(period);
        period.setStatus(request.status());
        CatalogDtos.AcademicPeriodResponse response = Mapper.toResponse(periodRepository.save(period));
        auditService.record(AuditAction.STATUS_CHANGE, "ACADEMIC_PERIOD", response.id(), "Estado de periodo actualizado a " + response.status(), before, response);
        return response;
    }

    public CatalogDtos.AcademicPeriodResponse updatePeriodActive(Long id, CatalogDtos.AcademicPeriodActiveRequest request) {
        AcademicPeriod period = findPeriod(id);
        CatalogDtos.AcademicPeriodResponse before = Mapper.toResponse(period);
        PeriodStatus nextStatus = request.active() ? PeriodStatus.ACTIVO : PeriodStatus.CERRADO;
        period.setStatus(nextStatus);
        CatalogDtos.AcademicPeriodResponse response = Mapper.toResponse(periodRepository.save(period));
        auditService.record(AuditAction.STATUS_CHANGE, "ACADEMIC_PERIOD", response.id(), "Periodo " + (request.active() ? "activado" : "desactivado") + ".", before, response);
        return response;
    }

    public void deletePeriod(Long id) {
        AcademicPeriod period = findPeriod(id);
        if (isPeriodInUse(period)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un periodo academico en uso; cambie su estado a cerrado.");
        }
        periodRepository.delete(period);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.SchoolResponse> listSchools() {
        return schoolRepository.findAll().stream()
                .sorted(Comparator.comparing(School::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    public CatalogDtos.SchoolResponse createSchool(CatalogDtos.SchoolRequest request) {
        String name = request.name().trim();
        if (schoolRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe una escuela con ese nombre.");
        }
        School school = new School();
        school.setName(name);
        school.setDescription(request.description());
        CatalogDtos.SchoolResponse response = Mapper.toResponse(schoolRepository.save(school));
        auditService.record(AuditAction.CREATE, "SCHOOL", response.id(), "Escuela creada: " + response.name(), null, response);
        return response;
    }

    public CatalogDtos.SchoolResponse updateSchool(Long id, CatalogDtos.SchoolRequest request) {
        School school = findSchool(id);
        CatalogDtos.SchoolResponse before = Mapper.toResponse(school);
        Optional<School> existing = schoolRepository.findByNameIgnoreCase(request.name().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe una escuela con ese nombre.");
        }
        school.setName(request.name().trim());
        school.setDescription(request.description());
        CatalogDtos.SchoolResponse response = Mapper.toResponse(schoolRepository.save(school));
        auditService.record(AuditAction.UPDATE, "SCHOOL", response.id(), "Escuela actualizada: " + response.name(), before, response);
        return response;
    }

    public void deleteSchool(Long id) {
        School school = findSchool(id);
        if (departmentRepository.existsBySchoolId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar una escuela con departamentos asociados.");
        }
        schoolRepository.delete(school);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    public CatalogDtos.DepartmentResponse createDepartment(CatalogDtos.DepartmentRequest request) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un departamento con ese nombre.");
        }
        Department department = new Department();
        department.setName(name);
        department.setDescription(request.description());
        department.setSchool(findSchool(request.schoolId()));
        department.setExternalDepartmentId(request.externalDepartmentId());
        CatalogDtos.DepartmentResponse response = Mapper.toResponse(departmentRepository.save(department));
        auditService.record(AuditAction.CREATE, "DEPARTMENT", response.id(), "Departamento creado: " + response.name(), null, response);
        return response;
    }

    public CatalogDtos.DepartmentResponse updateDepartment(Long id, CatalogDtos.DepartmentRequest request) {
        Department department = findDepartment(id);
        CatalogDtos.DepartmentResponse before = Mapper.toResponse(department);
        Optional<Department> existing = departmentRepository.findByNameIgnoreCase(request.name().trim());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un departamento con ese nombre.");
        }
        department.setName(request.name().trim());
        department.setDescription(request.description());
        department.setSchool(findSchool(request.schoolId()));
        department.setExternalDepartmentId(request.externalDepartmentId());
        CatalogDtos.DepartmentResponse response = Mapper.toResponse(departmentRepository.save(department));
        auditService.record(AuditAction.UPDATE, "DEPARTMENT", response.id(), "Departamento actualizado: " + response.name(), before, response);
        return response;
    }

    public void deleteDepartment(Long id) {
        Department department = findDepartment(id);
        if (objectiveRepository.existsByDepartmentId(id) || projectRepository.existsByDepartmentId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un departamento en uso.");
        }
        departmentRepository.delete(department);
    }

    private MeasurementUnit findUnit(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada."));
    }

    private AcademicPeriod findPeriod(Long id) {
        return periodRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Periodo academico no encontrado."));
    }

    private School findSchool(Long id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Escuela no encontrada."));
    }

    private Department findDepartment(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Departamento no encontrado."));
    }

    private void assertUnitNameIsAvailable(String name, Long currentId) {
        Optional<MeasurementUnit> existing = unitRepository.findByNameIgnoreCase(name.trim());
        if (existing.isPresent() && !existing.get().getId().equals(currentId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe una unidad de medida con ese nombre.");
        }
    }

    private void assertPeriodNameIsAvailable(String name, Long currentId) {
        Optional<AcademicPeriod> existing = periodRepository.findByNameIgnoreCase(name.trim());
        if (existing.isPresent() && !existing.get().getId().equals(currentId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Ya existe un periodo academico con ese nombre.");
        }
    }

    private void assertPeriodDatesAreValid(CatalogDtos.AcademicPeriodRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la fecha de inicio.");
        }
    }

    private boolean isUnitInUse(Long id) {
        return goalRepository.existsByMeasurementUnitId(id) || keyResultRepository.existsByMeasurementUnitId(id);
    }

    private boolean isPeriodInUse(AcademicPeriod period) {
        Long id = period.getId();
        String name = period.getName();
        return goalRepository.existsByPeriodsId(id)
                || objectiveRepository.existsByAcademicPeriodId(id)
                || projectRepository.existsByStartPeriodOrEndPeriod(name, name);
    }
}
