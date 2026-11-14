package com.codefultm.inventory_management.common.paging;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements
) {}
