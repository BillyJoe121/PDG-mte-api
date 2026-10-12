package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;
@RestController
@RequestMapping("/api/v2/policies/kr-links")
public class PoliciesV2Controller {
    private final PoliciesV2Service service;
    public PoliciesV2Controller(PoliciesV2Service service) { this.service=service; }
    @GetMapping public Response<List<PoliciesV2Service.Policy>> history() { return list(service.history()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response<PoliciesV2Service.Policy> record(@RequestBody PoliciesV2Service.Write r,@RequestHeader(value="If-Match",required=false) String version) {
        try { long parsed=Long.parseLong(version==null?"":version.replace("\"","")); if(parsed<0) throw new NumberFormatException(); return new Response<>("CREATED",service.record(r,parsed)); }
        catch(NumberFormatException e) { throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED"); }
    }
}
