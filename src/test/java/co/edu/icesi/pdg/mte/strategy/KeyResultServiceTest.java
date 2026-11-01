package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnitType;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyResultServiceTest extends StrategyServiceTestSupport {

    private void stubCatalogs() {
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
    }

    private void stubSave() {
        when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> {
            KeyResult saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
    }

    @Test
    void createsKeyResultLinkedOnlyToGoals() {
        stubCatalogs();
        stubSave();
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        var response = keyResultService.create(keyResultRequest(List.of(1L, 1L), List.of()));

        assertThat(response.goals()).containsExactly(new StrategyDtos.StrategyLinkResponse(1L, "Meta"));
        assertThat(response.strategicBets()).isEmpty();
        assertThat(response.academicPeriodId()).isEqualTo(1L);
        assertThat(response.academicPeriodName()).isEqualTo("2026-1");
        assertThat(response.status()).isEqualTo(KeyResultStatus.BORRADOR);
        assertThat(response.currentValue()).isEqualByComparingTo("0");
        assertThat(response.progressPercentage()).isEqualByComparingTo("0");
        verify(auditService).record(eq(AuditAction.CREATE), eq("KEY_RESULT"), eq(10L), anyString(), isNull(), any());
    }

    @Test
    void createsKeyResultLinkedOnlyToStrategicBets() {
        stubCatalogs();
        stubSave();
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));

        var response = keyResultService.create(keyResultRequest(null, List.of(1L)));

        assertThat(response.goals()).isEmpty();
        assertThat(response.strategicBets()).containsExactly(new StrategyDtos.StrategyLinkResponse(1L, "Apuesta"));
    }

    @Test
    void createsKeyResultLinkedToGoalsAndStrategicBets() {
        stubCatalogs();
        stubSave();
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));

        var response = keyResultService.create(keyResultRequest(List.of(1L), List.of(1L)));

        assertThat(response.goals()).extracting(StrategyDtos.StrategyLinkResponse::id).containsExactly(1L);
        assertThat(response.strategicBets()).extracting(StrategyDtos.StrategyLinkResponse::id).containsExactly(1L);
        assertThat(response.progressPercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsKeyResultWithoutGoalsOrStrategicBets() {
        assertThatThrownBy(() -> keyResultService.create(keyResultRequest(List.of(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos a una Meta o a una Apuesta")
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownGoalOrStrategicBetWithNotFound() {
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());
        when(strategicBetRepository.findById(98L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keyResultService.create(keyResultRequest(List.of(99L), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Meta institucional no encontrada")
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> keyResultService.create(keyResultRequest(List.of(), List.of(98L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Apuesta estrategica no encontrada")
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void updateReplacesLinksAndKeepsRecordedProgress() {
        InstitutionalGoal otherGoal = TestFixtures.goal(2L, unit);
        otherGoal.setName("Otra meta");
        unit.setType(MeasurementUnitType.NUMERICA);
        stubCatalogs();
        stubSave();
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(goalRepository.findById(2L)).thenReturn(Optional.of(otherGoal));

        keyResult.setCurrentValue(BigDecimal.valueOf(42));
        keyResult.setProgressPercentage(BigDecimal.valueOf(17));

        var response = keyResultService.update(1L, keyResultRequest("KR actualizado", BigDecimal.valueOf(200), List.of(2L), List.of()));

        assertThat(response.name()).isEqualTo("KR actualizado");
        assertThat(response.goals()).extracting(StrategyDtos.StrategyLinkResponse::id).containsExactly(2L);
        assertThat(response.strategicBets()).isEmpty();
        assertThat(response.currentValue()).isEqualByComparingTo("42");
        assertThat(response.progressPercentage()).isEqualByComparingTo("17");
        verify(auditService).record(eq(AuditAction.UPDATE), eq("KEY_RESULT"), eq(1L), anyString(), any(), any());
    }

    @Test
    void updateRejectsUnknownKeyResult() {
        when(keyResultRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keyResultService.update(99L, keyResultRequest(List.of(1L), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Key Result no encontrado");
    }

    @Test
    void listFiltersByGoalStrategicBetAndPeriod() {
        StrategicBet otherBet = TestFixtures.strategicBet(2L);
        var otherPeriod = TestFixtures.period(2L);
        otherPeriod.setName("2026-2");
        KeyResult goalOnly = TestFixtures.keyResult(2L, unit, period, goal, null);
        KeyResult otherBetOtherPeriod = TestFixtures.keyResult(3L, unit, otherPeriod, null, otherBet);
        when(keyResultRepository.findAll()).thenReturn(List.of(otherBetOtherPeriod, goalOnly, keyResult));

        assertThat(keyResultService.list(null, null, null)).extracting(StrategyDtos.KeyResultResponse::id)
                .containsExactly(1L, 2L, 3L);
        assertThat(keyResultService.list(1L, null, null)).extracting(StrategyDtos.KeyResultResponse::id)
                .containsExactly(1L, 2L);
        assertThat(keyResultService.list(null, 2L, null)).extracting(StrategyDtos.KeyResultResponse::id)
                .containsExactly(3L);
        assertThat(keyResultService.list(null, null, 2L)).extracting(StrategyDtos.KeyResultResponse::id)
                .containsExactly(3L);
        assertThat(keyResultService.list(1L, 1L, 1L)).extracting(StrategyDtos.KeyResultResponse::id)
                .containsExactly(1L);
    }

    @Test
    void returnsKeyResultWithItsGoalsAndStrategicBets() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));

        var response = keyResultService.get(1L);

        assertThat(response.goals()).extracting(StrategyDtos.StrategyLinkResponse::name).containsExactly("Meta");
        assertThat(response.strategicBets()).extracting(StrategyDtos.StrategyLinkResponse::name).containsExactly("Apuesta");
    }

    @Test
    void changesStatusFromDraftToActiveToClosedAndRejectsOtherTransitions() {
        keyResult.setStatus(KeyResultStatus.BORRADOR);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(keyResultRepository.save(keyResult)).thenReturn(keyResult);

        assertThatThrownBy(() -> keyResultService.setStatus(1L, KeyResultStatus.CERRADO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BORRADOR -> CERRADO");
        assertThat(keyResultService.setStatus(1L, KeyResultStatus.ACTIVO).status()).isEqualTo(KeyResultStatus.ACTIVO);
        assertThatThrownBy(() -> keyResultService.setStatus(1L, KeyResultStatus.BORRADOR))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACTIVO -> BORRADOR");
        assertThat(keyResultService.setStatus(1L, KeyResultStatus.CERRADO).status()).isEqualTo(KeyResultStatus.CERRADO);
        assertThatThrownBy(() -> keyResultService.setStatus(1L, KeyResultStatus.ACTIVO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CERRADO -> ACTIVO");
        assertThat(keyResultService.setStatus(1L, KeyResultStatus.CERRADO).status()).isEqualTo(KeyResultStatus.CERRADO);
        verify(auditService, org.mockito.Mockito.times(2))
                .record(eq(AuditAction.STATUS_CHANGE), eq("KEY_RESULT"), eq(1L), anyString(), any(), any());
    }

    @Test
    void closedKeyResultRejectsOrdinaryChanges() {
        keyResult.setStatus(KeyResultStatus.CERRADO);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));

        assertThatThrownBy(() -> keyResultService.update(1L, keyResultRequest(List.of(1L), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cerrado");
        assertThatThrownBy(() -> keyResultService.updateProgress(1L, progressRequest("10", "10")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cerrado");
        assertThatThrownBy(() -> keyResultService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cerrado");
        verify(keyResultRepository, never()).save(any());
        verify(keyResultRepository, never()).delete(any());
    }

    @Test
    void deletesKeyResultWhenItHasNoActiveProjectLinks() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(0L);

        keyResultService.delete(1L);

        verify(keyResultRepository).delete(keyResult);
    }

    @Test
    void rejectsKeyResultDeletionWhenItHasActiveProjectLinks() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(1L);

        assertThatThrownBy(() -> keyResultService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("proyectos vinculados");
        verify(keyResultRepository, never()).delete(any());
    }

    @Test
    void recordsCurrentValueAndProgressExactlyAsSubmittedAndAuditsBothStates() {
        keyResult.setBaseValue(BigDecimal.TEN);
        keyResult.setTargetValue(BigDecimal.valueOf(30));
        keyResult.setCurrentValue(BigDecimal.TEN);
        keyResult.setProgressPercentage(BigDecimal.ZERO);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = keyResultService.updateProgress(1L, progressRequest("25", "40"));

        assertThat(response.currentValue()).isEqualByComparingTo("25");
        assertThat(response.progressPercentage()).isEqualByComparingTo("40");
        var before = org.mockito.ArgumentCaptor.forClass(StrategyDtos.KeyResultResponse.class);
        var after = org.mockito.ArgumentCaptor.forClass(StrategyDtos.KeyResultResponse.class);
        verify(auditService).record(eq(AuditAction.PROGRESS_REGISTERED), eq("KEY_RESULT"), eq(1L),
                contains("Avance de Key Result registrado"), before.capture(), after.capture());
        assertThat(before.getValue().currentValue()).isEqualByComparingTo("10");
        assertThat(before.getValue().progressPercentage()).isEqualByComparingTo("0");
        assertThat(after.getValue().currentValue()).isEqualByComparingTo("25");
        assertThat(after.getValue().progressPercentage()).isEqualByComparingTo("40");
    }

    @Test
    void rejectsProgressOutsideRangeOrMissingValues() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));

        assertThatThrownBy(() -> keyResultService.updateProgress(1L, progressRequest("25", "-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("entre 0 y 100");
        assertThatThrownBy(() -> keyResultService.updateProgress(1L, progressRequest("25", "100.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("entre 0 y 100");
        assertThatThrownBy(() -> keyResultService.updateProgress(1L, new StrategyDtos.KeyResultProgressRequest(BigDecimal.ONE, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> keyResultService.updateProgress(1L, new StrategyDtos.KeyResultProgressRequest(null, BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
        verify(keyResultRepository, never()).save(any());
    }

    private static StrategyDtos.KeyResultProgressRequest progressRequest(String currentValue, String progressPercentage) {
        return new StrategyDtos.KeyResultProgressRequest(new BigDecimal(currentValue), new BigDecimal(progressPercentage));
    }

    @Test
    void rejectsPercentageValuesOutsideRange() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> keyResultService.create(keyResultRequest("KR", BigDecimal.valueOf(120), List.of(1L), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("entre 0 y 100");
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidBooleanValues() {
        unit.setType(MeasurementUnitType.BOOLEANA);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> keyResultService.create(keyResultRequest("KR", BigDecimal.valueOf(2), List.of(1L), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0 o 1");
    }

    @Test
    void rejectsInactiveMeasurementUnit() {
        unit.setActive(false);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> keyResultService.create(keyResultRequest(List.of(1L), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inactiva");
    }
}
