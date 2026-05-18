package co.edu.icesi.pdg.mte.presentation;

import co.edu.icesi.pdg.mte.api.dto.PresentationDtos;
import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriod;
import co.edu.icesi.pdg.mte.catalog.AcademicPeriodRepository;
import co.edu.icesi.pdg.mte.catalog.PeriodStatus;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.strategy.StrategyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class PresentationService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(Q[1-4]|[1-2])$");

    private final StrategyService strategyService;
    private final AcademicPeriodRepository periodRepository;

    public PresentationService(StrategyService strategyService, AcademicPeriodRepository periodRepository) {
        this.strategyService = strategyService;
        this.periodRepository = periodRepository;
    }

    public PresentationDtos.PresentationResponse presentation(String period) {
        String normalizedPeriod = resolvePeriod(period);
        var bets = strategyService.listStrategicBets(normalizedPeriod);
        StrategyDtos.ExecutionSummaryResponse total = totalSummary(bets);
        List<PresentationDtos.PresentationSlideResponse> slides = new ArrayList<>();
        slides.add(new PresentationDtos.PresentationSlideResponse(
                1,
                "COVER",
                "Seguimiento estrategico MSP",
                normalizedPeriod == null ? "Portafolio completo" : "Periodo " + normalizedPeriod,
                coverContent(normalizedPeriod, bets.size(), total)
        ));
        int order = 2;
        for (var bet : bets) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("strategicBetId", bet.id());
            content.put("status", bet.status());
            content.put("executionSummary", bet.executionSummary());
            slides.add(new PresentationDtos.PresentationSlideResponse(
                    order++,
                    "STRATEGIC_BET",
                    bet.name(),
                    bet.description(),
                    content
            ));
        }
        slides.add(new PresentationDtos.PresentationSlideResponse(
                order,
                "CLOSING",
                "Cierre",
                "Resumen generado dinamicamente con datos del MSP.",
                closingContent(order, total)
        ));
        return new PresentationDtos.PresentationResponse(
                normalizedPeriod,
                "Modo presentacion MSP",
                new PresentationDtos.PresentationControlsResponse(
                        true,
                        true,
                        List.of("ArrowRight", "Space"),
                        List.of("ArrowLeft"),
                        List.of("Escape")
                ),
                slides
        );
    }

    private String resolvePeriod(String period) {
        if (period == null || period.isBlank()) {
            return periodRepository.findFirstByStatusOrderByStartDateDesc(PeriodStatus.ACTIVO)
                    .map(AcademicPeriod::getName)
                    .orElse(null);
        }
        String trimmed = period.trim();
        if (!PERIOD_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El periodo debe tener formato YYYY-Q1..Q4 o YYYY-1..2.");
        }
        return trimmed;
    }

    private Map<String, Object> coverContent(
            String period,
            int strategicBets,
            StrategyDtos.ExecutionSummaryResponse total
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("institution", "Escuela TDI");
        content.put("source", "MSP");
        content.put("period", period);
        content.put("generatedAt", Instant.now());
        content.put("strategicBets", strategicBets);
        content.put("completedObjectives", total.completedObjectives());
        content.put("inProgressObjectives", total.inProgressObjectives());
        content.put("completedKeyResults", total.completedKeyResults());
        content.put("inProgressKeyResults", total.inProgressKeyResults());
        content.put("completedProjects", total.completedProjects());
        content.put("inProgressProjects", total.inProgressProjects());
        return content;
    }

    private Map<String, Object> closingContent(int slideCount, StrategyDtos.ExecutionSummaryResponse total) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("slides", slideCount);
        content.put("summaryText", total.summaryText());
        content.put("completedObjectives", total.completedObjectives());
        content.put("inProgressObjectives", total.inProgressObjectives());
        content.put("completedKeyResults", total.completedKeyResults());
        content.put("inProgressKeyResults", total.inProgressKeyResults());
        content.put("completedProjects", total.completedProjects());
        content.put("inProgressProjects", total.inProgressProjects());
        return content;
    }

    private StrategyDtos.ExecutionSummaryResponse totalSummary(List<StrategyDtos.StrategicBetResponse> bets) {
        int completedObjectives = 0;
        int inProgressObjectives = 0;
        int completedKeyResults = 0;
        int inProgressKeyResults = 0;
        int completedProjects = 0;
        int inProgressProjects = 0;
        for (StrategyDtos.StrategicBetResponse bet : bets) {
            StrategyDtos.ExecutionSummaryResponse summary = bet.executionSummary();
            if (summary == null) {
                continue;
            }
            completedObjectives += summary.completedObjectives();
            inProgressObjectives += summary.inProgressObjectives();
            completedKeyResults += summary.completedKeyResults();
            inProgressKeyResults += summary.inProgressKeyResults();
            completedProjects += summary.completedProjects();
            inProgressProjects += summary.inProgressProjects();
        }
        return new StrategyDtos.ExecutionSummaryResponse(
                "%d objetivos completos, %d en desarrollo. %d KPIs completos, %d en desarrollo. %d proyectos completados, %d en desarrollo."
                        .formatted(
                                completedObjectives,
                                inProgressObjectives,
                                completedKeyResults,
                                inProgressKeyResults,
                                completedProjects,
                                inProgressProjects
                        ),
                completedObjectives,
                inProgressObjectives,
                completedKeyResults,
                inProgressKeyResults,
                completedProjects,
                inProgressProjects,
                List.of()
        );
    }
}
