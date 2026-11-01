package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/key-results")
public class KeyResultController {

    private final KeyResultService keyResultService;

    public KeyResultController(KeyResultService keyResultService) {
        this.keyResultService = keyResultService;
    }

    @GetMapping
    List<StrategyDtos.KeyResultResponse> list(
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) Long strategicBetId,
            @RequestParam(required = false) Long periodId
    ) {
        return keyResultService.list(goalId, strategicBetId, periodId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse create(@Valid @RequestBody StrategyDtos.KeyResultRequest request) {
        return keyResultService.create(request);
    }

    @GetMapping("/{id}")
    StrategyDtos.KeyResultResponse get(@PathVariable Long id) {
        return keyResultService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse update(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultRequest request
    ) {
        return keyResultService.update(id, request);
    }

    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse updateProgress(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultProgressRequest request
    ) {
        return keyResultService.updateProgress(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    StrategyDtos.KeyResultResponse setStatus(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.KeyResultStatusRequest request
    ) {
        return keyResultService.setStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    void delete(@PathVariable Long id) {
        keyResultService.delete(id);
    }
}
