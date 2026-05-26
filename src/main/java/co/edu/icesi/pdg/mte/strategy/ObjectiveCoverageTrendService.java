package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import co.edu.icesi.pdg.mte.common.BusinessException;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLink;
import co.edu.icesi.pdg.mte.integration.ProjectKeyResultLinkRepository;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntry;
import co.edu.icesi.pdg.mte.project.ProjectProgressEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
class ObjectiveCoverageTrendService {
    private final ObjectiveRepository objectiveRepository;
    private final ProjectKeyResultLinkRepository linkRepository;
    private final ProjectProgressEntryRepository progressRepository;

    ObjectiveCoverageTrendService(
            ObjectiveRepository objectiveRepository,
            ProjectKeyResultLinkRepository linkRepository,
            ProjectProgressEntryRepository progressRepository
    ) {
        this.objectiveRepository = objectiveRepository;
        this.linkRepository = linkRepository;
        this.progressRepository = progressRepository;
    }

    List<StrategyDtos.CoverageTrendPointResponse> coverageTrend(Long objectiveId) {
        Objective objective = objectiveRepository.findById(objectiveId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Objetivo no encontrado."));
        List<ProjectKeyResultLink> links = objective.getKeyResults()
                .stream()
                .flatMap(keyResult -> linkRepository.findByKeyResultIdAndActiveTrueOrderByIdAsc(keyResult.getId()).stream())
                .filter(link -> link.getProject() != null && link.getProject().getId() != null)
                .toList();
        List<ProjectProgressEntry> entries = progressRepository.findAll()
                .stream()
                .filter(entry -> links.stream().anyMatch(link -> link.getProject().getId().equals(entry.getProject().getId())))
                .sorted(Comparator.comparing(ProjectProgressEntry::getCreatedAt))
                .toList();

        List<StrategyDtos.CoverageTrendPointResponse> trend = new ArrayList<>();
        trend.add(new StrategyDtos.CoverageTrendPointResponse(
                objective.getCreatedAt(),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "OBJECTIVE_CREATED"
        ));
        for (ProjectProgressEntry entry : entries) {
            trend.add(new StrategyDtos.CoverageTrendPointResponse(
                    entry.getCreatedAt(),
                    estimatedCoverageAt(objective, links, entries, entry.getCreatedAt()),
                    "PROJECT_PROGRESS"
            ));
        }
        trend.add(new StrategyDtos.CoverageTrendPointResponse(
                Instant.now(),
                objective.completionPercentage().setScale(2, RoundingMode.HALF_UP),
                "CURRENT"
        ));
        return trend;
    }

    private BigDecimal estimatedCoverageAt(
            Objective objective,
            List<ProjectKeyResultLink> links,
            List<ProjectProgressEntry> entries,
            Instant timestamp
    ) {
        if (objective.getKeyResults().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal totalByKeyResult = BigDecimal.ZERO;
        for (KeyResult keyResult : objective.getKeyResults()) {
            BigDecimal krProgress = links.stream()
                    .filter(link -> link.getKeyResult().getId().equals(keyResult.getId()))
                    .map(link -> weightedProgressAt(link, entries, timestamp))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalByKeyResult = totalByKeyResult.add(krProgress);
        }
        return totalByKeyResult.divide(BigDecimal.valueOf(objective.getKeyResults().size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedProgressAt(ProjectKeyResultLink link, List<ProjectProgressEntry> entries, Instant timestamp) {
        BigDecimal latestProgress = entries.stream()
                .filter(entry -> link.getProject().getId().equals(entry.getProject().getId()))
                .filter(entry -> !entry.getCreatedAt().isAfter(timestamp))
                .max(Comparator.comparing(ProjectProgressEntry::getCreatedAt))
                .map(ProjectProgressEntry::getProgressPercent)
                .orElse(BigDecimal.ZERO);
        return link.getContributionWeight()
                .multiply(latestProgress)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
