package com.gskart.product.exceptionHandlers;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

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
        // Surface only the per-field validation messages, not the framework's internal binding dump.
        String details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                details.isEmpty() ? "Request validation failed." : details);
        problem.setTitle("Validation failed");
        return problem;
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    public ProblemDetail constraintViolationExceptionHandler(ConstraintViolationException exception) {
        String details = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                details.isEmpty() ? "Request validation failed." : details);
        problem.setTitle("Validation failed");
        return problem;
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    public ProblemDetail illegalArgumentExceptionHandler(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(value = Exception.class)
    public ProblemDetail exceptionHandler(Exception exception) {
        log.error("Unhandled exception while processing request.", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error occurred. Unable to process this request.");
    }
}
