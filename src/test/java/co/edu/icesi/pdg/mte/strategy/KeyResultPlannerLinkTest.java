package co.edu.icesi.pdg.mte.strategy;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyResultPlannerLinkTest extends StrategyServiceTestSupport {
    private static final String PLAN = "https://tasks.office.com/icesi.edu.co/Home/PlanViews/abc123";

    private void stubCatalogs() {
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
    }

    private void stubSave() {
        lenient().when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> {
            KeyResult saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
    }

    @Test
    void createStoresTheLinkAndTheResponseExposesIt() {
        stubCatalogs();
        stubSave();

        var response = keyResultService.create(
                keyResultRequest("KR", BigDecimal.valueOf(100), List.of(1L), List.of(), "  " + PLAN + "  "));

        assertThat(response.plannerUrl()).isEqualTo(PLAN);
    }

    @Test
    void createWithoutLinkLeavesItEmpty() {
        stubCatalogs();
        stubSave();

        assertThat(keyResultService.create(keyResultRequest(List.of(1L), List.of())).plannerUrl()).isNull();
    }

    @Test
    void updateReplacesAndClearsTheLink() {
        stubCatalogs();
        stubSave();
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));

        var replaced = keyResultService.update(1L,
                keyResultRequest("KR", BigDecimal.valueOf(100), List.of(1L), List.of(), PLAN));
        var cleared = keyResultService.update(1L,
                keyResultRequest("KR", BigDecimal.valueOf(100), List.of(1L), List.of(), "   "));

        assertThat(replaced.plannerUrl()).isEqualTo(PLAN);
        assertThat(cleared.plannerUrl()).isNull();
    }

    @Test
    void createRejectsAnInvalidLinkWithBadRequestAndStoresNothing() {
        assertThatThrownBy(() -> keyResultService.create(keyResultRequest(
                "KR", BigDecimal.valueOf(100), List.of(1L), List.of(), "http://tasks.office.com/plan")))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("El enlace de Planner no es valido")
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(keyResultRepository, never()).save(any());
    }

    @Test
    void updateRejectsAnInvalidLinkAndKeepsTheStoredOne() {
        keyResult.setPlannerUrl(PLAN);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));

        assertThatThrownBy(() -> keyResultService.update(1L, keyResultRequest(
                "KR", BigDecimal.valueOf(100), List.of(1L), List.of(), "https://evil.example/plan")))
                .isInstanceOf(BusinessException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(keyResultRepository, never()).save(any());
    }
}
