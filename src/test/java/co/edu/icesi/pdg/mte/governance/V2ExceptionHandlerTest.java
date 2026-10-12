package co.edu.icesi.pdg.mte.governance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
class V2ExceptionHandlerTest {
    @Test void unexpectedFailureUsesV2ContractWithoutLeakingDatabaseDetails()throws Exception{
        var service=mock(SchoolReportV2Service.class);when(service.get(7)).thenThrow(new IllegalStateException("private SQL detail and credential"));
        var mvc=MockMvcBuilders.standaloneSetup(new SchoolReportV2Controller(service)).setControllerAdvice(new V2ExceptionHandler()).build();
        var response=mvc.perform(get("/api/v2/school-final-reports/7")).andReturn().getResponse();var body=new ObjectMapper().readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(500);assertThat(body.size()).isEqualTo(2);assertThat(body.path("error").asText()).isEqualTo("INTERNAL_ERROR");assertThat(body.path("traceId").asText()).isNotBlank().isEqualTo(response.getHeader("X-Trace-Id"));assertThat(response.getContentAsString()).doesNotContain("private","SQL","credential");
    }
    @Test void malformedPathParameterUsesV2Contract()throws Exception{
        var mvc=MockMvcBuilders.standaloneSetup(new SchoolReportV2Controller(mock(SchoolReportV2Service.class))).setControllerAdvice(new V2ExceptionHandler()).build();
        var response=mvc.perform(get("/api/v2/school-final-reports/not-an-id")).andReturn().getResponse();var body=new ObjectMapper().readTree(response.getContentAsString());assertThat(response.getStatus()).isEqualTo(422);assertThat(body.path("error").asText()).isEqualTo("INVALID_REQUEST");assertThat(body.path("traceId").asText()).isEqualTo(response.getHeader("X-Trace-Id"));
    }
}
