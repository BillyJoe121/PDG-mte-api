package co.edu.icesi.pdg.mte.audit;

import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuditLogWithActorAndSerializedSnapshots() {
        AuditService service = new AuditService(repository, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ExternalUserContext.mock(),
                null,
                List.of()
        ));
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        service.record(AuditAction.CREATE, "PROJECT", 1L, "Proyecto creado", null, Map.of("name", "Proyecto"));

        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(log.getEntityType()).isEqualTo("PROJECT");
        assertThat(log.getEntityId()).isEqualTo("1");
        assertThat(log.getActorUsername()).isEqualTo("demo.admin");
        assertThat(log.getActorRoles()).contains("ADMIN");
        assertThat(log.getBeforeSnapshot()).isNull();
        assertThat(log.getAfterSnapshot()).contains("Proyecto");
    }

    @Test
    void recordsAuditLogWithoutActorAndFallsBackWhenSnapshotCannotBeSerialized() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("fallo") {
        });
        AuditService service = new AuditService(repository, mapper);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        service.record(AuditAction.UPDATE, "PROJECT", "abc", "Proyecto actualizado", new Object(), new Object());

        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertThat(log.getActorUsername()).isNull();
        assertThat(log.getBeforeSnapshot()).contains("@");
        assertThat(log.getAfterSnapshot()).contains("@");
    }
}
