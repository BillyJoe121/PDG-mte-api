package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.Response;
@RestController @RequestMapping("/api/v2")
public class TargetApprovalV2Controller {
    private final ApprovalPolicyV2Service policies;private final TargetApprovalV2Service targets;
    public TargetApprovalV2Controller(ApprovalPolicyV2Service policies,TargetApprovalV2Service targets){this.policies=policies;this.targets=targets;}
    private long version(String header){if(header==null)throw GovernanceV2Service.error(HttpStatus.PRECONDITION_REQUIRED,"VERSION_REQUIRED");try{long value=Long.parseLong(header.replace("\"",""));if(value<0)throw new NumberFormatException();return value;}catch(NumberFormatException e){throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED");}}
    @GetMapping("/target-approval-policy/search") public Response<ApprovalPolicyV2Service.Page> policies(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",policies.page(page,size));}
    @PostMapping("/target-approval-policy") @ResponseStatus(HttpStatus.CREATED)
    public Response<ApprovalPolicyV2Service.Policy> record(@RequestBody ApprovalPolicyV2Service.Write r,@RequestHeader(value="If-Match",required=false)String header){return new Response<>("CREATED",policies.record(r,version(header)));}
    @GetMapping("/key-results/{id}/target-requests") public Response<TargetApprovalV2Service.Page> requests(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",targets.page(id,page,size));}
    @PatchMapping("/key-results/{id}/target")
    public Response<Map<String,Object>> change(@PathVariable long id,@RequestBody TargetApprovalV2Service.Write r,@RequestHeader(value="If-Match",required=false)String header,@RequestHeader(value="Idempotency-Key",required=false)String key){return new Response<>("UPDATED",targets.change(id,r,version(header),key));}
    @PostMapping("/key-results/{id}/target-requests") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> request(@PathVariable long id,@RequestBody TargetApprovalV2Service.Write r,@RequestHeader(value="If-Match",required=false)String header,@RequestHeader(value="Idempotency-Key",required=false)String key){var result=targets.request(id,r,version(header),key);return new Response<>("PENDING".equals(result.get("status"))?"ACCEPTED":"UPDATED",result);}
    @PostMapping("/key-results/{id}/target-requests/{request}/decision")
    public Response<Map<String,Object>> decide(@PathVariable long id,@PathVariable long request,@RequestBody TargetApprovalV2Service.Decision r,@RequestHeader(value="If-Match",required=false)String header){return new Response<>("UPDATED",targets.decide(id,request,r,version(header)));}
}
