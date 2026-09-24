package com.mk.movieticketbooking.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response body. Serialized as JSON on any handled exception.
 * Fields the client can rely on:
 * <ul>
 *   <li><b>timestamp</b>: server-side timestamp of the failure.</li>
 *   <li><b>status</b>: numeric HTTP status.</li>
 *   <li><b>error</b>: short machine-readable code (e.g. NOT_FOUND, VALIDATION_FAILED).</li>
 *   <li><b>message</b>: human-readable summary.</li>
 *   <li><b>path</b>: request URI.</li>
 *   <li><b>traceId</b>: correlation id for log lookup.</li>
 *   <li><b>fieldErrors</b>: per-field validation issues (only present on 400s from bean validation).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String traceId,
    List<FieldError> fieldErrors) {

  public record FieldError(String field, String message) {}
}
