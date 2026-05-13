package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
public class SeedDataConfig {

    @Bean
    @ConditionalOnProperty(prefix = "mte.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedData(
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            DepartmentRepository departmentRepository
    ) {
        return args -> {
            seedUnits(unitRepository);
            seedPeriods(periodRepository);
            seedDepartments(departmentRepository);
        };
    }

    private void seedUnits(MeasurementUnitRepository repository) {
        createUnit(repository, "Porcentaje", MeasurementUnitType.PORCENTAJE, "Avance o cumplimiento expresado de 0 a 100.");
        createUnit(repository, "Numero", MeasurementUnitType.NUMERICA, "Conteo numerico simple.");
        createUnit(repository, "Si/No", MeasurementUnitType.BOOLEANA, "Indicador booleano de cumplimiento.");
    }

    private void createUnit(MeasurementUnitRepository repository, String name, MeasurementUnitType type, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        MeasurementUnit unit = new MeasurementUnit();
        unit.setName(name);
        unit.setType(type);
        unit.setDescription(description);
        repository.save(unit);
    }

    private void seedPeriods(AcademicPeriodRepository repository) {
        createPeriod(repository, "2026-1", LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 30), PeriodStatus.ACTIVO);
        createPeriod(repository, "2026-2", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 15), PeriodStatus.FUTURO);
    }

    private void createPeriod(
            AcademicPeriodRepository repository,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            PeriodStatus status
    ) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        AcademicPeriod period = new AcademicPeriod();
        period.setName(name);
        period.setStartDate(startDate);
        period.setEndDate(endDate);
        period.setStatus(status);
        repository.save(period);
    }

    private void seedDepartments(DepartmentRepository repository) {
        createDepartment(repository, "Departamento de TIC", "Departamento base para demo local.", 1L);
        createDepartment(repository, "Departamento de Diseno", "Departamento base para demo local.", 2L);
        createDepartment(repository, "Departamento de Ciencias Basicas", "Departamento base para demo local.", 3L);
    }

    private void createDepartment(DepartmentRepository repository, String name, String description, Long externalId) {
        if (repository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }
        Department department = new Department();
        department.setName(name);
        department.setDescription(description);
        department.setExternalDepartmentId(externalId);
        repository.save(department);
    }
}
