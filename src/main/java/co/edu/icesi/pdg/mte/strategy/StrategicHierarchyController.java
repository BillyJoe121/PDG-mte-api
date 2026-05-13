package co.edu.icesi.pdg.mte.strategy;

import co.edu.icesi.pdg.mte.api.dto.StrategyDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/strategic-hierarchy")
public class StrategicHierarchyController {

    private final StrategyService strategyService;

    public StrategicHierarchyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping("/tree")
    List<StrategyDtos.StrategicHierarchyNodeResponse> tree(@RequestParam(required = false) String period) {
        return strategyService.getStrategicHierarchyTree(period);
    }
}
