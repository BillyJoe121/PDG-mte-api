package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;

@RestController
@RequestMapping("/api/v2")
public class EvidenceV2Controller {
    private final EvidenceV2Service service;
    public EvidenceV2Controller(EvidenceV2Service service) { this.service=service; }
    @GetMapping("/measurements/{id}/evidence")
    public Response<List<Map<String,Object>>> measurements(@PathVariable long id) { return list(service.list("measurement",id)); }
    @GetMapping("/initiatives/{id}/evidence")
    public Response<List<Map<String,Object>>> initiatives(@PathVariable long id) { return list(service.list("initiative",id)); }
    @GetMapping("/measurements/{id}/evidence/search")
    public Response<EvidenceV2Service.Page> searchMeasurements(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String evidenceType,@RequestParam(required=false) Boolean active) { return new Response<>("READY",service.search("measurement",id,page,size,evidenceType,active)); }
    @GetMapping("/initiatives/{id}/evidence/search")
    public Response<EvidenceV2Service.Page> searchInitiatives(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String evidenceType,@RequestParam(required=false) Boolean active) { return new Response<>("READY",service.search("initiative",id,page,size,evidenceType,active)); }
    @PostMapping("/measurements/{id}/evidence") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> createMeasurement(@PathVariable long id,@RequestBody EvidenceV2Service.Write r) { return new Response<>("CREATED",service.create("measurement",id,r)); }
    @PostMapping("/initiatives/{id}/evidence") @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> createInitiative(@PathVariable long id,@RequestBody EvidenceV2Service.Write r) { return new Response<>("CREATED",service.create("initiative",id,r)); }
}
