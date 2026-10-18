package com.fixhomi.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

/**
 * Turns bad support input into a clean 400 instead of the base app's catch-all 500.
 * It only handles the exceptions listed below and only for classes in the support controller
 * package, so the existing GlobalExceptionHandler behaves exactly as before for everything else.
 */
@RestControllerAdvice(basePackages = "com.fixhomi.auth.controller.support")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SupportExceptionHandler {

    @ExceptionHandler(SupportBadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(SupportBadRequestException ex, HttpServletRequest request) {
        return build(ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build("Invalid value for '" + ex.getName() + "'." + (ex.getRequiredType() == java.time.LocalDate.class ? " Dates must look like 2026-10-16." : ""), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissing(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build("Missing required parameter '" + ex.getParameterName() + "'.", request);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build("Request body is missing or has an invalid value.", request);
    }

    private ResponseEntity<ErrorResponse> build(String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", message, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}