package co.edu.icesi.pdg.mte.governance;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v2")
public class GovernanceV2Controller {
    public record Response<T>(String state, T data) {}
    private final GovernanceV2Service service;
    public GovernanceV2Controller(GovernanceV2Service service) { this.service = service; }
    static <T> Response<List<T>> list(List<T> data) { return new Response<>(data.isEmpty() ? "EMPTY" : "READY", data); }
    @GetMapping("/cycles")
    public Response<List<GovernanceV2Service.Cycle>> cycles() { return list(service.cycles()); }
    @PostMapping("/cycles") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Response<GovernanceV2Service.Cycle> createCycle(@RequestBody GovernanceV2Service.CycleWrite r) { return new Response<>("CREATED", service.saveCycle(null, r)); }
    @PutMapping("/cycles/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Response<GovernanceV2Service.Cycle> updateCycle(@PathVariable long id, @RequestBody GovernanceV2Service.CycleWrite r) { return new Response<>("UPDATED", service.saveCycle(id, r)); }
    @GetMapping("/metric-definitions")
    public Response<List<GovernanceV2Service.Metric>> metrics() { return list(service.metrics()); }
    @PostMapping("/metric-definitions") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Response<GovernanceV2Service.Metric> createMetric(@RequestBody GovernanceV2Service.MetricWrite r) { return new Response<>("CREATED", service.saveMetric(null, r)); }
    @PutMapping("/metric-definitions/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Response<GovernanceV2Service.Metric> updateMetric(@PathVariable long id, @RequestBody GovernanceV2Service.MetricWrite r) { return new Response<>("UPDATED", service.saveMetric(id, r)); }
}
