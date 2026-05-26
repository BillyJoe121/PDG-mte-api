package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import org.springframework.test.util.ReflectionTestUtils;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.PeriodStatus;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyServiceCatalogTest extends StrategyServiceTestSupport {
    @Test
    void createsStrategicBetWhenValid() {
        when(strategicBetRepository.existsByNameIgnoreCase("Apuesta nueva")).thenReturn(false);
        when(strategicBetRepository.save(any(StrategicBet.class))).thenAnswer(invocation -> {
            StrategicBet saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        var response = service.createStrategicBet(new StrategyDtos.StrategicBetRequest(
                "Apuesta nueva",
                "Descripcion",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        ));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.status()).isEqualTo(StrategicStatus.ACTIVA);
    }

    @Test
    void rejectsStrategicBetWhenNameIsDuplicated() {
        when(strategicBetRepository.existsByNameIgnoreCase("Apuesta")).thenReturn(true);

        assertThatThrownBy(() -> service.createStrategicBet(new StrategyDtos.StrategicBetRequest(
                "Apuesta",
                "Descripcion",
                null,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void rejectsStrategicBetWhenDateRangeIsInvalid() {
        assertThatThrownBy(() -> service.createStrategicBet(new StrategyDtos.StrategicBetRequest(
                "Apuesta",
                "Descripcion",
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1)
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de cierre");
    }

    @Test
    void updatesStrategicBetWhenValid() {
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(strategicBetRepository.findByNameIgnoreCase("Apuesta editada")).thenReturn(Optional.empty());
        when(strategicBetRepository.save(any(StrategicBet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectiveRepository.findByStrategicBetIdIn(any())).thenReturn(List.of());

        var response = service.updateStrategicBet(1L, new StrategyDtos.StrategicBetRequest(
                "Apuesta editada",
                "Descripcion editada",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 12, 1)
        ));

        assertThat(response.name()).isEqualTo("Apuesta editada");
        assertThat(response.description()).isEqualTo("Descripcion editada");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        verify(auditService).record(eq(co.edu.icesi.pdg.mte.audit.AuditAction.UPDATE), eq("STRATEGIC_BET"), eq(1L), contains("actualizada"), any(), any());
    }

    @Test
    void createsGoalAndAttachesAndDetachesPeriods() {
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(goalRepository.save(any(InstitutionalGoal.class))).thenAnswer(invocation -> {
            InstitutionalGoal saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));

        var created = service.createGoal(new StrategyDtos.GoalRequest(
                "Meta",
                "Descripcion",
                "Indicador",
                BigDecimal.TEN,
                1L,
                null,
                null
        ));
        var attached = service.attachGoalPeriod(1L, 1L);
        var detached = service.detachGoalPeriod(1L, 1L);

        assertThat(created.measurementUnitId()).isEqualTo(1L);
        assertThat(attached.periods()).hasSize(1);
        assertThat(detached.periods()).isEmpty();
    }

    @Test
    void updatesGoalWhenValid() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(goalRepository.save(any(InstitutionalGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectiveRepository.findByGoalIdIn(any())).thenReturn(List.of());

        var response = service.updateGoal(1L, new StrategyDtos.GoalRequest(
                "Meta editada",
                "Descripcion editada",
                "Indicador editado",
                BigDecimal.valueOf(90),
                1L,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 12, 1)
        ));

        assertThat(response.name()).isEqualTo("Meta editada");
        assertThat(response.referenceIndicator()).isEqualTo("Indicador editado");
        assertThat(response.expectedValue()).isEqualByComparingTo("90");
        verify(auditService).record(eq(co.edu.icesi.pdg.mte.audit.AuditAction.UPDATE), eq("GOAL"), eq(1L), contains("actualizada"), any(), any());
    }

    @Test
    void rejectsGoalWhenUnitDoesNotExist() {
        when(unitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGoal(new StrategyDtos.GoalRequest(
                "Meta",
                "Descripcion",
                null,
                BigDecimal.ONE,
                99L,
                null,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unidad");
    }

    @Test
    void rejectsGoalWhenDateRangeIsInvalid() {
        assertThatThrownBy(() -> service.createGoal(new StrategyDtos.GoalRequest(
                "Meta",
                "Descripcion",
                null,
                BigDecimal.ONE,
                1L,
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1)
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de cierre");
    }

    @Test
    void createsObjectiveWithKeyResultAndCurrentUserContext() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(invocation -> {
            Objective saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var response = service.createObjective(objectiveRequest(1L, 1L, 1L, 1L, 1L));

        assertThat(response.keyResults()).hasSize(1);
        assertThat(response.completionPercentage()).isEqualByComparingTo("0.00");
        verify(objectiveRepository).save(argThat(saved -> saved.getCreatedByExternalUserId().equals(1L)));
    }

    @Test
    void rejectsObjectiveWhenDepartmentIsMissing() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createObjective(objectiveRequest(99L, 1L, 1L, 1L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Departamento");
    }

    @Test
    void updatesKeyResultAndRecalculatesProgress() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateKeyResult(1L, new StrategyDtos.KeyResultRequest(
                "KR actualizado",
                "KR actualizado",
                "Metric",
                BigDecimal.ZERO,
                BigDecimal.valueOf(200),
                1L
        ));

        assertThat(response.progressPercentage()).isEqualByComparingTo("0.00");
        assertThat(response.currentValue()).isEqualByComparingTo("0.00");
    }

}
