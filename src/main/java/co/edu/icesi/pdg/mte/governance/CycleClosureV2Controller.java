package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.Response;
@RestController @RequestMapping("/api/v2/cycles/{id}")
public class CycleClosureV2Controller {
    private final CycleClosureV2Service service;public CycleClosureV2Controller(CycleClosureV2Service service){this.service=service;}
    @GetMapping("/closure")public Response<Map<String,Object>> context(@PathVariable long id){return new Response<>("READY",service.context(id));}
    @GetMapping("/closure/snapshots")public Response<CycleClosureV2Service.Page> snapshots(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",service.snapshots(id,page,size));}
    @PostMapping("/close")public Response<Map<String,Object>> close(@PathVariable long id,@RequestBody CycleClosureV2Service.Write r,@RequestHeader(value="If-Match",required=false)String header,@RequestHeader(value="Idempotency-Key",required=false)String key){if(header==null)throw GovernanceV2Service.error(HttpStatus.PRECONDITION_REQUIRED,"VERSION_REQUIRED");long version;try{version=Long.parseLong(header.replace("\"",""));if(version<0)throw new NumberFormatException();}catch(Exception e){throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED");}return new Response<>("CLOSED",service.close(id,r,version,key));}
}
