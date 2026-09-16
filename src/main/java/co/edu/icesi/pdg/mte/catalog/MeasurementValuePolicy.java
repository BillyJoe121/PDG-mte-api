package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.common.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * Centralizes the value semantics defined by each measurement-unit type.
 */
public final class MeasurementValuePolicy {
    private MeasurementValuePolicy() {
    }

    public static void requireActive(MeasurementUnit unit) {
        if (!unit.isActive()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "La unidad de medida seleccionada esta inactiva.");
        }
    }

    public static void validateGoalValue(BigDecimal value, MeasurementUnit unit) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El valor esperado debe ser mayor o igual a cero.");
        }
        validateTypedValue(value, unit, "El valor esperado");
    }

    public static void validateKeyResultValues(BigDecimal baseValue, BigDecimal targetValue, MeasurementUnit unit) {
        validateTypedValue(baseValue, unit, "El valor base");
        validateTypedValue(targetValue, unit, "El valor objetivo");
    }

    public static void validateKeyResultCurrentValue(BigDecimal value, MeasurementUnit unit) {
        validateTypedValue(value, unit, "El valor actual");
    }

    private static void validateTypedValue(BigDecimal value, MeasurementUnit unit, String fieldLabel) {
        if (value == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, fieldLabel + " es obligatorio.");
        }
        if (unit.getType() == MeasurementUnitType.PORCENTAJE
                && (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, fieldLabel + " para una unidad de porcentaje debe estar entre 0 y 100.");
        }
        if (unit.getType() == MeasurementUnitType.BOOLEANA
                && value.compareTo(BigDecimal.ZERO) != 0
                && value.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, fieldLabel + " booleano solo puede ser 0 o 1.");
        }
    }
}
