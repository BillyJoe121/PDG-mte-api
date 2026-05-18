package co.edu.icesi.pdg.mte.api.dto;

import java.util.List;
import java.util.Map;

public final class PresentationDtos {
    private PresentationDtos() {
    }

    public record PresentationResponse(
            String period,
            String title,
            PresentationControlsResponse controls,
            List<PresentationSlideResponse> slides
    ) {
    }

    public record PresentationControlsResponse(
            boolean fullscreenEnabled,
            boolean keyboardNavigationEnabled,
            List<String> nextKeys,
            List<String> previousKeys,
            List<String> exitKeys
    ) {
    }

    public record PresentationSlideResponse(
            int order,
            String type,
            String title,
            String subtitle,
            Map<String, Object> content
    ) {
    }
}
