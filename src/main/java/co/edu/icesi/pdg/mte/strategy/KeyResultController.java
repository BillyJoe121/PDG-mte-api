package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/key-results")
public class KeyResultController {

    private final StrategyService strategyService;

    public KeyResultController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @PutMapping("/{id}")
    StrategyDtos.KeyResultResponse update(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultRequest request
    ) {
        return strategyService.updateKeyResult(id, request);
    }

    @PatchMapping("/{id}/current-value")
    StrategyDtos.KeyResultResponse updateCurrentValue(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultCurrentValueRequest request
    ) {
        return strategyService.updateCurrentValue(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        strategyService.deleteKeyResult(id);
    }
}
