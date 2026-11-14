package com.codefultm.inventory_management.common.paging;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageRequestFactory {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String DEFAULT_SORT = "createdAt";

    public static Pageable fromParams(
            Integer page,
            Integer size,
            String sort,
            String direction
    ) {
        int p = page != null ? page : DEFAULT_PAGE;
        int s = size != null ? Math.min(size, 100) : DEFAULT_SIZE;
        String sortBy = sort != null ? sort : DEFAULT_SORT;

        Sort.Direction dir = "DESC".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return PageRequest.of(p, s, dir, sortBy);
    }

    public static Pageable defaultPage() {
        return fromParams(null, null, null, null);
    }
}
