package io.github.yagogefaell.asaltamontes.common.pagination;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;

/**
 * Allowed {@code ?sort=} values of one endpoint, mapped to the path of the query that uses them (T14).
 * Anything else is a 400, instead of reaching the JPQL of an @Query method (BUG-012).
 */
public final class SortWhitelist {

    private final Map<String, String> pathsByProperty;
    private final Sort defaultSort;

    /** @param pathsByProperty public property name → path in the query, with its alias (e.g. "name" → "c.name") */
    public SortWhitelist(Map<String, String> pathsByProperty, String defaultProperty) {
        this.pathsByProperty = Map.copyOf(pathsByProperty);
        this.defaultSort = JpaSort.unsafe(Sort.Direction.ASC, pathsByProperty.get(defaultProperty));
    }

    public Pageable apply(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }

        Sort sort = Sort.unsorted();
        for (Sort.Order order : pageable.getSort()) {
            String path = pathsByProperty.get(order.getProperty());
            if (path == null) {
                throw new BadRequestException("Parámetro de ordenación no válido");
            }
            sort = sort.and(JpaSort.unsafe(order.getDirection(), path));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
