package co.edu.icesi.pdg.mte.presentation;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.PeriodStatus;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.strategy.StrategicStatus;
import co.edu.icesi.pdg.mte.strategy.StrategyService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresentationServiceTest {
    @Test
    void buildsCoverStrategicBetAndClosingSlides() {
        StrategyService strategyService = mock(StrategyService.class);
        AcademicPeriodRepository periodRepository = mock(AcademicPeriodRepository.class);
        var executionSummary = new StrategyDtos.ExecutionSummaryResponse(
                "1 objetivo completo",
                1,
                0,
                1,
                0,
                1,
                0,
                List.of()
        );
        when(strategyService.listStrategicBets("2026-1")).thenReturn(List.of(
                new StrategyDtos.StrategicBetResponse(
                        1L,
                        "Apuesta",
                        "Descripcion",
                        null,
                        null,
                        StrategicStatus.ACTIVA,
                        Instant.now(),
                        executionSummary
                )
        ));

        var response = new PresentationService(strategyService, periodRepository).presentation("2026-1");

        assertThat(response.period()).isEqualTo("2026-1");
        assertThat(response.controls().fullscreenEnabled()).isTrue();
        assertThat(response.controls().keyboardNavigationEnabled()).isTrue();
        assertThat(response.controls().nextKeys()).contains("ArrowRight", "Space");
        assertThat(response.controls().previousKeys()).contains("ArrowLeft");
        assertThat(response.controls().exitKeys()).contains("Escape");
        assertThat(response.slides()).hasSize(3);
        assertThat(response.slides().stream().map(slide -> slide.type())).containsExactly("COVER", "STRATEGIC_BET", "CLOSING");
        assertThat(response.slides().get(0).content()).containsEntry("institution", "Escuela TDI");
        assertThat(response.slides().get(0).content()).containsEntry("completedObjectives", 1);
        assertThat(response.slides().get(1).content()).containsKey("executionSummary");
        assertThat(response.slides().get(2).content()).containsEntry("summaryText", "1 objetivos completos, 0 en desarrollo. 1 KPIs completos, 0 en desarrollo. 1 proyectos completados, 0 en desarrollo.");
    }

    @Test
    void defaultsBlankPeriodToActiveAcademicPeriod() {
        StrategyService strategyService = mock(StrategyService.class);
        AcademicPeriodRepository periodRepository = mock(AcademicPeriodRepository.class);
        when(periodRepository.findFirstByStatusOrderByStartDateDesc(PeriodStatus.ACTIVO))
                .thenReturn(Optional.of(TestFixtures.period(1L)));
        when(strategyService.listStrategicBets("2026-1")).thenReturn(List.of());

        var response = new PresentationService(strategyService, periodRepository).presentation(" ");

        assertThat(response.period()).isEqualTo("2026-1");
        assertThat(response.slides().get(0).subtitle()).isEqualTo("Periodo 2026-1");
    }

    @Test
    void rejectsMalformedPeriod() {
        StrategyService strategyService = mock(StrategyService.class);
        AcademicPeriodRepository periodRepository = mock(AcademicPeriodRepository.class);

        assertThatThrownBy(() -> new PresentationService(strategyService, periodRepository).presentation("2026-X"))
                .isInstanceOf(BusinessException.class);
    }
}
