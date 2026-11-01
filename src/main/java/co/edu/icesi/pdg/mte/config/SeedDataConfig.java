package co.edu.icesi.pdg.mte.config;

import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.people.*;
import co.edu.icesi.pdg.mte.project.*;
import co.edu.icesi.pdg.mte.strategy.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Configuration
public class SeedDataConfig {
    private static final Logger log = LoggerFactory.getLogger(SeedDataConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "mte.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedData(
            JdbcTemplate jdbcTemplate,
            MeasurementUnitRepository unitRepository,
            AcademicPeriodRepository periodRepository,
            SchoolRepository schoolRepository,
            DepartmentRepository departmentRepository,
            WorldRepository worldRepository,
            StrategicBetRepository strategicBetRepository,
            InstitutionalGoalRepository goalRepository,
            KeyResultRepository keyResultRepository,
            RoleRepository roleRepository,
            PositionRepository positionRepository,
            ProfessorRepository professorRepository,
            TeacherPositionRepository teacherPositionRepository,
            ProjectRepository projectRepository,
            ProjectTeacherRepository projectTeacherRepository,
            ProjectProgressEntryRepository progressRepository,
            ProjectKeyResultLinkRepository linkRepository
    ) {
        return args -> {
            log.info("Starting demo seed data refresh.");
            cleanDatabase(jdbcTemplate);

            SeedCatalogData.seedUnits(unitRepository);
            SeedCatalogData.seedPeriods(periodRepository);
            School school = SeedCatalogData.seedSchool(schoolRepository);
            SeedCatalogData.seedDepartments(departmentRepository, school);
            SeedCatalogData.seedWorlds(worldRepository);
            SeedCatalogData.seedRoles(roleRepository);
            SeedCatalogData.seedPositions(positionRepository);
            SeedCatalogData.seedProfessors(professorRepository, departmentRepository);
            SeedCatalogData.seedTeacherPositions(teacherPositionRepository, positionRepository, professorRepository);
            SeedStrategicHierarchyData.seedStrategicHierarchy(
                    unitRepository,
                    periodRepository,
                    worldRepository,
                    strategicBetRepository,
                    goalRepository,
                    keyResultRepository
            );
            SeedProjectData.seedProjects(
                    keyResultRepository,
                    departmentRepository,
                    professorRepository,
                    roleRepository,
                    projectRepository,
                    projectTeacherRepository,
                    progressRepository,
                    linkRepository,
                    jdbcTemplate
            );
            SeedExpandedPortfolioData.seedExpandedPortfolio(
                    unitRepository,
                    periodRepository,
                    departmentRepository,
                    strategicBetRepository,
                    goalRepository,
                    keyResultRepository,
                    professorRepository,
                    roleRepository,
                    projectRepository,
                    projectTeacherRepository,
                    progressRepository,
                    linkRepository
            );
            log.info("Demo seed data refresh completed.");
        };
    }

    private void cleanDatabase(JdbcTemplate jdbcTemplate) {
        String databaseName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName()
        );
        if (databaseName != null && databaseName.toLowerCase().contains("h2")) {
            cleanH2Database(jdbcTemplate);
            return;
        }
        jdbcTemplate.execute("TRUNCATE TABLE " + quotedTableList() + " RESTART IDENTITY CASCADE");
    }

    private void cleanH2Database(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            SEED_TABLES.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + quoteIdentifier(table)));
            IDENTITY_TABLES.forEach(table -> jdbcTemplate.execute(
                    "ALTER TABLE " + quoteIdentifier(table) + " ALTER COLUMN " + quoteIdentifier("id") + " RESTART WITH 1"
            ));
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private String quotedTableList() {
        return SEED_TABLES.stream()
                .map(this::quoteIdentifier)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    private static final List<String> SEED_TABLES = List.of(
            "audit_log",
            "project_teacher",
            "project_progress_entry",
            "project_key_result_link",
            "project_tutor",
            "project",
            "key_result_goal",
            "key_result_strategic_bet",
            "key_result",
            "goal_period",
            "goal",
            "strategic_bet",
            "teacher_position",
            "professor",
            "role",
            "position",
            "department",
            "school",
            "period",
            "unit_of_measure",
            "world"
    );

    private static final List<String> IDENTITY_TABLES = List.of(
            "audit_log",
            "project_progress_entry",
            "project_key_result_link",
            "project",
            "key_result",
            "goal",
            "strategic_bet",
            "professor",
            "role",
            "position",
            "department",
            "school",
            "period",
            "unit_of_measure",
            "world"
    );
}
