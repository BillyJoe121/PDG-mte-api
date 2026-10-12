package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Service.error;

@RestController
@RequestMapping("/api/v2")
public class StrategyV2Controller {
    private final StrategyV2Service service;
    private final CycleClosureV2Service closure;
    public StrategyV2Controller(StrategyV2Service service,CycleClosureV2Service closure) { this.service=service;this.closure=closure; }
    @GetMapping("/strategy-choices") public Response<Map<String,Object>> choices() { return new Response<>("READY",service.choices()); }
    @GetMapping("/key-results/search")
    public Response<StrategyV2Service.Page> search(@RequestParam(required=false) String text,@RequestParam(required=false) Long cycleId,@RequestParam(required=false) String status,@RequestParam(required=false) Long responsibleId,@RequestParam(required=false) Long goalId,@RequestParam(required=false) Long betId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var data=service.search(text,cycleId,status,responsibleId,goalId,betId,page,size); return new Response<>(data.items().isEmpty()?"EMPTY":"READY",data);
    }
    @GetMapping("/key-results/{id}") public Response<Map<String,Object>> get(@PathVariable long id) { return new Response<>("READY",service.get(id)); }
    @PostMapping("/key-results") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> create(@RequestBody StrategyV2Service.Create r,@RequestHeader(value="Idempotency-Key",required=false) String key) { return new Response<>("CREATED",service.create(r,key)); }
    @PatchMapping("/key-results/{id}")
    public Response<Map<String,Object>> revise(@PathVariable long id,@RequestBody StrategyV2Service.Revision r,@RequestHeader(value="If-Match",required=false) String version) {
        try { return new Response<>("UPDATED",service.revise(id,r,Long.parseLong(version==null?"":version.replace("\"","")))); }
        catch(NumberFormatException e) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED"); }
    }
    @GetMapping("/key-results/{id}/versions") public Response<List<Map<String,Object>>> versions(@PathVariable long id) { return list(service.versions(id)); }
    @GetMapping("/key-results/{id}/versions/search") public Response<TrackingV2Service.HistoryPage> versionPage(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.versionPage(id,page,size)); }
    @GetMapping("/key-results/{id}/relations") public Response<Map<String,Object>> relations(@PathVariable long id) { return new Response<>("READY",service.relations(id)); }
    @GetMapping("/key-results/{id}/relations/{kind}") public Response<TrackingV2Service.HistoryPage> relationPage(@PathVariable long id,@PathVariable String kind,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.relationPage(id,kind,page,size)); }
    @PostMapping("/key-results/{id}/{kind:goals|bets}") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> link(@PathVariable long id,@PathVariable String kind,@RequestBody StrategyV2Service.LinkWrite r,@RequestHeader(value="If-Match",required=false) String version) { return new Response<>("CREATED",service.addLink(id,kind,r,version(version))); }
    @PatchMapping("/key-results/{id}/{kind:goals|bets}/{linkId}")
    public Response<Map<String,Object>> unlink(@PathVariable long id,@PathVariable String kind,@PathVariable long linkId,@RequestBody Map<String,String> r,@RequestHeader(value="If-Match",required=false) String version) { return new Response<>("DEACTIVATED",service.removeLink(id,kind,linkId,r.get("reason"),version(version))); }
    @PutMapping("/key-results/{id}/assignee")
    public Response<Map<String,Object>> reassign(@PathVariable long id,@RequestBody StrategyV2Service.OwnerWrite r,@RequestHeader(value="If-Match",required=false) String version) { return new Response<>("UPDATED",service.reassign(id,r,version(version))); }
    @PatchMapping("/key-results/{id}/type")
    public Response<Map<String,Object>> classify(@PathVariable long id,@RequestBody StrategyV2Service.TypeWrite r,@RequestHeader(value="If-Match",required=false) String version) { return new Response<>("UPDATED",service.classify(id,r,version(version))); }
    @PatchMapping("/key-results/{id}/active")
    public Response<Map<String,Object>> deactivate(@PathVariable long id,@RequestBody StrategyV2Service.DeactivateWrite r,@RequestHeader(value="If-Match",required=false) String version) { return new Response<>("DEACTIVATED",service.deactivate(id,r,version(version))); }
    @GetMapping("/key-results/{id}/activation-check") public Response<StrategyV2Service.ActivationCheck> activationCheck(@PathVariable long id) { return new Response<>("READY",service.activationCheck(id)); }
    @PatchMapping("/key-results/{id}/status")
    public Response<Map<String,Object>> status(@PathVariable long id,@RequestBody StrategyV2Service.StatusWrite r,@RequestHeader(value="If-Match",required=false) String version,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        if("CLOSED".equals(r.newStatus())){
            if(version==null)throw error(HttpStatus.PRECONDITION_REQUIRED,"VERSION_REQUIRED");
            return new Response<>("CLOSED",closure.closeKr(id,r,version(version),key));
        }
        return new Response<>("INACTIVE".equals(r.newStatus()) ? "DEACTIVATED" : "UPDATED",service.transition(id,r,version(version)));
    }
    @GetMapping("/key-results/{id}/closure") public Response<Map<String,Object>> closure(@PathVariable long id){return new Response<>("READY",closure.krContext(id));}
    private long version(String value) {
        try { long number=Long.parseLong(value==null?"":value.replace("\"","")); if(number<0) throw new NumberFormatException(); return number; }
        catch(NumberFormatException e) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED"); }
    }
}
