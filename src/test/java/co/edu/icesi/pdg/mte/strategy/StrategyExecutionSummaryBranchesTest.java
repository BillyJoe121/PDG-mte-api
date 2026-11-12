package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ContributionType;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyExecutionSummaryBranchesTest {
    private final KeyResultRepository keyResults = mock(KeyResultRepository.class);
    private final ProjectKeyResultLinkRepository links = mock(ProjectKeyResultLinkRepository.class);
    private final StrategyExecutionSummaryService service = new StrategyExecutionSummaryService(keyResults, links);

    @Test
    void draftKeyResultsAreNotCountedInAnyCategory() {
        KeyResult draft = keyResult(1L, "2026-1", KeyResultStatus.BORRADOR);
        when(keyResults.findAll()).thenReturn(List.of(draft));

        var summary = service.forPortfolio(null);

        assertThat(summary.completedKeyResults()).isZero();
        assertThat(summary.inProgressKeyResults()).isZero();
        assertThat(summary.completedProjects()).isZero();
    }

    @Test
    void keyResultPeriodsMustBeWellFormedWhenAFilterIsApplied() {
        when(keyResults.findAll()).thenReturn(List.of(keyResult(1L, "  ", KeyResultStatus.ACTIVO)));
        assertThatThrownBy(() -> service.forPortfolio("2026-1")).isInstanceOf(BusinessException.class);

        when(keyResults.findAll()).thenReturn(List.of(keyResult(1L, "periodo", KeyResultStatus.ACTIVO)));
        assertThatThrownBy(() -> service.forPortfolio("2026-1")).isInstanceOf(BusinessException.class);

        when(keyResults.findAll()).thenReturn(List.of(keyResult(1L, "2026-1", KeyResultStatus.ACTIVO)));
        assertThatThrownBy(() -> service.forPortfolio("periodo")).isInstanceOf(BusinessException.class);
    }

    @Test
    void periodFilterKeepsOnlyOverlappingPeriods() {
        when(keyResults.findAll()).thenReturn(List.of(
                keyResult(1L, "2025-1", KeyResultStatus.ACTIVO),
                keyResult(2L, "2026-Q2", KeyResultStatus.ACTIVO),
                keyResult(3L, "2026-2", KeyResultStatus.CERRADO),
                keyResult(4L, "2027-1", KeyResultStatus.ACTIVO)));

        var firstSemester = service.forPortfolio("2026-1");
        var secondSemester = service.forPortfolio("2026-Q3");

        assertThat(firstSemester.inProgressKeyResults()).isEqualTo(1);
        assertThat(firstSemester.completedKeyResults()).isZero();
        assertThat(secondSemester.completedKeyResults()).isEqualTo(1);
        assertThat(secondSemester.inProgressKeyResults()).isZero();
    }

    @Test
    void linkedProjectsUseTheirEndPeriodThenTheirStartPeriodThenTheKeyResultPeriod() {
        KeyResult keyResult = keyResult(1L, "2026-1", KeyResultStatus.ACTIVO);
        Project endOnly = project(10L, null, "2026-2", ProjectStatus.FINALIZADO);
        Project startOnly = project(11L, "2026-2", " ", ProjectStatus.ACTIVO);
        Project noPeriods = project(12L, "  ", null, ProjectStatus.ACTIVO);
        Project nullStart = project(13L, null, null, ProjectStatus.ACTIVO);
        when(keyResults.findAll()).thenReturn(List.of(keyResult));
        when(links.findByKeyResultIdInAndActiveTrueOrderByIdAsc(any())).thenReturn(List.of(
                link(keyResult, endOnly), link(keyResult, startOnly), link(keyResult, noPeriods), link(keyResult, nullStart)));

        var firstSemester = service.forPortfolio("2026-1");
        var secondSemester = service.forPortfolio("2026-2");

        assertThat(firstSemester.inProgressProjects()).isEqualTo(2);
        assertThat(firstSemester.completedProjects()).isZero();
        assertThat(secondSemester.completedProjects()).isEqualTo(1);
        assertThat(secondSemester.inProgressProjects()).isEqualTo(1);
    }

    @Test
    void legacyKeyResultsWithoutAnyPeriodCannotBePlacedInTime() {
        KeyResult legacy = keyResult(1L, "2026-1", KeyResultStatus.ACTIVO);
        legacy.setAcademicPeriod(null);
        when(keyResults.findAll()).thenReturn(List.of(legacy));

        assertThat(service.forPortfolio("2026-1").inProgressKeyResults()).isZero();
        assertThat(service.forPortfolio(" ").inProgressKeyResults()).isZero();
    }

    private static KeyResult keyResult(Long id, String periodName, KeyResultStatus status) {
        AcademicPeriod period = TestFixtures.period(id);
        period.setName(periodName);
        KeyResult keyResult = TestFixtures.keyResult(id, TestFixtures.unit(1L), period, null, null);
        keyResult.setStatus(status);
        return keyResult;
    }

    private static Project project(Long id, String startPeriod, String endPeriod, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setName("Proyecto " + id);
        project.setType(ProjectType.INVESTIGACION);
        project.setStatus(status);
        project.setStartPeriod(startPeriod);
        project.setEndPeriod(endPeriod);
        return project;
    }

    private static ProjectKeyResultLink link(KeyResult keyResult, Project project) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setProject(project);
        link.setContributionWeight(BigDecimal.TEN);
        link.setContributionType(ContributionType.DIRECTA);
        return link;
    }
}
