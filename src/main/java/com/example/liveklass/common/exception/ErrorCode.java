package com.example.liveklass.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "날짜 형식이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "yearMonth 형식이 올바르지 않습니다. 예: 2025-03"),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "from은 to보다 이후일 수 없습니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "금액은 1 이상이어야 합니다."),
    FUTURE_PAID_AT(HttpStatus.BAD_REQUEST, "paidAt은 현재 시각 이후일 수 없습니다."),
    INVALID_REFUND_AMOUNT(HttpStatus.BAD_REQUEST, "환불 금액이 올바르지 않습니다."),
    INVALID_CANCEL_DATE(HttpStatus.UNPROCESSABLE_ENTITY, "canceledAt은 paidAt 이후여야 합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CREATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "크리에이터를 찾을 수 없습니다."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),
    SALE_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "판매 내역을 찾을 수 없습니다."),
    DUPLICATE_CREATOR(HttpStatus.CONFLICT, "이미 존재하는 크리에이터 ID입니다."),
    DUPLICATE_COURSE(HttpStatus.CONFLICT, "이미 존재하는 강의 ID입니다."),
    DUPLICATE_SALE_RECORD(HttpStatus.CONFLICT, "이미 존재하는 판매 내역 ID입니다."),
    ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소된 판매 내역입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
