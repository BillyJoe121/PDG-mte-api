package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceBranchesTest extends StrategyServiceTestSupport {

    private void stubGoalPersistence() {
        lenient().when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        lenient().when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        lenient().when(keyResultRepository.findAll()).thenReturn(List.of());
        lenient().when(goalRepository.save(any(InstitutionalGoal.class))).thenAnswer(invocation -> {
            InstitutionalGoal saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(5L);
            }
            return saved;
        });
    }

    private static StrategyDtos.GoalRequest goalRequest(String indicator, Long worldId, LocalDate start, LocalDate end) {
        return new StrategyDtos.GoalRequest("Meta", "Descripcion", indicator, BigDecimal.TEN, 1L, worldId, start, end);
    }

    @Test
    void renamingAWorldToAFreeNameSucceeds() {
        when(worldRepository.findByNameIgnoreCase("Libre")).thenReturn(Optional.empty());
        when(worldRepository.save(any(World.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateWorld(1L, new StrategyDtos.WorldRequest(" Libre ", "Nueva"));

        assertThat(response.name()).isEqualTo("Libre");
        assertThat(response.description()).isEqualTo("Nueva");
    }

    @Test
    void goalsCanBeCreatedInAnExplicitWorldWithPartialDates() {
        stubGoalPersistence();

        var withWorld = service.createGoal(goalRequest("   ", 1L, null, null));
        assertThat(withWorld.worldId()).isEqualTo(1L);
        assertThat(withWorld.referenceIndicator()).isNull();

        var onlyStart = service.createGoal(goalRequest(null, null, LocalDate.of(2026, 3, 1), null));
        assertThat(onlyStart.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(onlyStart.endDate()).isEqualTo(LocalDate.of(2027, 3, 1));

        var onlyEnd = service.createGoal(goalRequest(null, null, null, LocalDate.now().plusYears(2)));
        assertThat(onlyEnd.endDate()).isEqualTo(LocalDate.now().plusYears(2));
    }

    @Test
    void goalDateRangeRejectsEqualOrInvertedDates() {
        stubGoalPersistence();
        LocalDate day = LocalDate.of(2026, 5, 1);

        assertThatThrownBy(() -> service.createGoal(goalRequest(null, null, day, day)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createGoal(goalRequest(null, null, day, day.minusDays(1))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updatingAGoalKeepsStoredDatesAndWorldWhenOmitted() {
        stubGoalPersistence();
        goal.setReferenceIndicator("Anterior");

        var kept = service.updateGoal(1L, goalRequest("  ", null, null, null));
        assertThat(kept.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(kept.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(kept.worldId()).isEqualTo(1L);
        assertThat(kept.referenceIndicator()).isNull();

        var moved = service.updateGoal(1L, goalRequest("Nuevo", 1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 11, 1)));
        assertThat(moved.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(moved.referenceIndicator()).isEqualTo("Nuevo");

        // Only one of the two dates is supplied: the stored counterpart completes the range.
        assertThatThrownBy(() -> service.updateGoal(1L, goalRequest(null, null, LocalDate.of(2027, 1, 1), null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void betsCanBeCreatedInAnExplicitWorld() {
        when(strategicBetRepository.save(any(StrategicBet.class))).thenAnswer(invocation -> {
            StrategicBet saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        var response = service.createStrategicBet(new StrategyDtos.StrategicBetRequest(
                "Con mundo", "Desc", 1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));

        assertThat(response.worldId()).isEqualTo(1L);
        assertThat(response.id()).isEqualTo(8L);
    }
}
