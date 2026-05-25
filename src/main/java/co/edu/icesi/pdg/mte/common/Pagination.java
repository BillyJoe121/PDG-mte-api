package co.edu.icesi.pdg.mte.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

public final class Pagination {
    private static final int MAX_PAGE_SIZE = 100;

    private Pagination() {
    }

    public static boolean requested(Integer page, Integer size) {
        return page != null || size != null;
    }

    public static PageRequest pageRequest(Integer page, Integer size, int defaultSize, Sort sort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? defaultSize : size;
        if (pageNumber < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El parametro page debe ser mayor o igual a 0.");
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El parametro size debe estar entre 1 y 100.");
        }
        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
