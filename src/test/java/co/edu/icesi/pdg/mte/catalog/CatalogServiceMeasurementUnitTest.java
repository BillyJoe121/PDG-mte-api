package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.CatalogDtos;
import co.edu.icesi.pdg.mte.audit.AuditAction;
import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceMeasurementUnitTest extends CatalogServiceTestSupport {

    @Test
    void createsMeasurementUnitWhenNameIsUnique() {
        when(unitRepository.existsByNameIgnoreCase("Horas")).thenReturn(false);
        when(unitRepository.save(any(MeasurementUnit.class))).thenAnswer(invocation -> {
            MeasurementUnit unit = invocation.getArgument(0);
            unit.setId(1L);
            return unit;
        });

        var response = service.createUnit(new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                "Tiempo invertido"
        ));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Horas");
        verify(unitRepository).save(any(MeasurementUnit.class));
    }

    @Test
    void rejectsDuplicatedMeasurementUnitName() {
        when(unitRepository.existsByNameIgnoreCase("Horas")).thenReturn(true);

        assertThatThrownBy(() -> service.createUnit(new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");

        verify(unitRepository, never()).save(any());
    }

    @Test
    void updatesMeasurementUnitWhenNameIsAvailable() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Horas actualizadas")).thenReturn(Optional.empty());
        when(unitRepository.save(unit)).thenReturn(unit);

        var response = service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Horas actualizadas",
                MeasurementUnitType.NUMERICA,
                "Nueva descripcion"
        ));

        assertThat(response.name()).isEqualTo("Horas actualizadas");
        assertThat(response.type()).isEqualTo(MeasurementUnitType.NUMERICA);
        assertThat(response.description()).isEqualTo("Nueva descripcion");
    }

    @Test
    void updatesMeasurementUnitWhenNameBelongsToSameRecord() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Porcentaje")).thenReturn(Optional.of(unit));
        when(unitRepository.save(unit)).thenReturn(unit);

        var response = service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Porcentaje",
                MeasurementUnitType.PORCENTAJE,
                "Mismo registro"
        ));

        assertThat(response.name()).isEqualTo("Porcentaje");
    }

    @Test
    void rejectsChangingTypeOfMeasurementUnitUsedByGoals() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Porcentaje")).thenReturn(Optional.of(unit));
        when(goalRepository.existsByMeasurementUnitId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Porcentaje",
                MeasurementUnitType.BOOLEANA,
                "Nueva regla"
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("tipo")
                .hasMessageContaining("en uso");

        verify(unitRepository, never()).save(any());
    }

    @Test
    void normalizesOptionalMeasurementUnitDescription() {
        when(unitRepository.existsByNameIgnoreCase("Horas")).thenReturn(false);
        when(unitRepository.save(any(MeasurementUnit.class))).thenAnswer(invocation -> {
            MeasurementUnit saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var response = service.createUnit(new CatalogDtos.MeasurementUnitRequest(
                "  Horas  ",
                MeasurementUnitType.NUMERICA,
                "   "
        ));

        assertThat(response.name()).isEqualTo("Horas");
        assertThat(response.description()).isNull();
    }

    @Test
    void togglesMeasurementUnitActiveFlag() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.save(unit)).thenReturn(unit);

        var response = service.updateUnitActive(1L, new CatalogDtos.MeasurementUnitActiveRequest(false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void deletesMeasurementUnitWhenItIsNotInUse() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        service.deleteUnit(1L);

        verify(unitRepository).delete(unit);
        verify(auditService).record(eq(AuditAction.DELETE), eq("MEASUREMENT_UNIT"), eq(1L), anyString(), any(), isNull());
    }

    @Test
    void rejectsUpdatingMissingMeasurementUnit() {
        when(unitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUnit(99L, new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrada");
    }

    @Test
    void rejectsUpdatingMeasurementUnitWithDuplicatedName() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        MeasurementUnit duplicated = TestFixtures.unit(2L);
        duplicated.setName("Horas");
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.findByNameIgnoreCase("Horas")).thenReturn(Optional.of(duplicated));

        assertThatThrownBy(() -> service.updateUnit(1L, new CatalogDtos.MeasurementUnitRequest(
                "Horas",
                MeasurementUnitType.NUMERICA,
                null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void rejectsDeletingMissingMeasurementUnit() {
        when(unitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUnit(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no encontrada");
    }

    @Test
    void rejectsDeletingMeasurementUnitUsedByGoal() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(goalRepository.existsByMeasurementUnitId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUnit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");

        verify(unitRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletingMeasurementUnitUsedByKeyResult() {
        MeasurementUnit unit = TestFixtures.unit(1L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(keyResultRepository.existsByMeasurementUnitId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUnit(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("en uso");
    }
}
