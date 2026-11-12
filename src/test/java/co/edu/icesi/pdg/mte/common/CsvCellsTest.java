package co.edu.icesi.pdg.mte.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvCellsTest {

    @Test
    void quotesPlainValuesAndDoublesInnerQuotes() {
        assertThat(CsvCells.quote(null)).isEmpty();
        assertThat(CsvCells.quote("")).isEqualTo("\"\"");
        assertThat(CsvCells.quote("Ingenieria")).isEqualTo("\"Ingenieria\"");
        assertThat(CsvCells.quote("Proyecto \"A\", fase 1")).isEqualTo("\"Proyecto \"\"A\"\", fase 1\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@nombre", "\tvalor", "\rvalor"})
    void keepsValuesThatLookLikeFormulasAsText(String value) {
        assertThat(CsvCells.quote(value)).isEqualTo("\"'" + value + "\"");
    }

    @Test
    void leavesFormulaCharactersAfterTheFirstPositionAlone() {
        assertThat(CsvCells.quote("Meta 2026-1")).isEqualTo("\"Meta 2026-1\"");
        assertThat(CsvCells.quote("a=b")).isEqualTo("\"a=b\"");
    }
}
