package co.edu.icesi.pdg.mte.governance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;
@RestController @RequestMapping("/api/v2/report-format-policy")
public class ReportFormatV2Controller {
    private final ReportFormatV2Service service;
    public ReportFormatV2Controller(ReportFormatV2Service service){this.service=service;}
    @GetMapping public Response<List<ReportFormatV2Service.Format>> history(){return list(service.history());}
    @GetMapping("/search") public Response<ReportFormatV2Service.FormatPage> page(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",service.page(page,size));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response<ReportFormatV2Service.Format> record(@RequestBody ReportFormatV2Service.Write r,@RequestHeader(value="If-Match",required=false)String header){
        long expected;try{expected=Long.parseLong(header==null?"":header.replace("\"",""));if(expected<0)throw new NumberFormatException();}catch(NumberFormatException e){throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED");}
        return new Response<>("CREATED",service.record(r,expected));
    }
}
