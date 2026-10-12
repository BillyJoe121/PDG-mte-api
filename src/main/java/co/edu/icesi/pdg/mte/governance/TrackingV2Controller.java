package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;

@RestController
@RequestMapping("/api/v2")
public class TrackingV2Controller {
    private final TrackingV2Service service;
    public TrackingV2Controller(TrackingV2Service service) { this.service = service; }
    @GetMapping("/key-results") public Response<List<TrackingV2Service.Kr>> keyResults() { return list(service.keyResults()); }
    @GetMapping("/key-results/{id}/tracking") public Response<TrackingV2Service.Kr> summary(@PathVariable long id) { return new Response<>("READY",service.keyResultSummary(id)); }
    @GetMapping("/key-results/{id}/measurements") public Response<List<Map<String, Object>>> measurements(@PathVariable long id) { return list(service.measurements(id)); }
    @GetMapping("/key-results/{id}/progress") public Response<List<Map<String, Object>>> progress(@PathVariable long id) { return list(service.progress(id)); }
    @GetMapping("/key-results/{id}/measurements/search") public Response<TrackingV2Service.HistoryPage> measurementPage(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.measurementPage(id,page,size)); }
    @GetMapping("/key-results/{id}/progress/search") public Response<TrackingV2Service.HistoryPage> progressPage(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.progressPage(id,page,size)); }
    @PostMapping("/key-results/{id}/measurements") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String, Object>> measure(@PathVariable long id, @RequestBody TrackingV2Service.MeasurementWrite r) { return new Response<>("CREATED", service.measure(id, r)); }
    @PostMapping("/key-results/{id}/progress") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String, Object>> declare(@PathVariable long id, @RequestBody TrackingV2Service.ProgressWrite r) { return new Response<>("UPDATED", service.declareProgress(id, r)); }
    @GetMapping("/initiative-responsibles") public Response<List<Map<String, Object>>> responsibles() { return list(service.responsibles()); }
    @GetMapping("/initiatives") public Response<List<Map<String, Object>>> initiatives() { return list(service.initiatives()); }
    @GetMapping("/initiatives/search") public Response<TrackingV2Service.HistoryPage> initiativesPage(@RequestParam(required=false) String text,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.initiativePage(text,status,page,size)); }
    @GetMapping("/initiatives/{id}") public Response<Map<String, Object>> initiative(@PathVariable long id) { return new Response<>("READY", service.getInitiative(id)); }
    @PostMapping("/key-results/{id}/initiatives") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String, Object>> createInitiative(@PathVariable long id, @RequestBody TrackingV2Service.InitiativeWrite r) { return new Response<>("CREATED", service.createInitiative(id, r)); }
    @GetMapping("/initiatives/{id}/progress") public Response<List<Map<String, Object>>> initiativeProgress(@PathVariable long id) { return list(service.initiativeProgress(id)); }
    @GetMapping("/initiatives/{id}/progress/search") public Response<TrackingV2Service.HistoryPage> initiativeProgressPage(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.initiativeProgressPage(id,page,size)); }
    @PostMapping("/initiatives/{id}/progress") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String, Object>> declareInitiative(@PathVariable long id, @RequestBody TrackingV2Service.ProgressWrite r) { return new Response<>("UPDATED", service.declareInitiative(id, r)); }
    @PatchMapping("/initiatives/{id}/active")
    public Response<Map<String, Object>> deactivate(@PathVariable long id, @RequestBody TrackingV2Service.DeactivateWrite r) { return new Response<>("DEACTIVATED", service.deactivate(id, r)); }
    @GetMapping("/initiatives/{id}/assignments") public Response<List<Map<String,Object>>> assignments(@PathVariable long id) { return list(service.initiativeAssignments(id)); }
    @GetMapping("/initiatives/{id}/assignments/search") public Response<TrackingV2Service.HistoryPage> assignmentPage(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.initiativeAssignmentPage(id,page,size)); }
    @PutMapping("/initiatives/{id}/assignee")
    public Response<Map<String,Object>> reassign(@PathVariable long id,@RequestBody TrackingV2Service.OwnerWrite r,@RequestHeader(value="If-Match",required=false) String version) {
        try { long parsed=Long.parseLong(version==null?"":version.replace("\"","")); if(parsed<0) throw new NumberFormatException(); return new Response<>("UPDATED",service.reassignInitiative(id,r,parsed)); }
        catch(NumberFormatException e) { throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED"); }
    }
}
