package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;
@RestController
@RequestMapping("/api/v2")
public class MonitoringV2Controller {
    private final MonitoringV2Service service;
    public MonitoringV2Controller(MonitoringV2Service service) { this.service=service; }
    @GetMapping("/monitoring-schools") public Response<List<Map<String,Object>>> schools() { return list(service.schools()); }
    @GetMapping("/monitoring-cuts") public Response<List<Map<String,Object>>> cuts() { return list(service.cuts()); }
    @PostMapping("/monitoring-cuts") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> create(@RequestBody MonitoringV2Service.CutWrite r) { return new Response<>("CREATED",service.create(r)); }
    @PostMapping("/monitoring-cuts/{id}/validate") public Response<Map<String,Object>> validate(@PathVariable long id,@RequestHeader(value="If-Match",required=false) String version) { return new Response<>("UPDATED",service.validate(id,version(version))); }
    @PostMapping("/monitoring-cuts/{id}/consolidate") public Response<List<Map<String,Object>>> consolidate(@PathVariable long id,@RequestHeader(value="If-Match",required=false) String version,@RequestBody MonitoringV2Service.ConsolidateWrite r) { return new Response<>("ACCEPTED",service.consolidate(id,version(version),r)); }
    @GetMapping("/monitoring-cuts/{id}/branches") public Response<List<Map<String,Object>>> branches(@PathVariable long id) { return list(service.branches(id)); }
    @GetMapping("/monitoring-cuts/{id}/report") public Response<Map<String,Object>> report(@PathVariable long id,@RequestParam(required=false) Long departmentId) { return new Response<>("READY",service.report(id,departmentId)); }
    @GetMapping("/monitoring-cuts/{id}/report/export.csv") public org.springframework.http.ResponseEntity<byte[]> exportCsv(@PathVariable long id,@RequestParam(required=false) Long departmentId) { return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=\"corte-"+id+".csv\"").header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=utf-8")).body(service.exportCsv(id,departmentId)); }
    @GetMapping("/monitoring-cuts/{id}/departments") public Response<List<Map<String,Object>>> departments(@PathVariable long id) { return list(service.departments(id)); }
    @GetMapping("/monitoring-branches/{id}/entries") public Response<List<Map<String,Object>>> entries(@PathVariable long id) { return list(service.entries(id)); }
    @GetMapping("/monitoring-branches/{id}/department-results") public Response<List<Map<String,Object>>> departmentResults(@PathVariable long id) { return list(service.departmentResults(id)); }
    @GetMapping("/monitoring-branches/{id}/entries/search") public Response<TrackingV2Service.HistoryPage> entryPage(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.entryPage(id,page,size)); }
    @GetMapping("/monitoring-branches/{id}/department-results/{departmentId}/history") public Response<TrackingV2Service.HistoryPage> departmentHistory(@PathVariable long id,@PathVariable long departmentId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.departmentDeliveryPage(id,departmentId,page,size)); }
    @PostMapping("/monitoring-branches/{id}/department-results") public Response<Map<String,Object>> submitDepartment(@PathVariable long id,@RequestHeader(value="If-Match",required=false) String version,@RequestBody MonitoringV2Service.DepartmentDelivery r) { return new Response<>("UPDATED",service.submitDepartment(id,version(version),r)); }
    @GetMapping("/monitoring-situations/{id}") public Response<Map<String,Object>> situation(@PathVariable long id) { return new Response<>("READY",service.situation(id)); }
    @GetMapping("/monitoring-situations/{id}/history/search") public Response<TrackingV2Service.HistoryPage> situationHistory(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",service.situationHistoryPage(id,page,size));}
    @GetMapping("/monitoring-situations/{id}/corrective-actions/{actionId}") public Response<Map<String,Object>> correctiveAction(@PathVariable long id,@PathVariable long actionId){return new Response<>("READY",service.correctiveAction(id,actionId));}
    @GetMapping("/monitoring-situations/{id}/corrective-actions/search") public Response<TrackingV2Service.HistoryPage> correctivePage(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",service.correctivePage(id,page,size));}
    @GetMapping("/monitoring-situations/{id}/corrective-actions/{actionId}/history/search") public Response<TrackingV2Service.HistoryPage> correctiveHistoryPage(@PathVariable long id,@PathVariable long actionId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",service.correctiveHistoryPage(id,actionId,page,size));}
    @GetMapping("/monitoring-situations/{id}/corrective-responsibles") public Response<List<Map<String,Object>>> correctiveResponsibles(@PathVariable long id) { return list(service.correctiveResponsibles(id)); }
    @GetMapping("/monitoring-situations/{id}/corrective-actions") public Response<List<Map<String,Object>>> correctiveActions(@PathVariable long id) { return list(service.correctiveActions(id)); }
    @PostMapping("/monitoring-situations/{id}/corrective-actions") @ResponseStatus(HttpStatus.CREATED) public Response<Map<String,Object>> createCorrective(@PathVariable long id,@RequestBody MonitoringV2Service.CorrectiveWrite r) { return new Response<>("CREATED",service.createCorrective(id,r)); }
    @PatchMapping("/monitoring-situations/{id}/corrective-actions/{actionId}") public Response<Map<String,Object>> transitionCorrective(@PathVariable long id,@PathVariable long actionId,@RequestHeader(value="If-Match",required=false) String version,@RequestBody MonitoringV2Service.CorrectiveTransition r) { return new Response<>("UPDATED",service.transitionCorrective(id,actionId,version(version),r)); }
    @PatchMapping("/monitoring-situations/{id}/status") public Response<Map<String,Object>> transitionSituation(@PathVariable long id,@RequestHeader(value="If-Match",required=false) String version,@RequestBody MonitoringV2Service.SituationTransition r) { return new Response<>("UPDATED",service.transitionSituation(id,version(version),r)); }
    @PostMapping("/monitoring-branches/{id}/entries") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> record(@PathVariable long id,@RequestBody MonitoringV2Service.EntryWrite r) { return new Response<>("CREATED",service.record(id,r,false)); }
    @PostMapping("/monitoring-branches/{id}/review") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> review(@PathVariable long id,@RequestBody MonitoringV2Service.EntryWrite r) { return new Response<>("UPDATED",service.record(id,r,true)); }
    private long version(String value) { try { long parsed=Long.parseLong(value==null?"":value.replace("\"","")); if(parsed<0) throw new NumberFormatException(); return parsed; } catch(NumberFormatException e) { throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED"); } }
}
