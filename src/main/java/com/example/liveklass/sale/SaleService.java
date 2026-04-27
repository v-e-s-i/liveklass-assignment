package com.example.liveklass.sale;

import com.example.liveklass.common.TimeUtils;
import com.example.liveklass.common.exception.BusinessException;
import com.example.liveklass.common.exception.ErrorCode;
import com.example.liveklass.course.Course;
import com.example.liveklass.course.CourseRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class SaleService {
    private final SaleRecordRepository saleRecordRepository;
    private final CancelRecordRepository cancelRecordRepository;
    private final CourseRepository courseRepository;

    public SaleService(SaleRecordRepository saleRecordRepository, CancelRecordRepository cancelRecordRepository, CourseRepository courseRepository) {
        this.saleRecordRepository = saleRecordRepository;
        this.cancelRecordRepository = cancelRecordRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public SaleRecordResponse create(CreateSaleRecordRequest request) {
        if (saleRecordRepository.existsById(request.id())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SALE_RECORD);
        }
        if (request.paidAt().toInstant().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.FUTURE_PAID_AT);
        }
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        SaleRecord sale = saleRecordRepository.save(new SaleRecord(
                request.id(), course, request.studentId(), request.amount(), request.paidAt().toInstant(), TimeUtils.nowKst().toInstant()));
        return SaleRecordResponse.from(sale, false);
    }

    @Transactional
    public CancelRecordResponse cancel(String saleRecordId, CancelSaleRecordRequest request) {
        SaleRecord sale = saleRecordRepository.findById(saleRecordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_RECORD_NOT_FOUND));
        if (cancelRecordRepository.existsBySaleRecordId(saleRecordId)) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }
        if (request.refundAmount() > sale.getAmount()) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (request.canceledAt().toInstant().isBefore(sale.getPaidAt())) {
            throw new BusinessException(ErrorCode.INVALID_CANCEL_DATE);
        }
        CancelRecord cancel = cancelRecordRepository.save(new CancelRecord(
                sale, request.refundAmount(), request.canceledAt().toInstant(), TimeUtils.nowKst().toInstant()));
        return CancelRecordResponse.from(cancel);
    }

    @Transactional(readOnly = true)
    public SaleRecordPageResponse list(String creatorId, String headerCreatorId, LocalDate from, LocalDate to, int page, int size) {
        if (headerCreatorId != null && !headerCreatorId.isBlank() && !headerCreatorId.equals(creatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDate startDate = from == null ? LocalDate.of(1900, 1, 1) : from;
        LocalDate endDate = to == null ? LocalDate.of(9999, 12, 31) : to;
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paidAt"));
        var result = saleRecordRepository.findByCourseCreatorIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                creatorId, TimeUtils.startOfDayKst(startDate), TimeUtils.startOfNextDayKst(endDate), pageable);
        return SaleRecordPageResponse.from(result);
    }

    public record CreateSaleRecordRequest(
            @NotBlank String id,
            @NotBlank String courseId,
            @NotBlank String studentId,
            @Min(1) long amount,
            @NotNull OffsetDateTime paidAt
    ) {
    }

    public record CancelSaleRecordRequest(@Min(1) long refundAmount, @NotNull OffsetDateTime canceledAt) {
    }

    public record SaleRecordResponse(
            String id, String courseId, String creatorId, String studentId, long amount,
            OffsetDateTime paidAt, boolean canceled, OffsetDateTime createdAt
    ) {
        static SaleRecordResponse from(SaleRecord sale, boolean canceled) {
            return new SaleRecordResponse(
                    sale.getId(), sale.getCourse().getId(), sale.getCourse().getCreator().getId(), sale.getStudentId(),
                    sale.getAmount(), TimeUtils.toKst(sale.getPaidAt()), canceled, TimeUtils.toKst(sale.getCreatedAt()));
        }
    }

    public record CancelRecordResponse(String saleRecordId, long refundAmount, OffsetDateTime canceledAt, OffsetDateTime createdAt) {
        static CancelRecordResponse from(CancelRecord cancel) {
            return new CancelRecordResponse(
                    cancel.getSaleRecord().getId(), cancel.getRefundAmount(), TimeUtils.toKst(cancel.getCanceledAt()), TimeUtils.toKst(cancel.getCreatedAt()));
        }
    }

    public record SaleRecordListItem(
            String id, String courseId, String courseTitle, String studentId, long amount,
            OffsetDateTime paidAt, boolean canceled, CancelInfo cancelInfo
    ) {
        static SaleRecordListItem from(SaleRecord sale) {
            CancelRecord cancel = sale.getCancelRecord();
            return new SaleRecordListItem(
                    sale.getId(), sale.getCourse().getId(), sale.getCourse().getTitle(), sale.getStudentId(), sale.getAmount(),
                    TimeUtils.toKst(sale.getPaidAt()), cancel != null, cancel == null ? null : CancelInfo.from(cancel));
        }
    }

    public record CancelInfo(long refundAmount, OffsetDateTime canceledAt) {
        static CancelInfo from(CancelRecord cancel) {
            return new CancelInfo(cancel.getRefundAmount(), TimeUtils.toKst(cancel.getCanceledAt()));
        }
    }

    public record SaleRecordPageResponse(java.util.List<SaleRecordListItem> content, long totalElements, int totalPages, int page, int size) {
        static SaleRecordPageResponse from(org.springframework.data.domain.Page<SaleRecord> page) {
            return new SaleRecordPageResponse(
                    page.getContent().stream().map(SaleRecordListItem::from).toList(),
                    page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
        }
    }
}
