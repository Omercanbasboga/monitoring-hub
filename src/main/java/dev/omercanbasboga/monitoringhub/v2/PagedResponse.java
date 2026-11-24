package dev.omercanbasboga.monitoringhub.v2;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Fixed page envelope. Spring's own Page JSON has changed shape between
 * versions (and Boot 3.3 warns about it), so the client gets this instead.
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                               boolean first, boolean last) {

    public static <T> PagedResponse<T> of(Page<T> p) {
        return new PagedResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                p.getTotalPages(), p.isFirst(), p.isLast());
    }
}
