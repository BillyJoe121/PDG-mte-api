package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyResultArchiveTest extends StrategyServiceTestSupport {

    private void stubFind() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
    }

    private void stubSave() {
        lenient().when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void archivedKeyResult() {
        keyResult.setArchived(true);
        keyResult.setArchivedAt(Instant.parse("2026-03-01T10:00:00Z"));
    }

    @Test
    void archivingMarksTheKeyResultStampsTheDateAndAuditsTheChange() {
        stubFind();
        stubSave();
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(0L);
        Instant before = Instant.now();

        var response = keyResultService.setActive(1L, false);

        assertThat(response.archived()).isTrue();
        assertThat(response.archivedAt()).isBetween(before, Instant.now());
        assertThat(keyResult.isArchived()).isTrue();
        verify(keyResultRepository).save(keyResult);
        verify(auditService).record(eq(AuditAction.STATUS_CHANGE), eq("KEY_RESULT"), eq(1L),
                eq("Key Result archivado."), any(), any());
    }

    @Test
    void restoringClearsTheArchiveDateAndAuditsTheChange() {
        archivedKeyResult();
        stubFind();
        stubSave();

        var response = keyResultService.setActive(1L, true);

        assertThat(response.archived()).isFalse();
        assertThat(response.archivedAt()).isNull();
        assertThat(keyResult.getArchivedAt()).isNull();
        verify(auditService).record(eq(AuditAction.STATUS_CHANGE), eq("KEY_RESULT"), eq(1L),
                eq("Key Result restaurado."), any(), any());
    }

    @Test
    void archivingAClosedKeyResultIsRejected() {
        keyResult.setStatus(KeyResultStatus.CERRADO);
        stubFind();

        assertThatThrownBy(() -> keyResultService.setActive(1L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cerrado")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        assertThat(keyResult.isArchived()).isFalse();
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void archivingAKeyResultWithActiveLinksIsRejected() {
        stubFind();
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(2L);

        assertThatThrownBy(() -> keyResultService.setActive(1L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("proyectos vinculados")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        assertThat(keyResult.isArchived()).isFalse();
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void repeatingTheCurrentStateChangesNothingAndWritesNoAudit() {
        stubFind();
        archivedKeyResult();
        Instant stamp = keyResult.getArchivedAt();

        assertThat(keyResultService.setActive(1L, false).archivedAt()).isEqualTo(stamp);
        keyResult.setArchived(false);
        keyResult.setArchivedAt(null);
        assertThat(keyResultService.setActive(1L, true).archived()).isFalse();

        verify(keyResultRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownKeyResultIsNotFound() {
        when(keyResultRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keyResultService.setActive(99L, false))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listHidesArchivedKeyResultsUnlessAskedToIncludeThem() {
        KeyResult archived = TestFixtures.keyResult(2L, unit, period, goal, bet);
        archived.setArchived(true);
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, archived));

        assertThat(keyResultService.list(null, null, null, false)).extracting("id").containsExactly(1L);
        assertThat(keyResultService.list(null, null, null, true)).extracting("id").containsExactly(1L, 2L);
        assertThat(keyResultService.list(null, null, null, true).get(1).archived()).isTrue();
    }

    @Test
    void anArchivedKeyResultRejectsEditsAndProgressWithoutSaving() {
        archivedKeyResult();
        stubFind();

        assertThatThrownBy(() -> keyResultService.update(1L, keyResultRequest(List.of(1L), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("archivado")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> keyResultService.updateProgress(1L,
                new co.edu.icesi.pdg.mte.api.dto.StrategyDtos.KeyResultProgressRequest(
                        BigDecimal.TEN, BigDecimal.TEN)))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void anArchivedKeyResultStaysReadable() {
        archivedKeyResult();
        stubFind();

        var response = keyResultService.get(1L);

        assertThat(response.archived()).isTrue();
        assertThat(response.archivedAt()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
    }

    @Test
    void executionSummariesIgnoreArchivedKeyResults() {
        KeyResult archived = TestFixtures.keyResult(2L, unit, period, goal, bet);
        archived.setArchived(true);
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(keyResultRepository.findAll()).thenReturn(List.of(keyResult, archived));
        lenient().when(linkRepository.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of());

        assertThat(service.getStrategicBet(1L, null).executionSummary().inProgressKeyResults()).isEqualTo(1);
    }
}
