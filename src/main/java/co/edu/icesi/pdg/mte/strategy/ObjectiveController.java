package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
            @RequestParam(required = false) Long periodId
    ) {
        return strategyService.listObjectives(strategicBetId, goalId, departmentId, periodId);
    }

    @GetMapping("/cards")
    List<StrategyDtos.ObjectiveCardResponse> listCards(
            @RequestParam(required = false) Long strategicBetId,
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long periodId
    ) {
        return strategyService.listObjectiveCards(strategicBetId, goalId, departmentId, periodId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StrategyDtos.ObjectiveResponse create(@Valid @RequestBody StrategyDtos.ObjectiveRequest request) {
        return strategyService.createObjective(request);
    }

    @GetMapping("/{id}")
    StrategyDtos.ObjectiveResponse get(@PathVariable Long id) {
        return strategyService.getObjective(id);
    }

    @PatchMapping("/{id}")
    StrategyDtos.ObjectiveResponse update(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.ObjectiveUpdateRequest request
    ) {
        return strategyService.updateObjective(id, request);
    }

    @GetMapping("/{id}/key-results")
    List<StrategyDtos.KeyResultResponse> listKeyResults(@PathVariable Long id) {
        return strategyService.listObjectiveKeyResults(id);
    }

    @PostMapping("/{id}/key-results")
    @ResponseStatus(HttpStatus.CREATED)
    StrategyDtos.KeyResultResponse addKeyResult(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultRequest request
    ) {
        return strategyService.addKeyResult(id, request);
    }
}
