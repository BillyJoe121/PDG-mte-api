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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void listsAndSummarizesAuditLogsWithValidDateRange() {
        AuditService service = new AuditService(repository, new ObjectMapper());
        AuditLog create = log(AuditAction.CREATE, "PROJECT", "1");
        AuditLog update = log(AuditAction.UPDATE, "PROJECT", "1");
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(create, update));

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T23:59:59Z");
        var logs = service.list(null, "project", "1", from, to);
        var summary = service.summary(null, "project", "1", from, to);

        assertThat(logs).hasSize(2);
        assertThat(summary.totalEvents()).isEqualTo(2);
        assertThat(summary.byAction()).extracting("key").contains("CREATE", "UPDATE");
        assertThat(summary.byEntityType()).extracting("key").contains("PROJECT");
    }

    @Test
    void returnsPagedAuditLogsWithMetadata() {
        AuditService service = new AuditService(repository, new ObjectMapper());
        AuditLog create = log(AuditAction.CREATE, "PROJECT", "1");
        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(create), PageRequest.of(0, 50), 75));

        var page = service.listPage(null, null, null, null, null, 0, 50);

        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.totalElements()).isEqualTo(75);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidDateRange() {
        AuditService service = new AuditService(repository, new ObjectMapper());

        assertThatThrownBy(() -> service.list(
                null,
                null,
                null,
                Instant.parse("2026-12-31T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        )).isInstanceOf(co.edu.icesi.pdg.mte.common.BusinessException.class)
                .hasMessageContaining("rango");
    }

    private AuditLog log(AuditAction action, String entityType, String entityId) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setSummary(action.name());
        return log;
    }
}
