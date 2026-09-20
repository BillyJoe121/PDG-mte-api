package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/objectives")
public class ObjectiveController {

    private final StrategyService strategyService;

    public ObjectiveController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    List<StrategyDtos.ObjectiveResponse> list(
            @RequestParam(required = false) Long strategicBetId,
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long periodId,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return strategyService.listObjectives(strategicBetId, goalId, departmentId, periodId, includeArchived);
    }

    @GetMapping("/cards")
    List<StrategyDtos.ObjectiveCardResponse> listCards(
            @RequestParam(required = false) Long strategicBetId,
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long periodId,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return strategyService.listObjectiveCards(strategicBetId, goalId, departmentId, periodId, includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.ObjectiveResponse create(@Valid @RequestBody StrategyDtos.ObjectiveRequest request) {
        return strategyService.createObjective(request);
    }

    @GetMapping("/{id}")
    StrategyDtos.ObjectiveResponse get(@PathVariable Long id) {
        return strategyService.getObjective(id);
    }

    @GetMapping("/{id}/detail")
    StrategyDtos.ObjectiveDetailResponse detail(@PathVariable Long id) {
        return strategyService.getObjectiveDetail(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.ObjectiveResponse update(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.ObjectiveUpdateRequest request
    ) {
        return strategyService.updateObjective(id, request);
    }

    @PatchMapping("/{id}/archived")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.ObjectiveResponse setArchived(
            @PathVariable Long id,
            @RequestBody StrategyDtos.ArchiveStateRequest request
    ) {
        return strategyService.setObjectiveArchived(id, request.archived());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.ObjectiveResponse setStatus(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.ObjectiveStatusRequest request
    ) {
        return strategyService.setObjectiveStatus(id, request.status());
    }

    @GetMapping("/{id}/key-results")
    List<StrategyDtos.KeyResultResponse> listKeyResults(@PathVariable Long id) {
        return strategyService.listObjectiveKeyResults(id);
    }

    @PostMapping("/{id}/key-results")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse addKeyResult(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultRequest request
    ) {
        return strategyService.addKeyResult(id, request);
    }

    @GetMapping("/{id}/coverage-trend")
    List<StrategyDtos.CoverageTrendPointResponse> coverageTrend(@PathVariable Long id) {
        return strategyService.coverageTrend(id);
    }
}
