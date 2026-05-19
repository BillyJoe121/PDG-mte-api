package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/worlds")
public class WorldController {
    private final StrategyService strategyService;

    public WorldController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    List<StrategyDtos.WorldResponse> list() {
        return strategyService.listWorlds();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    StrategyDtos.WorldResponse create(@Valid @RequestBody StrategyDtos.WorldRequest request) {
        return strategyService.createWorld(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    StrategyDtos.WorldResponse update(@PathVariable Long id, @Valid @RequestBody StrategyDtos.WorldRequest request) {
        return strategyService.updateWorld(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void delete(@PathVariable Long id) {
        strategyService.deleteWorld(id);
    }
}
