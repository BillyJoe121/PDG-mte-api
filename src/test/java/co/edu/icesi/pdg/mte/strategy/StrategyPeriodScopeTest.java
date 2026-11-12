package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StrategyPeriodScopeTest {
    private final AcademicPeriod period = TestFixtures.period(1L);

    @Test
    void resolveIgnoresBlankNamesAndTrimsKnownOnes() {
        AcademicPeriodRepository repository = mock(AcademicPeriodRepository.class);
        when(repository.findByNameIgnoreCase("2026-1")).thenReturn(Optional.of(period));

        assertThat(StrategyPeriodScope.resolve(null, repository)).isNull();
        assertThat(StrategyPeriodScope.resolve("  ", repository)).isNull();
        verifyNoInteractions(repository);
        assertThat(StrategyPeriodScope.resolve(" 2026-1 ", repository)).isSameAs(period);
        assertThat(StrategyPeriodScope.resolve("2030-1", repository)).isNull();
    }

    @Test
    void betsMatchWhenTheirDatesOverlapThePeriod() {
        StrategicBet bet = TestFixtures.strategicBet(1L);

        assertThat(StrategyPeriodScope.matches(bet, null)).isTrue();
        assertThat(StrategyPeriodScope.matches(bet, period)).isTrue();

        bet.setStartDate(LocalDate.of(2026, 7, 1));
        bet.setEndDate(LocalDate.of(2026, 12, 31));
        assertThat(StrategyPeriodScope.matches(bet, period)).isFalse();

        bet.setStartDate(LocalDate.of(2025, 1, 1));
        bet.setEndDate(LocalDate.of(2025, 12, 31));
        assertThat(StrategyPeriodScope.matches(bet, period)).isFalse();
    }

    @Test
    void goalsMatchByLinkedPeriodsOrElseByDates() {
        InstitutionalGoal goal = TestFixtures.goal(1L, TestFixtures.unit(1L));
        assertThat(StrategyPeriodScope.matches(goal, null)).isTrue();
        assertThat(StrategyPeriodScope.matches(goal, period)).isTrue();

        goal.setStartDate(LocalDate.of(2027, 1, 1));
        goal.setEndDate(LocalDate.of(2027, 12, 31));
        assertThat(StrategyPeriodScope.matches(goal, period)).isFalse();

        goal.setPeriods(Set.of(TestFixtures.period(2L)));
        assertThat(StrategyPeriodScope.matches(goal, period)).isFalse();
        goal.setPeriods(Set.of(TestFixtures.period(1L)));
        assertThat(StrategyPeriodScope.matches(goal, period)).isTrue();
    }

    @Test
    void keyResultsMatchOnlyTheirOwnPeriod() {
        KeyResult keyResult = TestFixtures.keyResult(1L, TestFixtures.unit(1L));

        assertThat(StrategyPeriodScope.matches(keyResult, null)).isTrue();
        keyResult.setAcademicPeriod(null);
        assertThat(StrategyPeriodScope.matches(keyResult, period)).isFalse();
        keyResult.setAcademicPeriod(TestFixtures.period(2L));
        assertThat(StrategyPeriodScope.matches(keyResult, period)).isFalse();
        keyResult.setAcademicPeriod(TestFixtures.period(1L));
        assertThat(StrategyPeriodScope.matches(keyResult, period)).isTrue();
    }
}
