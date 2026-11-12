package co.edu.icesi.pdg.mte.catalog;

import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeasurementValuePolicyTest {

    @Test
    void inactiveUnitsAreRejectedAndActiveOnesAccepted() {
        MeasurementUnit unit = unit(MeasurementUnitType.NUMERICA);
        assertThatCode(() -> MeasurementValuePolicy.requireActive(unit)).doesNotThrowAnyException();

        unit.setActive(false);
        assertThatThrownBy(() -> MeasurementValuePolicy.requireActive(unit))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void goalValueMustBePresentAndNotNegative() {
        MeasurementUnit unit = unit(MeasurementUnitType.NUMERICA);

        assertThatThrownBy(() -> MeasurementValuePolicy.validateGoalValue(null, unit))
                .hasMessageContaining("mayor o igual a cero");
        assertThatThrownBy(() -> MeasurementValuePolicy.validateGoalValue(BigDecimal.valueOf(-1), unit))
                .hasMessageContaining("mayor o igual a cero");
        assertThatCode(() -> MeasurementValuePolicy.validateGoalValue(BigDecimal.ZERO, unit)).doesNotThrowAnyException();
        assertThatCode(() -> MeasurementValuePolicy.validateGoalValue(BigDecimal.valueOf(5000), unit)).doesNotThrowAnyException();
    }

    @Test
    void percentageValuesStayBetweenZeroAndOneHundred() {
        MeasurementUnit unit = unit(MeasurementUnitType.PORCENTAJE);

        assertThatCode(() -> MeasurementValuePolicy.validateKeyResultValues(BigDecimal.ZERO, BigDecimal.valueOf(100), unit))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> MeasurementValuePolicy.validateKeyResultCurrentValue(BigDecimal.valueOf(-1), unit))
                .hasMessageContaining("entre 0 y 100");
        assertThatThrownBy(() -> MeasurementValuePolicy.validateKeyResultCurrentValue(new BigDecimal("100.01"), unit))
                .hasMessageContaining("entre 0 y 100");
    }

    @Test
    void booleanValuesAreZeroOrOne() {
        MeasurementUnit unit = unit(MeasurementUnitType.BOOLEANA);

        assertThatCode(() -> MeasurementValuePolicy.validateKeyResultValues(BigDecimal.ZERO, BigDecimal.ONE, unit))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> MeasurementValuePolicy.validateKeyResultCurrentValue(BigDecimal.valueOf(2), unit))
                .hasMessageContaining("0 o 1");
        assertThatThrownBy(() -> MeasurementValuePolicy.validateKeyResultCurrentValue(new BigDecimal("0.5"), unit))
                .hasMessageContaining("0 o 1");
    }

    @Test
    void keyResultValuesAreMandatory() {
        MeasurementUnit unit = unit(MeasurementUnitType.NUMERICA);

        assertThatThrownBy(() -> MeasurementValuePolicy.validateKeyResultValues(null, BigDecimal.ONE, unit))
                .hasMessage("El valor base es obligatorio.");
        assertThatThrownBy(() -> MeasurementValuePolicy.validateKeyResultValues(BigDecimal.ONE, null, unit))
                .hasMessage("El valor objetivo es obligatorio.");
    }

    private static MeasurementUnit unit(MeasurementUnitType type) {
        MeasurementUnit unit = new MeasurementUnit();
        unit.setType(type);
        unit.setActive(true);
        return unit;
    }
}
