package com.karaninder.pulseWatch.common;

import com.karaninder.pulseWatch.auth.EmailAlreadyRegisteredException;
import com.karaninder.pulseWatch.auth.InvalidCredentialsException;
import com.karaninder.pulseWatch.auth.TooManyAttemptsException;
import com.karaninder.pulseWatch.monitor.ForbiddenTargetException;
import com.karaninder.pulseWatch.monitor.MonitorNotFoundException;
import com.karaninder.pulseWatch.stats.InvalidStatsWindowException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One place that turns exceptions from ANY controller into clean JSON errors.
 * ProblemDetail is Spring's built-in implementation of the RFC 9457 standard error format
 * (fields: type, title, status, detail, instance, plus any extras we add).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MonitorNotFoundException.class)
	public ProblemDetail handleNotFound(MonitorNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ForbiddenTargetException.class)
	public ProblemDetail handleForbiddenTarget(ForbiddenTargetException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", Map.of("url", ex.getMessage())); // shown next to the URL field in the UI
		return problem;
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	public ProblemDetail handleEmailTaken(EmailAlreadyRegisteredException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(TooManyAttemptsException.class)
	public ResponseEntity<ProblemDetail> handleRateLimited(TooManyAttemptsException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ProblemDetail handleBadCredentials(InvalidCredentialsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(InvalidStatsWindowException.class)
	public ProblemDetail handleInvalidWindow(InvalidStatsWindowException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/** Thrown when @Valid fails. We list every bad field, not just the first. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	/** Malformed JSON, or a value that doesn't fit the type (e.g. method: "GTE"). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed or unreadable JSON request body");
	}

	/** e.g. GET /api/monitors/abc where an id (number) was expected. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Parameter '" + ex.getName() + "' has an invalid value: " + ex.getValue());
	}
}
