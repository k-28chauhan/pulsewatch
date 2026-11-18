package com.karaninder.pulseWatch.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Our own, stable JSON shape for a page of results. We do not return Spring's Page object directly,
 * because its JSON layout is an internal detail that can change between versions.
 *
 * @param page zero-based page number
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
