package co.edu.icesi.pdg.mte.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        int statusCode,
        String message,
        List<String> details,
        Instant timestamp,
        String path
) {
}
