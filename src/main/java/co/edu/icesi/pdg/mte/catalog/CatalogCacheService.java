package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.api.Mapper;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.common.CacheNames;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoal;
import co.edu.icesi.pdg.mte.strategy.InstitutionalGoalRepository;
import co.edu.icesi.pdg.mte.strategy.StrategicBet;
import co.edu.icesi.pdg.mte.strategy.StrategicBetRepository;
import co.edu.icesi.pdg.mte.strategy.WorldRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogCacheService {
    private static final StrategyDtos.ExecutionSummaryResponse EMPTY_EXECUTION_SUMMARY =
            new StrategyDtos.ExecutionSummaryResponse("", 0, 0, 0, 0, 0, 0, List.of());

    private final MeasurementUnitRepository unitRepository;
    private final AcademicPeriodRepository periodRepository;
    private final SchoolRepository schoolRepository;
    private final DepartmentRepository departmentRepository;
    private final WorldRepository worldRepository;
    private final StrategicBetRepository strategicBetRepository;
    private final InstitutionalGoalRepository goalRepository;

    public CatalogCacheService(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            SchoolRepository schoolRepository,
            DepartmentRepository departmentRepository,
            WorldRepository worldRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository
    ) {
        this.unitRepository = unitRepository;
        this.periodRepository = periodRepository;
        this.schoolRepository = schoolRepository;
        this.departmentRepository = departmentRepository;
        this.worldRepository = worldRepository;
        this.strategicBetRepository = strategicBetRepository;
        this.goalRepository = goalRepository;
    }

    @Cacheable(CacheNames.MEASUREMENT_UNITS)
    public List<CatalogDtos.MeasurementUnitResponse> listUnits() {
        return unitRepository.findAll().stream()
                .sorted(Comparator.comparing(MeasurementUnit::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    @Cacheable(CacheNames.ACADEMIC_PERIODS)
    public List<CatalogDtos.AcademicPeriodResponse> listPeriods() {
        return periodRepository.findAll().stream()
                .sorted(Comparator.comparing(AcademicPeriod::getStartDate).thenComparing(AcademicPeriod::getName))
                .map(Mapper::toResponse)
                .toList();
    }

    @Cacheable(CacheNames.SCHOOLS)
    public List<CatalogDtos.SchoolResponse> listSchools() {
        return schoolRepository.findAll().stream()
                .sorted(Comparator.comparing(School::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    @Cacheable(CacheNames.DEPARTMENTS)
    public List<CatalogDtos.DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    @Cacheable(CacheNames.CATALOG_BOOTSTRAP)
    public CatalogDtos.CatalogBootstrapResponse bootstrap() {
        return new CatalogDtos.CatalogBootstrapResponse(
                listPeriods(),
                listDepartments(),
                listUnits(),
                listSchools()
        );
    }

    @Cacheable(CacheNames.WORLDS)
    public List<StrategyDtos.WorldResponse> listWorlds() {
        return worldRepository.findAll().stream()
                .sorted(Comparator.comparing(world -> world.getName(), String.CASE_INSENSITIVE_ORDER))
                .map(Mapper::toResponse)
                .toList();
    }

    @Cacheable(CacheNames.STRATEGIC_BET_CATALOG)
    public List<StrategyDtos.StrategicBetResponse> strategicBetCatalog() {
        return strategicBetRepository.findAll().stream()
                .sorted(Comparator.comparing(StrategicBet::getName, String.CASE_INSENSITIVE_ORDER))
                .map(bet -> Mapper.toResponse(bet, EMPTY_EXECUTION_SUMMARY))
                .toList();
    }

    @Cacheable(CacheNames.GOAL_CATALOG)
    public List<StrategyDtos.GoalResponse> goalCatalog() {
        return goalRepository.findAll().stream()
                .sorted(Comparator.comparing(InstitutionalGoal::getName, String.CASE_INSENSITIVE_ORDER))
                .map(goal -> Mapper.toResponse(goal, EMPTY_EXECUTION_SUMMARY))
                .toList();
    }
}
