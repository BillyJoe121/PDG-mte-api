package co.edu.icesi.pdg.mte.governance;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;
@RestController @RequestMapping("/api/v2/consistency")
public class ConsistencyV2Controller{
    private final ConsistencyV2Service service;public ConsistencyV2Controller(ConsistencyV2Service service){this.service=service;}
    @GetMapping("/departments") public Response<List<Map<String,Object>>> departments(){return list(service.departments());}
    @GetMapping("/check") public Response<Map<String,Object>> check(@RequestParam(required=false)Long cycleId,@RequestParam(required=false)Long departmentId){return new Response<>("READY",service.check(cycleId,departmentId));}
}
