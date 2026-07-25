package com.gskart.product.exceptionHandlers;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = NoResourceFoundException.class)
    public ProblemDetail noResourceFoundExceptionHandler(NoResourceFoundException exception) {
        // Don't echo the raw exception message (it reveals the internal path); use a generic detail.
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Requested resource was not found.");
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ProblemDetail methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException exception) {
        // Surface the per-field messages as a machine-readable list under "errors", not a joined
        // string, so clients can present them per field.
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    public ProblemDetail constraintViolationExceptionHandler(ConstraintViolationException exception) {
        List<String> errors = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    public ProblemDetail illegalArgumentExceptionHandler(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // Without this, a @PreAuthorize denial (e.g. a Customer calling an admin-only endpoint) falls
    // through to the catch-all handler below and comes back as a 500, not the 403 CODING_STANDARDS
    // calls for - the catch-all is otherwise the first handler broad enough to match
    // AccessDeniedException (uncovered by the m8 authz test in ProductSearchPipelineIntegrationTest).
    @ExceptionHandler(value = AccessDeniedException.class)
    public ProblemDetail accessDeniedExceptionHandler(AccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
    }

    @ExceptionHandler(value = Exception.class)
    public ProblemDetail exceptionHandler(Exception exception) {
        log.error("Unhandled exception while processing request.", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error occurred. Unable to process this request.");
    }
}
