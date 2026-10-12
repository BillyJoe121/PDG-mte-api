package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import org.springframework.test.util.ReflectionTestUtils;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.PeriodStatus;
import co.edu.icesi.pdg.mte.catalog.MeasurementUnitType;
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
    void managesWorldsAndProtectsWorldsInUse() {
        World world = TestFixtures.world(1L);
        World other = TestFixtures.world(2L); other.setName("Otro");
        when(worldRepository.save(any(World.class))).thenAnswer(invocation -> {
            World saved = invocation.getArgument(0); if (saved.getId() == null) saved.setId(1L); return saved;
        });
        assertThat(service.listWorlds()).hasSize(1);
        assertThat(service.createWorld(new StrategyDtos.WorldRequest(" Mundo ", "Desc")).name()).isEqualTo("Mundo");
        when(worldRepository.existsByNameIgnoreCase("Mundo")).thenReturn(true);
        assertThatThrownBy(() -> service.createWorld(new StrategyDtos.WorldRequest("Mundo", null))).isInstanceOf(BusinessException.class);

        when(worldRepository.findById(1L)).thenReturn(Optional.of(world));
        when(worldRepository.findByNameIgnoreCase("Otro")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateWorld(1L, new StrategyDtos.WorldRequest("Otro", null))).isInstanceOf(BusinessException.class);
        when(worldRepository.findByNameIgnoreCase(world.getName())).thenReturn(Optional.of(world));
        assertThat(service.updateWorld(1L, new StrategyDtos.WorldRequest(world.getName(), "Nueva")).description()).isEqualTo("Nueva");

        when(strategicBetRepository.existsByWorldId(1L)).thenReturn(true, false, false);
        when(goalRepository.existsByWorldId(1L)).thenReturn(true, false);
        assertThatThrownBy(() -> service.deleteWorld(1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.deleteWorld(1L)).isInstanceOf(BusinessException.class);
        service.deleteWorld(1L);
        verify(worldRepository).delete(world);
    }

    @Test
    void appliesDefaultBetDatesAndRequiresAnAvailableWorld() {
        when(strategicBetRepository.save(any(StrategicBet.class))).thenAnswer(invocation -> {
            StrategicBet saved = invocation.getArgument(0); saved.setId(7L); return saved;
        });
        var response = service.createStrategicBet(new StrategyDtos.StrategicBetRequest("Nueva", " Desc ", null, null));
        assertThat(response.endDate()).isEqualTo(response.startDate().plusYears(1));

        when(worldRepository.findAll()).thenReturn(List.of());
        assertThatThrownBy(() -> service.createStrategicBet(new StrategyDtos.StrategicBetRequest("Sin mundo", "Desc", null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("mundo");
    }

    @Test
    void updatesBetWithExistingDatesWorldAndDuplicateProtection() {
        World world = TestFixtures.world(2L);
        StrategicBet other = TestFixtures.strategicBet(2L); other.setName("Duplicada");
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of());
        when(strategicBetRepository.findByNameIgnoreCase("Duplicada")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.updateStrategicBet(1L,
                new StrategyDtos.StrategicBetRequest("Duplicada", "Desc", null, null)))
                .isInstanceOf(BusinessException.class);

        when(strategicBetRepository.findByNameIgnoreCase("Misma")).thenReturn(Optional.of(bet));
        when(worldRepository.findById(2L)).thenReturn(Optional.of(world));
        when(strategicBetRepository.save(bet)).thenReturn(bet);
        var response = service.updateStrategicBet(1L,
                new StrategyDtos.StrategicBetRequest("Misma", "Desc", 2L, null, null));
        assertThat(response.worldId()).isEqualTo(2L);
        assertThat(response.startDate()).isEqualTo(bet.getStartDate());
    }
    @Test
    void filtersStrategicBetsByAcademicPeriodDateRange() {
        StrategicBet outsidePeriod = TestFixtures.strategicBet(2L);
        outsidePeriod.setName("Apuesta futura");
        outsidePeriod.setStartDate(LocalDate.of(2027, 1, 1));
        outsidePeriod.setEndDate(LocalDate.of(2027, 12, 31));
        when(periodRepository.findByNameIgnoreCase("2026-1")).thenReturn(Optional.of(period));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet, outsidePeriod));
        when(objectiveRepository.findAll()).thenReturn(List.of());

        var response = service.listStrategicBets("2026-1");

        assertThat(response).extracting(StrategyDtos.StrategicBetResponse::id).containsExactly(1L);
    }

    @Test
    void filtersGoalsByExplicitPeriodAndUsesDatesForLegacyGoals() {
        AcademicPeriod otherPeriod = TestFixtures.period(2L);
        otherPeriod.setName("2027-1");
        otherPeriod.setStartDate(LocalDate.of(2027, 1, 1));
        otherPeriod.setEndDate(LocalDate.of(2027, 6, 30));

        InstitutionalGoal explicitlyIncluded = TestFixtures.goal(2L, unit);
        explicitlyIncluded.getPeriods().add(period);
        InstitutionalGoal explicitlyExcluded = TestFixtures.goal(3L, unit);
        explicitlyExcluded.getPeriods().add(otherPeriod);
        InstitutionalGoal legacyGoal = TestFixtures.goal(4L, unit);

        when(periodRepository.findByNameIgnoreCase("2026-1")).thenReturn(Optional.of(period));
        when(goalRepository.findAll()).thenReturn(List.of(explicitlyIncluded, explicitlyExcluded, legacyGoal));
        when(objectiveRepository.findAll()).thenReturn(List.of());

        var response = service.listGoals("2026-1");

        assertThat(response).extracting(StrategyDtos.GoalResponse::id).containsExactly(2L, 4L);
    }

    @Test
    void hidesInactiveStrategicItemsByDefaultAndIncludesThemForRecovery() {
        StrategicBet inactiveBet = TestFixtures.strategicBet(2L);
        inactiveBet.setStatus(StrategicStatus.INACTIVA);
        InstitutionalGoal inactiveGoal = TestFixtures.goal(2L, unit);
        inactiveGoal.setStatus(StrategicStatus.INACTIVA);
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet, inactiveBet));
        when(goalRepository.findAll()).thenReturn(List.of(goal, inactiveGoal));
        when(objectiveRepository.findAll()).thenReturn(List.of());

        assertThat(service.listStrategicBets(null)).extracting(StrategyDtos.StrategicBetResponse::id).containsExactly(1L);
        assertThat(service.listStrategicBets(null, true)).hasSize(2);
        assertThat(service.listGoals(null)).extracting(StrategyDtos.GoalResponse::id).containsExactly(1L);
        assertThat(service.listGoals(null, true)).hasSize(2);
    }

    @Test
    void archivesAndRestoresStrategicItemsWithoutDeletingThem() {
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(strategicBetRepository.save(bet)).thenReturn(bet);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(goal)).thenReturn(goal);
        when(objectiveRepository.findAll()).thenReturn(List.of());

        assertThat(service.setStrategicBetActive(1L, false).status()).isEqualTo(StrategicStatus.INACTIVA);
        assertThat(service.setStrategicBetActive(1L, true).status()).isEqualTo(StrategicStatus.ACTIVA);
        assertThat(service.setGoalActive(1L, false).status()).isEqualTo(StrategicStatus.INACTIVA);
        assertThat(service.setGoalActive(1L, true).status()).isEqualTo(StrategicStatus.ACTIVA);

        verify(strategicBetRepository, never()).delete(any());
        verify(goalRepository, never()).delete(any());
        verify(auditService, times(4)).record(eq(co.edu.icesi.pdg.mte.audit.AuditAction.STATUS_CHANGE), anyString(), eq(1L), anyString(), any(), any());
    }

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
        when(objectiveRepository.findAll()).thenReturn(List.of());

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
    void trimsGoalReferenceIndicatorWhenCreating() {
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(goalRepository.save(any(InstitutionalGoal.class))).thenAnswer(invocation -> {
            InstitutionalGoal saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var created = service.createGoal(new StrategyDtos.GoalRequest(
                "Meta",
                "Descripcion",
                "  Tasa de permanencia  ",
                BigDecimal.valueOf(90),
                1L,
                null,
                null
        ));

        assertThat(created.referenceIndicator()).isEqualTo("Tasa de permanencia");
    }

    @Test
    void rejectsPercentageGoalOutsideAllowedRange() {
        unit.setType(MeasurementUnitType.PORCENTAJE);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> service.createGoal(new StrategyDtos.GoalRequest(
                "Meta",
                "Descripcion",
                "Cobertura",
                BigDecimal.valueOf(101),
                1L,
                null,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("porcentaje");
    }

    @Test
    void rejectsBooleanGoalWithValueDifferentFromZeroOrOne() {
        unit.setType(MeasurementUnitType.BOOLEANA);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> service.createGoal(new StrategyDtos.GoalRequest(
                "Meta",
                "Descripcion",
                "Politica aprobada",
                BigDecimal.valueOf(7),
                1L,
                null,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("booleano");
    }

    @Test
    void updatesGoalWhenValid() {
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(goalRepository.save(any(InstitutionalGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectiveRepository.findAll()).thenReturn(List.of());

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
        unit.setType(MeasurementUnitType.NUMERICA);
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
