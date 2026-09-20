package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/key-results")
public class KeyResultController {

    private final StrategyService strategyService;

    public KeyResultController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping("/{id}")
    StrategyDtos.KeyResultResponse get(@PathVariable Long id) {
        return strategyService.getKeyResult(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse update(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultRequest request
    ) {
        return strategyService.updateKeyResult(id, request);
    }

    @PatchMapping("/{id}/current-value")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse updateCurrentValue(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultCurrentValueRequest request
    ) {
        return strategyService.updateKeyResultCurrentValue(id, request.currentValue());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse setStatus(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultStatusRequest request
    ) {
        return strategyService.setKeyResultStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    void delete(@PathVariable Long id) {
        strategyService.deleteKeyResult(id);
    }
}
