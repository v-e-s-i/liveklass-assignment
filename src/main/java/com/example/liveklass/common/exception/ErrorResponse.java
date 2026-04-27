package com.example.liveklass.common.exception;

import com.example.liveklass.common.TimeUtils;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;

public record ErrorResponse(int status, String code, String message, OffsetDateTime timestamp) {
    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code.status().value(), code.name(), code.message(), TimeUtils.nowKst().toOffsetDateTime());
    }

    public static ErrorResponse of(HttpStatus status, ErrorCode code) {
        return new ErrorResponse(status.value(), code.name(), code.message(), TimeUtils.nowKst().toOffsetDateTime());
    }
}
