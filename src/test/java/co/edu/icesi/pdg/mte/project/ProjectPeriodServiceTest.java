package co.edu.icesi.pdg.mte.project;

import co.edu.icesi.pdg.mte.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectPeriodServiceTest {
    private final ProjectPeriodService service = new ProjectPeriodService();

    @Test
    void parsesSemestersAndQuarters() {
        assertThat(service.parse("2026-1")).isEqualTo(new ProjectPeriodService.PeriodRange(2026 * 4 + 1, 2026 * 4 + 2));
        assertThat(service.parse("2026-2")).isEqualTo(new ProjectPeriodService.PeriodRange(2026 * 4 + 3, 2026 * 4 + 4));
        assertThat(service.parse("2026-Q3")).isEqualTo(new ProjectPeriodService.PeriodRange(2026 * 4 + 3, 2026 * 4 + 3));
    }

    @Test
    void rejectsMissingBlankAndMalformedPeriods() {
        assertThatThrownBy(() -> service.parse(null)).isInstanceOf(BusinessException.class).hasMessageContaining("YYYY");
        assertThatThrownBy(() -> service.parse("   ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.parse("2026-3")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.parse("26-1")).isInstanceOf(BusinessException.class);
    }

    @Test
    void validatesRangeWithOptionalEnd() {
        assertThatThrownBy(() -> service.validateRange(null, "2026-1")).isInstanceOf(BusinessException.class);
        assertThatCode(() -> service.validateRange(" 2026-1 ", null)).doesNotThrowAnyException();
        assertThatCode(() -> service.validateRange("2026-1", "  ")).doesNotThrowAnyException();
        assertThatCode(() -> service.validateRange("2026-1", " 2026-2 ")).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validateRange("2026-2", "2026-1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("periodo de fin");
    }

    @Test
    void overlapsUsesProjectEndPeriodOrFallsBackToItsStart() {
        var firstSemester = service.parse("2026-1");

        assertThat(service.overlaps(project("2026-1", null), firstSemester)).isTrue();
        assertThat(service.overlaps(project("2026-1", "  "), firstSemester)).isTrue();
        assertThat(service.overlaps(project("2026-2", null), firstSemester)).isFalse();
        assertThat(service.overlaps(project("2025-2", " 2026-1 "), firstSemester)).isTrue();
        assertThat(service.overlaps(project("2026-2", "2027-1"), firstSemester)).isFalse();
    }

    private static Project project(String startPeriod, String endPeriod) {
        Project project = new Project();
        project.setStartPeriod(startPeriod);
        project.setEndPeriod(endPeriod);
        return project;
    }
}
