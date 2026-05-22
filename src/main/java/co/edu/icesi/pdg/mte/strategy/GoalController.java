package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final StrategyService strategyService;

    public GoalController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    List<StrategyDtos.GoalResponse> list(@RequestParam(required = false) String period) {
        return strategyService.listGoals(period);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
    StrategyDtos.GoalResponse create(@Valid @RequestBody StrategyDtos.GoalRequest request) {
        return strategyService.createGoal(request);
    }

    @GetMapping("/{id}")
    StrategyDtos.GoalResponse get(@PathVariable Long id, @RequestParam(required = false) String period) {
        return strategyService.getGoal(id, period);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
    StrategyDtos.GoalResponse update(
            @PathVariable Long id,
            @Valid @RequestBody StrategyDtos.GoalRequest request
    ) {
        return strategyService.updateGoal(id, request);
    }

    @PostMapping("/{id}/periods/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
    StrategyDtos.GoalResponse attachPeriod(@PathVariable Long id, @PathVariable Long periodId) {
        return strategyService.attachGoalPeriod(id, periodId);
    }

    @DeleteMapping("/{id}/periods/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
    StrategyDtos.GoalResponse detachPeriod(@PathVariable Long id, @PathVariable Long periodId) {
        return strategyService.detachGoalPeriod(id, periodId);
    }
}
