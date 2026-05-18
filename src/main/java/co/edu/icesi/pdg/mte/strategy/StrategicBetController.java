package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategic-bets")
public class StrategicBetController {

    private final StrategyService strategyService;

    public StrategicBetController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    List<StrategyDtos.StrategicBetResponse> list(@RequestParam(required = false) String period) {
        return strategyService.listStrategicBets(period);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
    StrategyDtos.StrategicBetResponse create(@Valid @RequestBody StrategyDtos.StrategicBetRequest request) {
        return strategyService.createStrategicBet(request);
    }

    @GetMapping("/{id}")
    StrategyDtos.StrategicBetResponse get(@PathVariable Long id, @RequestParam(required = false) String period) {
        return strategyService.getStrategicBet(id, period);
    }
}
