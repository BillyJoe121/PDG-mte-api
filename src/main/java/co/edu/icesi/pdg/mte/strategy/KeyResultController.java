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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO')")
    StrategyDtos.KeyResultResponse update(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultRequest request
    ) {
        return strategyService.updateKeyResult(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO')")
    void delete(@PathVariable Long id) {
        strategyService.deleteKeyResult(id);
    }
}
