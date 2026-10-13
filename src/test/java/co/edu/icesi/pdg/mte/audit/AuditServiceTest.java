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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

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
        var logs = service.list(null, "project", "1", null, from, to);
        var summary = service.summary(null, "project", "1", null, from, to);

        assertThat(logs).hasSize(2);
        assertThat(summary.totalEvents()).isEqualTo(2);
        assertThat(summary.byAction()).extracting("key").contains("CREATE", "UPDATE");
        assertThat(summary.byEntityType()).extracting("key").contains("PROJECT");
    }

    @Test
    void summarizesActorsAndBuildsContinuousDailyTrend() {
        AuditService service = new AuditService(repository, new ObjectMapper());
        AuditLog first = log(AuditAction.CREATE, "KEY_RESULT", "1");
        first.setActorUsername("admin");
        ReflectionTestUtils.setField(first, "createdAt", Instant.parse("2026-04-01T10:00:00Z"));
        AuditLog second = log(AuditAction.UPDATE, "KEY_RESULT", "1");
        second.setActorUsername("admin");
        ReflectionTestUtils.setField(second, "createdAt", Instant.parse("2026-04-03T10:00:00Z"));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(second, first));

        var summary = service.summary(
                null, "KEY_RESULT", null, "admin",
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-03T23:59:59Z")
        );

        assertThat(summary.byActor()).containsExactly(new co.edu.icesi.pdg.mte.api.dto.AuditDtos.AuditCountResponse("admin", 2));
        assertThat(summary.trend()).containsExactly(
                new co.edu.icesi.pdg.mte.api.dto.AuditDtos.AuditTrendPointResponse(LocalDate.parse("2026-04-01"), 1),
                new co.edu.icesi.pdg.mte.api.dto.AuditDtos.AuditTrendPointResponse(LocalDate.parse("2026-04-02"), 0),
                new co.edu.icesi.pdg.mte.api.dto.AuditDtos.AuditTrendPointResponse(LocalDate.parse("2026-04-03"), 1)
        );
    }

    @Test
    void rejectsInvalidDateRange() {
        AuditService service = new AuditService(repository, new ObjectMapper());

        assertThatThrownBy(() -> service.list(
                null,
                null,
                null,
                null,
                Instant.parse("2026-12-31T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        )).isInstanceOf(co.edu.icesi.pdg.mte.common.BusinessException.class)
                .hasMessageContaining("rango");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appliesEveryRepositoryFilterAndSkipsBlankFilters() {
        AuditService service = new AuditService(repository, new ObjectMapper());
        ArgumentCaptor<Specification<AuditLog>> specifications = ArgumentCaptor.forClass(Specification.class);
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");

        service.list(AuditAction.UPDATE, " project ", " 7 ", " admin ", from, to);
        service.list(null, " ", "", " ", null, null);
        verify(repository, times(2)).findAll(specifications.capture(), any(Sort.class));

        Root root = mock(Root.class);
        CriteriaQuery query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        specifications.getAllValues().forEach(specification -> specification.toPredicate(root, query, builder));

        verify(builder, times(4)).equal(
                org.mockito.ArgumentMatchers.<jakarta.persistence.criteria.Expression<?>>isNull(),
                any(Object.class)
        );
        verify(builder).greaterThanOrEqualTo(any(), eq(from));
        verify(builder).lessThanOrEqualTo(any(), eq(to));
        verify(builder, times(2)).and(any(jakarta.persistence.criteria.Predicate[].class));
    }

    @Test
    void handlesEmptyAndImplicitAuditTrendBounds() {
        AuditService service = new AuditService(repository, new ObjectMapper());
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        assertThat(service.summary(null, null, null, null, null, null).trend()).isEmpty();

        var bounded = service.summary(null, null, null, null,
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-04-02T00:00:00Z"));
        assertThat(bounded.trend()).hasSize(2).allSatisfy(point -> assertThat(point.count()).isZero());

        AuditLog first = log(AuditAction.CREATE, "PROJECT", "1");
        ReflectionTestUtils.setField(first, "createdAt", Instant.parse("2026-04-03T10:00:00Z"));
        AuditLog withoutTimestamp = log(AuditAction.UPDATE, "PROJECT", "1");
        ReflectionTestUtils.setField(withoutTimestamp, "createdAt", null);
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(withoutTimestamp, first));
        var implicit = service.summary(null, null, null, null, null, null);
        assertThat(implicit.trend()).containsExactly(
                new co.edu.icesi.pdg.mte.api.dto.AuditDtos.AuditTrendPointResponse(LocalDate.parse("2026-04-03"), 1));
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
