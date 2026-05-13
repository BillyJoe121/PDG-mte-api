package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.TestFixtures;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.*;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.Project;
import co.edu.icesi.pdg.mte.project.ProjectStatus;
import co.edu.icesi.pdg.mte.project.ProjectType;
import co.edu.icesi.pdg.mte.security.ExternalUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    @Mock
    private StrategicBetRepository strategicBetRepository;
    @Mock
    private InstitutionalGoalRepository goalRepository;
    @Mock
    private ObjectiveRepository objectiveRepository;
    @Mock
    private KeyResultRepository keyResultRepository;
    @Mock
    private MeasurementUnitRepository unitRepository;
    @Mock
    private AcademicPeriodRepository periodRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private ProjectKeyResultLinkRepository linkRepository;

    @InjectMocks
    private StrategyService service;

    private MeasurementUnit unit;
    private AcademicPeriod period;
    private Department department;
    private StrategicBet bet;
    private InstitutionalGoal goal;
    private Objective objective;

    @BeforeEach
    void setUp() {
        unit = TestFixtures.unit(1L);
        period = TestFixtures.period(1L);
        department = TestFixtures.department(1L);
        bet = TestFixtures.strategicBet(1L);
        goal = TestFixtures.goal(1L, unit);
        objective = TestFixtures.objective(1L, unit, period, department, goal, bet);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ExternalUserContext.mock(),
                null,
                List.of()
        ));
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
                "Metric",
                BigDecimal.ZERO,
                BigDecimal.valueOf(200),
                BigDecimal.valueOf(100),
                1L
        ));

        assertThat(response.progressPercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void updatesCurrentValueAndRecalculatesProgress() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(keyResultRepository.save(any(KeyResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateCurrentValue(1L, new StrategyDtos.KeyResultCurrentValueRequest(BigDecimal.valueOf(75)));

        assertThat(response.progressPercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void deletesKeyResultWhenItHasNoActiveProjectLinks() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(keyResult));
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(0L);

        service.deleteKeyResult(1L);

        verify(keyResultRepository).delete(keyResult);
    }

    @Test
    void rejectsKeyResultDeletionWhenItHasActiveProjectLinks() {
        when(keyResultRepository.findById(1L)).thenReturn(Optional.of(TestFixtures.keyResult(1L, unit)));
        when(linkRepository.countByKeyResultIdAndActiveTrue(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteKeyResult(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("proyectos vinculados");
    }

    @Test
    void returnsObjectiveAndStrategicBetDetail() {
        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of());

        assertThat(service.getObjective(1L).id()).isEqualTo(1L);
        assertThat(service.getStrategicBet(1L, null).executionSummary().inProgressObjectives()).isEqualTo(1);
        assertThat(service.getStrategicBet(1L, null).executionSummary().inProgressKeyResults()).isEqualTo(1);
    }

    @Test
    void returnsEmptyStrategicBetSummaryWhenThereAreNoObjectives() {
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of());

        assertThat(service.getStrategicBet(1L, null).executionSummary().summaryText()).contains("0 objetivos completos");
    }

    @Test
    void listsObjectivesWithAndWithoutFilters() {
        when(objectiveRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(objective));

        assertThat(service.listObjectives(null, null, null, null)).hasSize(1);
        assertThat(service.listObjectives(1L, 1L, 1L, 1L)).hasSize(1);
        var cards = service.listObjectiveCards(1L, 1L, 1L, 1L);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).lowCompletionAlert()).isTrue();
    }

    @Test
    void createsObjectiveWithoutSecurityPrincipal() {
        SecurityContextHolder.clearContext();
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createObjective(objectiveRequest(1L, 1L, 1L, 1L, 1L));

        assertThat(response.keyResults()).hasSize(1);
        verify(objectiveRepository).save(argThat(saved -> saved.getCreatedByExternalUserId() == null));
    }

    @Test
    void createsObjectiveWithoutExternalUserPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario-local",
                null,
                List.of()
        ));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(periodRepository.findById(1L)).thenReturn(Optional.of(period));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createObjective(objectiveRequest(1L, 1L, 1L, 1L, 1L));

        verify(objectiveRepository).save(argThat(saved -> saved.getCreatedByExternalUserId() == null));
    }

    @Test
    void updatesObjectiveBasicFields() {
        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));
        when(objectiveRepository.save(any(Objective.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateObjective(1L, new StrategyDtos.ObjectiveUpdateRequest(
                "Objetivo actualizado",
                "Descripcion actualizada"
        ));

        assertThat(response.name()).isEqualTo("Objetivo actualizado");
        assertThat(response.description()).isEqualTo("Descripcion actualizada");
    }

    @Test
    void buildsStrategicHierarchyTreeWithProjectLinks() {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(objective.getKeyResults().get(0));
        link.setExternalProjectId(77L);
        link.setContributionWeight(BigDecimal.valueOf(35));

        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(strategicBetRepository.findAll()).thenReturn(List.of(bet));
        when(goalRepository.findAll()).thenReturn(List.of(goal));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(link));

        var tree = service.getStrategicHierarchyTree(null);

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).nodeType()).isEqualTo("STRATEGIC_BET");
        assertThat(tree.get(0).executionSummary().inProgressObjectives()).isEqualTo(1);
        assertThat(tree.get(0).children().get(0).badge()).contains("Meta:");
        assertThat(tree.get(0).children().get(0).children().get(0).children().get(0).nodeType()).isEqualTo("PROJECT");
        assertThat(tree.get(1).nodeType()).isEqualTo("GOAL");
        assertThat(tree.get(1).children().get(0).badge()).contains("Apuesta:");
    }

    @Test
    void strategicSummaryCountsCompletedObjectivesAndKeyResultsWithBlankPeriodFilter() {
        objective.getKeyResults().get(0).setProgressPercentage(BigDecimal.valueOf(100));
        when(strategicBetRepository.findById(1L)).thenReturn(Optional.of(bet));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of());

        var response = service.getStrategicBet(1L, "   ");

        assertThat(response.executionSummary().completedObjectives()).isEqualTo(1);
        assertThat(response.executionSummary().completedKeyResults()).isEqualTo(1);
        assertThat(response.executionSummary().periods().get(0).period()).isEqualTo("2026-1");
    }

    @Test
    void strategicSummaryHandlesProjectPeriodsNullIdsAndDuplicateProjectStates() {
        KeyResult keyResult = objective.getKeyResults().get(0);
        keyResult.setProgressPercentage(BigDecimal.valueOf(60));
        Project completed = project(1L, ProjectStatus.FINALIZADO);
        completed.setEndPeriod("2026-Q2");
        Project activeSameId = project(1L, ProjectStatus.ACTIVO);
        activeSameId.setEndPeriod("2026-Q2");
        Project activeWithStartFallback = project(2L, ProjectStatus.ACTIVO);
        activeWithStartFallback.setStartPeriod("2026-Q3");
        activeWithStartFallback.setEndPeriod(null);
        Project activeWithObjectiveFallback = project(3L, ProjectStatus.ACTIVO);
        activeWithObjectiveFallback.setStartPeriod(" ");
        activeWithObjectiveFallback.setEndPeriod(" ");
        Project withoutId = project(null, ProjectStatus.ACTIVO);
        withoutId.setEndPeriod("2026-Q2");

        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(
                link(keyResult, completed, 40),
                link(keyResult, activeSameId, 20),
                link(keyResult, activeWithStartFallback, 20),
                link(keyResult, activeWithObjectiveFallback, 20),
                link(keyResult, withoutId, 20)
        ));

        var response = service.getGoal(1L, null);

        assertThat(response.executionSummary().completedProjects()).isEqualTo(1);
        assertThat(response.executionSummary().inProgressProjects()).isEqualTo(2);
        assertThat(response.executionSummary().periods().stream().map(StrategyDtos.PeriodExecutionSummaryResponse::period))
                .contains("2026-Q2", "2026-Q3", "2026-1");
    }

    @Test
    void strategicSummaryHonorsTrimmedPeriodFilter() {
        Project project = project(2L, ProjectStatus.ACTIVO);
        project.setEndPeriod("2026-Q4");
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(objectiveRepository.findAll()).thenReturn(List.of(objective));
        when(linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(
                link(objective.getKeyResults().get(0), project, 20)
        ));

        var response = service.getGoal(1L, " 2026-Q4 ");

        assertThat(response.executionSummary().periods()).hasSize(1);
        assertThat(response.executionSummary().periods().get(0).period()).isEqualTo("2026-Q4");
    }

    @Test
    void listsKeyResultsForExistingObjective() {
        when(objectiveRepository.findById(1L)).thenReturn(Optional.of(objective));
        when(keyResultRepository.findByObjectiveIdOrderByIdAsc(1L)).thenReturn(objective.getKeyResults());

        assertThat(service.listObjectiveKeyResults(1L)).hasSize(1);
    }

    @Test
    void rejectsWhenTargetEntitiesDoNotExist() {
        when(objectiveRepository.findById(99L)).thenReturn(Optional.empty());
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());
        when(keyResultRepository.findById(99L)).thenReturn(Optional.empty());
        when(strategicBetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getObjective(99L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getGoal(99L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getStrategicBet(99L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateCurrentValue(99L, new StrategyDtos.KeyResultCurrentValueRequest(BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void keyResultProgressIgnoresManualValuesWhenThereAreNoCompletedProjects() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        keyResult.setBaseValue(BigDecimal.TEN);
        keyResult.setTargetValue(BigDecimal.TEN);
        keyResult.setCurrentValue(BigDecimal.valueOf(20));

        keyResult.recalculateProgress();

        assertThat(keyResult.getProgressPercentage()).isEqualByComparingTo("0");
    }

    @Test
    void keyResultProgressSumsCompletedProjectWeightsOnly() {
        KeyResult keyResult = TestFixtures.keyResult(1L, unit);
        Project completed = project(1L, ProjectStatus.FINALIZADO);
        Project active = project(2L, ProjectStatus.ACTIVO);
        ProjectKeyResultLink completedLink = link(keyResult, completed, 45);
        ProjectKeyResultLink activeLink = link(keyResult, active, 35);

        keyResult.recalculateProgress(List.of(completedLink, activeLink));

        assertThat(keyResult.getProgressPercentage()).isEqualByComparingTo("45.00");
    }

    private StrategyDtos.ObjectiveRequest objectiveRequest(
            Long departmentId,
            Long periodId,
            Long goalId,
            Long strategicBetId,
            Long unitId
    ) {
        return new StrategyDtos.ObjectiveRequest(
                "Objetivo",
                "Descripcion",
                departmentId,
                periodId,
                goalId,
                strategicBetId,
                List.of(new StrategyDtos.KeyResultRequest(
                        "KR",
                        "Metric",
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(25),
                        unitId
                ))
        );
    }

    private ProjectKeyResultLink link(KeyResult keyResult, Project project, int weight) {
        ProjectKeyResultLink link = new ProjectKeyResultLink();
        link.setKeyResult(keyResult);
        link.setProject(project);
        link.setContributionWeight(BigDecimal.valueOf(weight));
        return link;
    }

    private Project project(Long id, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setName("Proyecto " + id);
        project.setDescription("Descripcion");
        project.setType(ProjectType.INVESTIGACION);
        project.setDepartment(department);
        project.setStatus(status);
        project.setStartPeriod("2026-Q1");
        project.setEndPeriod("2026-Q2");
        return project;
    }
}
