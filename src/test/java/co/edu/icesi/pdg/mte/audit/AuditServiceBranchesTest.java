package co.edu.icesi.pdg.mte.audit;

import co.edu.icesi.pdg.mte.api.dto.AuditDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceBranchesTest {

    @Mock
    private AuditLogRepository repository;

    @Test
    void eventsWithoutAnActorAreGroupedUnderTheSystemUser() {
        AuditLog anonymous = log(AuditAction.CREATE, null);
        AuditLog blank = log(AuditAction.UPDATE, "   ");
        AuditLog named = log(AuditAction.UPDATE, "admin");
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(anonymous, blank, named));

        var summary = service().summary(null, null, null, null, null, null);

        assertThat(summary.byActor()).containsExactly(
                new AuditDtos.AuditCountResponse("SISTEMA", 2),
                new AuditDtos.AuditCountResponse("admin", 1));
    }

    @Test
    void trendIsEmptyWhenNeitherTheRangeNorTheEventsCarryDates() {
        AuditLog undated = log(AuditAction.CREATE, "admin");
        ReflectionTestUtils.setField(undated, "createdAt", null);
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(undated));

        assertThat(service().summary(null, null, null, null, null, null).trend()).isEmpty();
        assertThat(service().summary(null, null, null, null, Instant.parse("2026-04-01T00:00:00Z"), null).trend()).isEmpty();
    }

    @Test
    void trendStartsAtTheFirstEventWhenOnlyAnUpperBoundIsGiven() {
        AuditLog event = log(AuditAction.CREATE, "admin");
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-04-01T10:00:00Z"));
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(event));

        var summary = service().summary(null, null, null, null, null, Instant.parse("2026-04-02T23:00:00Z"));

        assertThat(summary.trend()).containsExactly(
                new AuditDtos.AuditTrendPointResponse(LocalDate.parse("2026-04-01"), 1),
                new AuditDtos.AuditTrendPointResponse(LocalDate.parse("2026-04-02"), 0));
    }

    private AuditService service() {
        return new AuditService(repository, new ObjectMapper());
    }

    private static AuditLog log(AuditAction action, String actor) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType("PROJECT");
        log.setEntityId("1");
        log.setSummary(action.name());
        log.setActorUsername(actor);
        return log;
    }
}
