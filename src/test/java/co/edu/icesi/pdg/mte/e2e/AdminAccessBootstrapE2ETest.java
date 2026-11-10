package co.edu.icesi.pdg.mte.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** The initial access link runs when the application is ready, outside any request transaction. */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "mte.auth.access-link-url=https://mte.example.edu/establecer-contrasena",
        "mte.auth.bootstrap-admin-link=true"
})
class AdminAccessBootstrapE2ETest {

    @Test
    void applicationStartsAndReportsTheInitialAccess(CapturedOutput output) {
        assertThat(output.getAll()).contains("Initial access");
        assertThat(output.getAll()).doesNotContain("Application run failed");
    }
}
