package co.edu.icesi.pdg.mte.dashboard;

import co.edu.icesi.pdg.mte.api.dto.DashboardDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','CONTRIBUTOR','VISITOR')")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardDtos.DashboardSummaryResponse summary(@RequestParam(required = false) String period) {
        return dashboardService.summary(period);
    }

    @GetMapping("/projects/by-status")
    public List<DashboardDtos.CountByStatusResponse> projectsByStatus(@RequestParam(required = false) String period) {
        return dashboardService.projectsByStatus(period);
    }

    @GetMapping("/key-results/by-status")
    public List<DashboardDtos.CountByStatusResponse> keyResultsByStatus(@RequestParam(required = false) String period) {
        return dashboardService.keyResultsByStatus(period);
    }

    @GetMapping("/departments/summary")
    public List<DashboardDtos.DepartmentExecutionResponse> departments(@RequestParam(required = false) String period) {
        return dashboardService.departments(period);
    }

    @GetMapping("/strategic-bets/summary")
    public List<DashboardDtos.StrategicBetExecutionResponse> strategicBets(@RequestParam(required = false) String period) {
        return dashboardService.strategicBets(period);
    }

    @GetMapping("/goals/summary")
    public List<DashboardDtos.GoalExecutionResponse> goals(@RequestParam(required = false) String period) {
        return dashboardService.goals(period);
    }
}
