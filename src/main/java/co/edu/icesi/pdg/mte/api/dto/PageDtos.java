package co.edu.icesi.pdg.mte.api.dto;

import java.util.List;

public final class PageDtos {
    private PageDtos() {
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
