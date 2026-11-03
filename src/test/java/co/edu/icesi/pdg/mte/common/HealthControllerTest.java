package co.edu.icesi.pdg.mte.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void reportsUpWhenDatabaseIsReachable() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.service").value("pdg-mte-api"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void reportsDownWithoutDetailsWhenConnectionFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection())
                .thenThrow(new SQLException("Connection refused: db.internal.example:5432 user=secret"));

        ResponseEntity<Map<String, Object>> response = new HealthController(dataSource).health();

        assertDown(response);
        assertThat(response.getBody().toString()).doesNotContain("refused", "db.internal", "secret");
    }

    @Test
    void reportsDownAndClosesConnectionWhenValidationFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = new HealthController(dataSource).health();

        assertDown(response);
        verify(connection).close();
    }

    private static void assertDown(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .containsEntry("status", "DOWN")
                .containsEntry("database", "DOWN")
                .containsEntry("service", "pdg-mte-api")
                .containsKey("timestamp")
                .hasSize(4);
    }
}
