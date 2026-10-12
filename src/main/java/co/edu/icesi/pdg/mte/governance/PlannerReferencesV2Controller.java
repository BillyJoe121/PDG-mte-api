package co.edu.icesi.pdg.mte.governance;

import org.springframework.web.bind.annotation.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.Response;

@RestController
@RequestMapping("/api/v2/initiatives/{id}/planner-references")
public class PlannerReferencesV2Controller {
    private final PlannerReferencesV2Service service;
    public PlannerReferencesV2Controller(PlannerReferencesV2Service service) { this.service=service; }
    @GetMapping
    public Response<PlannerReferencesV2Service.Page> list(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return new Response<>("READY",service.list(id,page,size));
    }
}
