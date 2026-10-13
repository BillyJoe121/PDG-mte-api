package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceAcademicPeriodTest extends CatalogServiceTestSupport {

    @Test
    void createsAcademicPeriodWhenDatesAndNameAreValid() {
        when(periodRepository.existsByNameIgnoreCase("2026-1")).thenReturn(false);
        when(periodRepository.save(any(AcademicPeriod.class))).thenAnswer(invocation -> {
            AcademicPeriod period = invocation.getArgument(0);
            period.setId(1L);
            return period;
        });

        var response = service.createPeriod(new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                PeriodStatus.ACTIVO
        ));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(PeriodStatus.ACTIVO);
    }

    @Test
    void rejectsAcademicPeriodWhenEndDateIsNotAfterStartDate() {
        assertThatThrownBy(() -> service.createPeriod(new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 1, 1),
                PeriodStatus.ACTIVO
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de fin");

        verify(periodRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicatedAcademicPeriodName() {
        when(periodRepository.existsByNameIgnoreCase("2026-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createPeriod(new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                PeriodStatus.ACTIVO
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void updatesAcademicPeriodWhenNameAndDatesAreValid() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.findByNameIgnoreCase("2026-2")).thenReturn(Optional.empty());
        when(periodRepository.save(period)).thenReturn(period);

        var response = service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 15),
                PeriodStatus.PLANIFICACION
        ));

        assertThat(response.name()).isEqualTo("2026-2");
        assertThat(response.status()).isEqualTo(PeriodStatus.PLANIFICACION);
    }

    @Test
    void updatesAcademicPeriodWhenNameBelongsToSameRecord() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.findByNameIgnoreCase("2026-1")).thenReturn(Optional.of(period));
        when(periodRepository.save(period)).thenReturn(period);

        var response = service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-1",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                PeriodStatus.ACTIVO
        ));

        assertThat(response.name()).isEqualTo("2026-1");
    }

    @Test
    void updatesAcademicPeriodStatus() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.save(period)).thenReturn(period);

        var response = service.updatePeriodStatus(1L, new CatalogDtos.AcademicPeriodStatusRequest(PeriodStatus.CERRADO));

        assertThat(response.status()).isEqualTo(PeriodStatus.CERRADO);
    }

    @Test
    void togglesAcademicPeriodActiveState() {
        AcademicPeriod period = TestFixtures.period(1L);
        period.setStatus(PeriodStatus.PLANIFICACION);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.save(period)).thenReturn(period);

        var activeResponse = service.updatePeriodActive(1L, new CatalogDtos.AcademicPeriodActiveRequest(true));
        var inactiveResponse = service.updatePeriodActive(1L, new CatalogDtos.AcademicPeriodActiveRequest(false));

        assertThat(activeResponse.status()).isEqualTo(PeriodStatus.ACTIVO);
        assertThat(inactiveResponse.status()).isEqualTo(PeriodStatus.CERRADO);
    }

    @Test
    void deletesAcademicPeriodWhenItIsNotInUse() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));

        service.deletePeriod(1L);

        verify(periodRepository).delete(period);
        verify(auditService).record(eq(AuditAction.DELETE), eq("ACADEMIC_PERIOD"), eq(1L), anyString(), any(), isNull());
    }

    @Test
    void rejectsUpdatingMissingAcademicPeriod() {
        when(periodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePeriod(99L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 15),
                PeriodStatus.PLANIFICACION
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrado");
    }

    @Test
    void rejectsUpdatingAcademicPeriodWithInvalidDates() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 7, 1),
                PeriodStatus.PLANIFICACION
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("fecha de fin");
    }

    @Test
    void rejectsUpdatingAcademicPeriodWithDuplicatedName() {
        AcademicPeriod period = TestFixtures.period(1L);
        AcademicPeriod duplicated = TestFixtures.period(2L);
        duplicated.setName("2026-2");
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(periodRepository.findByNameIgnoreCase("2026-2")).thenReturn(Optional.of(duplicated));

        assertThatThrownBy(() -> service.updatePeriod(1L, new CatalogDtos.AcademicPeriodRequest(
                "2026-2",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 15),
                PeriodStatus.PLANIFICACION
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void rejectsDeletingAcademicPeriodUsedByGoalPeriod() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.existsByPeriodsId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletePeriod(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");

        verify(periodRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingAcademicPeriodUsedByKeyResult() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(keyResultRepository.existsByAcademicPeriodId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deletePeriod(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");
    }

    @Test
    void rejectsDeletingAcademicPeriodUsedByProject() {
        AcademicPeriod period = TestFixtures.period(1L);
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(projectRepository.existsByStartPeriodOrEndPeriod("2026-1", "2026-1")).thenReturn(true);

        assertThatThrownBy(() -> service.deletePeriod(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");

        verify(periodRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingMissingAcademicPeriod() {
        when(periodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePeriod(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrado");
    }
}
