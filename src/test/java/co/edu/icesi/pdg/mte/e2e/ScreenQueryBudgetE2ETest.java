package co.edu.icesi.pdg.mte.e2e;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Counts the SQL statements each endpoint behind a screen's first load runs on the seeded data.
 * A budget that grows with the number of rows (N+1) shows up here before it reaches users.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class ScreenQueryBudgetE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @ParameterizedTest(name = "{0} runs at most {1} statements")
    @CsvSource({
            "/api/v1/academic-periods, 11",
            "/api/v1/departments, 10",
            "/api/v1/measurement-units, 9",
            "/api/v1/dashboard/summary, 12",
            "/api/v1/dashboard/projects/by-status, 10",
            "/api/v1/dashboard/key-results/by-status, 11",
            "/api/v1/dashboard/strategic-bets/summary, 15",
            "/api/v1/dashboard/goals/summary, 15",
            "/api/v1/dashboard/departments/summary, 14",
            "/api/v1/strategic-hierarchy/tree, 22",
            "/api/v1/strategic-bets, 16",
            "/api/v1/goals, 18",
            "/api/v1/key-results, 13",
            "/api/v1/projects, 14",
            "/api/v1/reports, 16",
            "/api/v1/consistency/check, 12",
            "/api/v1/presentation, 20",
    })
    void screenEndpointStaysWithinItsStatementBudget(String path, long budget) throws Exception {
        statistics.clear();
        mockMvc.perform(get(path)).andExpect(status().isOk());
        long statements = statistics.getPrepareStatementCount();
        System.out.printf("QUERY-BUDGET %s -> %d statements%n", path, statements);
        assertThat(statements).isLessThanOrEqualTo(budget);
    }
}
