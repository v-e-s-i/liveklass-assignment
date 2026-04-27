package com.example.liveklass.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(ex.getStatus()).body(ErrorResponse.of(ex.getStatus(), code));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        ErrorCode code = mapValidation(ex);
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageParseErrors(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_DATE_FORMAT));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(mapRequestParam(ex.getName(), true)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(mapRequestParam(ex.getParameterName(), false)));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ErrorCode mapValidation(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::mapFieldError)
                .orElse(ErrorCode.INVALID_DATE_FORMAT);
    }

    private ErrorCode mapFieldError(FieldError error) {
        String field = error.getField();
        if ("refundAmount".equals(field)) {
            return ErrorCode.INVALID_REFUND_AMOUNT;
        }
        if (field.toLowerCase().contains("amount")) {
            return ErrorCode.INVALID_AMOUNT;
        }
        return ErrorCode.INVALID_REQUEST;
    }

    private ErrorCode mapRequestParam(String name, boolean malformed) {
        if ("yearMonth".equals(name)) {
            return ErrorCode.INVALID_YEAR_MONTH;
        }
        if ("from".equals(name) || "to".equals(name)) {
            return malformed ? ErrorCode.INVALID_DATE_FORMAT : ErrorCode.INVALID_DATE_RANGE;
        }
        return ErrorCode.INVALID_REQUEST;
    }
}
