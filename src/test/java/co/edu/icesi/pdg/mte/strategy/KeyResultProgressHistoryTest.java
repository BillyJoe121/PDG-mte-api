package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyResultProgressHistoryTest extends StrategyServiceTestSupport {

    private void stubFindAndSave() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        lenient().when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static StrategyDtos.KeyResultProgressRequest request(String currentValue, String progressPercentage) {
        return new StrategyDtos.KeyResultProgressRequest(new BigDecimal(currentValue), new BigDecimal(progressPercentage));
    }

    private KeyResultProgressRecord savedRecord() {
        ArgumentCaptor<KeyResultProgressRecord> captor = ArgumentCaptor.forClass(KeyResultProgressRecord.class);
        verify(progressRecordRepository).save(captor.capture());
        return captor.getValue();
    }

    private void actAs(String professorName, String username, String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ExternalUserContext(7L, username, email, List.of("MANAGER"), List.of(), 7L, professorName, null),
                null, List.of()));
    }

    @Test
    void registeringProgressStoresPreviousAndNewValuesWithTheAuthorAndTheMoment() {
        stubFindAndSave();
        Instant before = Instant.now();

        keyResultService.updateProgress(1L, request("60", "60"));

        KeyResultProgressRecord record = savedRecord();
        assertThat(record.getKeyResultId()).isEqualTo(1L);
        assertThat(record.getPreviousValue()).isEqualByComparingTo("25");
        assertThat(record.getPreviousPercentage()).isEqualByComparingTo("25");
        assertThat(record.getCurrentValue()).isEqualByComparingTo("60");
        assertThat(record.getProgressPercentage()).isEqualByComparingTo("60");
        assertThat(record.getAuthorName()).isEqualTo("Usuario Demo");
        assertThat(record.getAuthorEmail()).isEqualTo("demo.admin@icesi.edu.co");
        assertThat(record.getRecordedAt()).isBetween(before, Instant.now());
    }

    @Test
    void theFirstRecordHasNoPreviousValues() {
        keyResult.setCurrentValue(null);
        keyResult.setProgressPercentage(null);
        stubFindAndSave();

        keyResultService.updateProgress(1L, request("10", "10"));

        KeyResultProgressRecord record = savedRecord();
        assertThat(record.getPreviousValue()).isNull();
        assertThat(record.getPreviousPercentage()).isNull();
        assertThat(record.getCurrentValue()).isEqualByComparingTo("10");
    }

    @Test
    void theAuthorFallsBackToTheUsernameWhenThereIsNoProfessorName() {
        actAs("  ", "maria.gestora", "maria@icesi.edu.co");
        stubFindAndSave();

        keyResultService.updateProgress(1L, request("30", "30"));

        assertThat(savedRecord().getAuthorName()).isEqualTo("maria.gestora");
        verify(progressRecordRepository).save(any());
    }

    @Test
    void progressWithoutAnAuthenticatedUserIsStillRecordedWithoutAuthor() {
        SecurityContextHolder.clearContext();
        stubFindAndSave();

        keyResultService.updateProgress(1L, request("30", "30"));

        KeyResultProgressRecord record = savedRecord();
        assertThat(record.getAuthorName()).isNull();
        assertThat(record.getAuthorEmail()).isNull();
    }

    @Test
    void rejectedProgressWritesNoRecord() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));

        assertThatThrownBy(() -> keyResultService.updateProgress(1L, request("30", "101")))
                .isInstanceOf(BusinessException.class);
        keyResult.setStatus(KeyResultStatus.CERRADO);
        assertThatThrownBy(() -> keyResultService.updateProgress(1L, request("30", "30")))
                .isInstanceOf(BusinessException.class);
        keyResult.setStatus(KeyResultStatus.ACTIVO);
        keyResult.setArchived(true);
        assertThatThrownBy(() -> keyResultService.updateProgress(1L, request("30", "30")))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(progressRecordRepository);
    }

    @Test
    void otherChangesNeverWriteProgressRecords() {
        stubFindAndSave();
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(0L);

        keyResultService.setActive(1L, false);
        keyResultService.setActive(1L, true);
        keyResultService.setStatus(1L, KeyResultStatus.CERRADO);

        verifyNoInteractions(progressRecordRepository);
    }

    @Test
    void listsTheRecordsOfAKeyResultAsReturnedByTheRepository() {
        KeyResultProgressRecord newer = record(12L, "Ana", "2026-03-02T10:00:00Z", null, "50");
        KeyResultProgressRecord older = record(11L, "Luis", "2026-03-01T10:00:00Z", "25", "30");
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(progressRecordRepository.findByKeyResultIdOrderByRecordedAtDescIdDesc(1L)).thenReturn(List.of(newer, older));

        var records = keyResultService.progressRecords(1L);

        assertThat(records).extracting("id").containsExactly(12L, 11L);
        assertThat(records.get(0).previousValue()).isNull();
        assertThat(records.get(0).authorName()).isEqualTo("Ana");
        assertThat(records.get(1).previousValue()).isEqualByComparingTo("25");
        assertThat(records.get(1).recordedAt()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
    }

    @Test
    void anArchivedKeyResultKeepsItsHistoryReadable() {
        keyResult.setArchived(true);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(progressRecordRepository.findByKeyResultIdOrderByRecordedAtDescIdDesc(1L)).thenReturn(List.of());

        assertThat(keyResultService.progressRecords(1L)).isEmpty();
    }

    @Test
    void historyOfAnUnknownKeyResultIsNotFound() {
        when(keyResultRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> keyResultService.progressRecords(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
        verify(progressRecordRepository, never()).findByKeyResultIdOrderByRecordedAtDescIdDesc(any());
    }

    private static KeyResultProgressRecord record(Long id, String author, String at, String previous, String current) {
        KeyResultProgressRecord record = new KeyResultProgressRecord();
        record.setId(id);
        record.setKeyResultId(1L);
        record.setPreviousValue(previous == null ? null : new BigDecimal(previous));
        record.setCurrentValue(new BigDecimal(current));
        record.setProgressPercentage(new BigDecimal(current));
        record.setAuthorName(author);
        record.setRecordedAt(Instant.parse(at));
        return record;
    }
}
