package com.ibm.rnd.alexa.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central exception-to-HTTP mapping (RFC 9457 problem details). Messages returned to clients are
 * deliberately generic; details are logged server-side only.
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} so standard Spring MVC errors (415, 400, 405 ...)
 * keep their proper status instead of being swallowed by the catch-all below.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidAlexaRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidAlexaRequestException e) {
        log.warn("Rejected malformed Alexa request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid Alexa request");
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ProblemDetail> handleVerificationFailure(SecurityException e) {
        log.warn("Alexa request verification failed: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid Alexa request");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Error processing Alexa request", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
