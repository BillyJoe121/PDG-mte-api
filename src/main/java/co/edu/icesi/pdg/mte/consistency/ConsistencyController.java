package co.edu.icesi.pdg.mte.consistency;

import co.edu.icesi.pdg.mte.api.dto.ConsistencyDtos;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consistency")
@PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA','JEFE_DPTO')")
public class ConsistencyController {
    private final ConsistencyService consistencyService;

    public ConsistencyController(ConsistencyService consistencyService) {
        this.consistencyService = consistencyService;
    }

    @GetMapping("/check")
    public ConsistencyDtos.ConsistencyCheckResponse check(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer staleDays
    ) {
        return consistencyService.check(severity, module, staleDays);
    }

    @GetMapping(value = "/check/export.csv", produces = "text/csv")
    public ResponseEntity<String> csv(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Integer staleDays
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("msp-consistency-findings.csv")
                        .build()
                        .toString())
                .body(consistencyService.csv(severity, module, staleDays));
    }
}
