package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;
@RestController
@RequestMapping("/api/v2/initiatives/{id}/execution")
public class ExecutionV2Controller {
    private final ExecutionV2Service service;
    public ExecutionV2Controller(ExecutionV2Service service) { this.service=service; }
    @GetMapping public Response<List<Map<String,Object>>> listEntries(@PathVariable long id) { return list(service.list(id)); }
    @GetMapping("/search") public Response<ExecutionV2Service.Page> search(@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return new Response<>("READY",service.search(id,page,size)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response<Map<String,Object>> create(@PathVariable long id,@RequestBody ExecutionV2Service.Write r) { return new Response<>("CREATED",service.create(id,r)); }
}
