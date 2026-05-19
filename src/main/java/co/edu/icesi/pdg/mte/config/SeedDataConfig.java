package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.people.*;
import co.edu.icesi.pdg.mte.strategy.World;
import co.edu.icesi.pdg.mte.strategy.WorldRepository;
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
            SchoolRepository schoolRepository,
            DepartmentRepository departmentRepository,
            WorldRepository worldRepository,
            RoleRepository roleRepository,
            PositionRepository positionRepository,
            ProfessorRepository professorRepository
    ) {
        return args -> {
            seedUnits(unitRepository);
            seedPeriods(periodRepository);
            School school = seedSchool(schoolRepository);
            seedDepartments(departmentRepository, school);
            seedWorlds(worldRepository);
            seedRoles(roleRepository);
            seedPositions(positionRepository);
            seedProfessors(professorRepository, departmentRepository);
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
        createPeriod(repository, "2026-2", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 15), PeriodStatus.PLANIFICACION);
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

    private School seedSchool(SchoolRepository repository) {
        return repository.findByNameIgnoreCase("Escuela TDI").orElseGet(() -> {
            School school = new School();
            school.setName("Escuela TDI");
            school.setDescription("Escuela base para demo local.");
            return repository.save(school);
        });
    }

    private void seedDepartments(DepartmentRepository repository, School school) {
        createDepartment(repository, "Departamento de TIC", "Departamento base para demo local.", 1L, school);
        createDepartment(repository, "Departamento de Diseno", "Departamento base para demo local.", 2L, school);
        createDepartment(repository, "Departamento de Ciencias Basicas", "Departamento base para demo local.", 3L, school);
    }

    private void createDepartment(DepartmentRepository repository, String name, String description, Long externalId, School school) {
        if (repository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }
        Department department = new Department();
        department.setName(name);
        department.setDescription(description);
        department.setExternalDepartmentId(externalId);
        department.setSchool(school);
        repository.save(department);
    }

    private void seedWorlds(WorldRepository repository) {
        if (repository.existsByNameIgnoreCase("Mundo Institucional")) {
            return;
        }
        World world = new World();
        world.setName("Mundo Institucional");
        world.setDescription("Agrupacion estrategica base para demo local.");
        repository.save(world);
    }

    private void seedRoles(RoleRepository repository) {
        createRole(repository, "Lider", "Responsable principal de un proyecto.");
        createRole(repository, "Colaborador", "Participante del proyecto.");
    }

    private void createRole(RoleRepository repository, String name, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        repository.save(role);
    }

    private void seedPositions(PositionRepository repository) {
        createPosition(repository, "Profesor", "Cargo docente base.");
        createPosition(repository, "Director de Departamento", "Cargo directivo de departamento.");
    }

    private void createPosition(PositionRepository repository, String name, String description) {
        if (repository.existsByNameIgnoreCase(name)) {
            return;
        }
        Position position = new Position();
        position.setName(name);
        position.setDescription(description);
        repository.save(position);
    }

    private void seedProfessors(ProfessorRepository repository, DepartmentRepository departmentRepository) {
        if (repository.existsByEmailIgnoreCase("demo.profesor@icesi.edu.co")) {
            return;
        }
        departmentRepository.findByNameIgnoreCase("Departamento de TIC").ifPresent(department -> {
            Professor professor = new Professor();
            professor.setName("Profesor Demo");
            professor.setEmail("demo.profesor@icesi.edu.co");
            professor.setDepartment(department);
            repository.save(professor);
        });
    }
}
