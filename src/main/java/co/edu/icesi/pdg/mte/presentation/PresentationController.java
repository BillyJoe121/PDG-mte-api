package co.edu.icesi.pdg.mte.presentation;

import co.edu.icesi.pdg.mte.api.dto.PresentationDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presentation")
@PreAuthorize("hasAnyRole('ADMIN','DECANO','DIRECTOR_ESCUELA')")
public class PresentationController {
    private final PresentationService presentationService;

    public PresentationController(PresentationService presentationService) {
        this.presentationService = presentationService;
    }

    @GetMapping
    public PresentationDtos.PresentationResponse presentation(@RequestParam(required = false) String period) {
        return presentationService.presentation(period);
    }
}
