package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyResultBranchesTest extends StrategyServiceTestSupport {

    @Test
    void periodFilterExcludesKeyResultsWithoutPeriodOrWithAnotherOne() {
        KeyResult withoutPeriod = TestFixtures.keyResult(3L, unit);
        withoutPeriod.setAcademicPeriod(null);
        KeyResult otherPeriod = TestFixtures.keyResult(2L, unit, TestFixtures.period(2L), null, null);
        when(keyResultRepository.findAll()).thenReturn(List.of(withoutPeriod, otherPeriod, keyResult));

        var inPeriod = keyResultService.list(null, null, 1L, false);
        var everything = keyResultService.list(null, null, null, false);

        assertThat(inPeriod).extracting(StrategyDtos.KeyResultResponse::id).containsExactly(1L);
        assertThat(everything).extracting(StrategyDtos.KeyResultResponse::id).containsExactly(1L, 2L, 3L);
        assertThat(everything.get(2).academicPeriodId()).isNull();
        assertThat(everything.get(2).academicPeriodName()).isNull();
    }

    @Test
    void progressAuthorIgnoresPrincipalsThatAreNotExternalUsers() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-user", null, List.of()));

        KeyResultProgressRecord record = registerProgress();

        assertThat(record.getAuthorName()).isNull();
        assertThat(record.getAuthorEmail()).isNull();
    }

    @Test
    void progressAuthorUsesTheUsernameWhenTheProfessorNameIsMissing() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ExternalUserContext(7L, "maria.gestora", "maria@icesi.edu.co", List.of("MANAGER"), List.of(), 7L, null, null),
                null, List.of()));

        KeyResultProgressRecord record = registerProgress();

        assertThat(record.getAuthorName()).isEqualTo("maria.gestora");
        assertThat(record.getAuthorEmail()).isEqualTo("maria@icesi.edu.co");
    }

    private KeyResultProgressRecord registerProgress() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        keyResultService.updateProgress(1L, new StrategyDtos.KeyResultProgressRequest(
                BigDecimal.valueOf(40), BigDecimal.valueOf(40)));

        ArgumentCaptor<KeyResultProgressRecord> captor = ArgumentCaptor.forClass(KeyResultProgressRecord.class);
        verify(progressRecordRepository).save(captor.capture());
        return captor.getValue();
    }
}
