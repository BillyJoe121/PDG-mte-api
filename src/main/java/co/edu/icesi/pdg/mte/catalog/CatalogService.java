package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.KeyResultRepository;
import co.edu.icesi.pdg.mte.strategy.ObjectiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CatalogService {

    private final MeasurementUnitRepository unitRepository;
    private final AcademicPeriodRepository periodRepository;
    private final DepartmentRepository departmentRepository;
    private final InstitutionalGoalRepository goalRepository;
    private final KeyResultRepository keyResultRepository;
    private final ObjectiveRepository objectiveRepository;

    public CatalogService(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            DepartmentRepository departmentRepository,
            InstitutionalGoalRepository goalRepository,
            KeyResultRepository keyResultRepository,
            ObjectiveRepository objectiveRepository
    ) {
        this.unitRepository = unitRepository;
        this.periodRepository = periodRepository;
        this.departmentRepository = departmentRepository;
        this.goalRepository = goalRepository;
        this.keyResultRepository = keyResultRepository;
        this.objectiveRepository = objectiveRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.MeasurementUnitResponse> listUnits() {
        return unitRepository.findAll().stream().map(Mapper::toResponse).toList();
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
        return Mapper.toResponse(unitRepository.save(unit));
    }

    public CatalogDtos.MeasurementUnitResponse updateUnit(Long id, CatalogDtos.MeasurementUnitRequest request) {
        MeasurementUnit unit = findUnit(id);
        assertUnitNameIsAvailable(request.name(), id);
        unit.setName(request.name().trim());
        unit.setType(request.type());
        unit.setDescription(request.description());
        return Mapper.toResponse(unitRepository.save(unit));
    }

    public CatalogDtos.MeasurementUnitResponse updateUnitActive(Long id, CatalogDtos.MeasurementUnitActiveRequest request) {
        MeasurementUnit unit = findUnit(id);
        unit.setActive(request.active());
        return Mapper.toResponse(unitRepository.save(unit));
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
        return periodRepository.findAll().stream().map(Mapper::toResponse).toList();
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
        return Mapper.toResponse(periodRepository.save(period));
    }

    public CatalogDtos.AcademicPeriodResponse updatePeriod(Long id, CatalogDtos.AcademicPeriodRequest request) {
        AcademicPeriod period = findPeriod(id);
        assertPeriodDatesAreValid(request);
        assertPeriodNameIsAvailable(request.name(), id);
        period.setName(request.name().trim());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setStatus(request.status());
        return Mapper.toResponse(periodRepository.save(period));
    }

    public CatalogDtos.AcademicPeriodResponse updatePeriodStatus(Long id, CatalogDtos.AcademicPeriodStatusRequest request) {
        AcademicPeriod period = findPeriod(id);
        period.setStatus(request.status());
        return Mapper.toResponse(periodRepository.save(period));
    }

    public void deletePeriod(Long id) {
        AcademicPeriod period = findPeriod(id);
        if (isPeriodInUse(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "No se puede eliminar un periodo academico en uso; cambie su estado a cerrado.");
        }
        periodRepository.delete(period);
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream().map(Mapper::toResponse).toList();
    }

    private MeasurementUnit findUnit(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada."));
    }

    private AcademicPeriod findPeriod(Long id) {
        return periodRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Periodo academico no encontrado."));
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

    private boolean isPeriodInUse(Long id) {
        return goalRepository.existsByPeriodsId(id) || objectiveRepository.existsByAcademicPeriodId(id);
    }
}
