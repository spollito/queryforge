package dev.queryforge.api.web;

import java.util.Map;

import dev.queryforge.api.connection.TargetDatabaseException;
import dev.queryforge.api.parsing.UnsupportedQueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps failures to RFC 9457 problem details. Each problem carries a stable
 * {@code code} property for clients; the inherited handlers cover request
 * validation (400) and other framework errors.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler
	ProblemDetail unsupportedQuery(UnsupportedQueryException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "Unsupported query", "unsupported-query",
				ex.getMessage());
		if (ex.line() != null) {
			problem.setProperty("line", ex.line());
			problem.setProperty("column", ex.column());
		}
		return problem;
	}

	@ExceptionHandler
	ProblemDetail targetDatabase(TargetDatabaseException ex) {
		return switch (ex.reason()) {
			case UNAVAILABLE -> {
				// The driver message can name hosts and users; log it, return a generic one.
				log.warn("Target database unavailable", ex);
				yield problem(HttpStatus.SERVICE_UNAVAILABLE, "Target database unavailable", "target-unavailable",
						"Could not connect to the target database. Check that it is running and that the "
								+ "TARGET_DB_* settings are correct.");
			}
			case TIMEOUT -> problem(HttpStatus.GATEWAY_TIMEOUT, "Statement timed out", "statement-timeout",
					"The target database stopped the statement after the configured statement timeout.");
			case QUERY_ERROR -> {
				ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "PostgreSQL rejected the query",
						"query-error", ex.getMessage());
				problem.setProperty("sqlState", ex.sqlState());
				if (ex.position() != null) {
					problem.setProperty("position", ex.position());
				}
				yield problem;
			}
		};
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setDetail("The request has invalid fields.");
		problem.setProperty("code", "invalid-request");
		problem.setProperty("errors", ex.getFieldErrors()
			.stream()
			.map((error) -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
			.toList());
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

}
