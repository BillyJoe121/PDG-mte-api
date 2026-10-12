package co.edu.icesi.pdg.mte.governance;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static co.edu.icesi.pdg.mte.governance.GovernanceV2Controller.*;
@RestController @RequestMapping("/api/v2")
public class SchoolReportV2Controller {
    private final SchoolReportV2Service service;public SchoolReportV2Controller(SchoolReportV2Service service){this.service=service;}
    @GetMapping("/monitoring-cuts/{id}/final-reports") public Response<List<Map<String,Object>>> listReports(@PathVariable long id){return list(service.list(id));}
    @GetMapping("/monitoring-cuts/{id}/final-reports/search") public Response<SchoolReportV2Service.ReportPage> reportPage(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",service.reportPage(id,page,size));}
    @GetMapping("/school-final-reports/{id}") public Response<Map<String,Object>> get(@PathVariable long id){return new Response<>("READY",service.get(id));}
    @GetMapping("/school-final-reports/{id}/reviews/search") public Response<SchoolReportV2Service.ReviewPage> reviews(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return new Response<>("READY",service.reviews(id,page,size));}
    @PostMapping("/school-final-reports/{id}/reviews") @ResponseStatus(HttpStatus.CREATED) public Response<SchoolReportV2Service.ReviewReceipt> review(@PathVariable long id,@RequestBody SchoolReportV2Service.Review body,@RequestHeader(value="If-Match",required=false)String version){return new Response<>("UPDATED",service.review(id,expected(version),body));}
    @PostMapping("/school-final-reports/{id}/finish") public Response<Map<String,Object>> finish(@PathVariable long id,@RequestBody SchoolReportV2Service.Finish body,@RequestHeader(value="If-Match",required=false)String version){return new Response<>("CLOSED",service.finish(id,expected(version),body));}
    private long expected(String version){try{long parsed=Long.parseLong(version==null?"":version.replace("\"",""));if(parsed<0)throw new NumberFormatException();return parsed;}catch(NumberFormatException e){throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED");}}
    @PostMapping("/school-final-reports/{id}/publish") public Response<Map<String,Object>> publish(@PathVariable long id,@RequestBody SchoolReportV2Service.Publish body,@RequestHeader(value="If-Match",required=false)String version){long expected;try{expected=Long.parseLong(version==null?"":version.replace("\"",""));if(expected<0)throw new NumberFormatException();}catch(NumberFormatException e){throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED");}return new Response<>("READY",service.publish(id,expected,body));}
    @PostMapping("/school-final-reports") @ResponseStatus(HttpStatus.CREATED) public Response<Map<String,Object>> create(@RequestBody SchoolReportV2Service.Draft r,@RequestHeader(value="If-Match",required=false)String version){long expected;try{expected=Long.parseLong(version==null?"":version.replace("\"",""));if(expected<0)throw new NumberFormatException();}catch(NumberFormatException e){throw GovernanceV2Service.error(HttpStatus.UNPROCESSABLE_ENTITY,"VERSION_REQUIRED");}return new Response<>("CREATED",service.create(r,expected));}
}
