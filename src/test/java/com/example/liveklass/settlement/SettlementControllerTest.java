package com.example.liveklass.settlement;

import com.example.liveklass.course.Course;
import com.example.liveklass.course.CourseRepository;
import com.example.liveklass.creator.Creator;
import com.example.liveklass.creator.CreatorRepository;
import com.example.liveklass.sale.CancelRecord;
import com.example.liveklass.sale.CancelRecordRepository;
import com.example.liveklass.sale.SaleRecord;
import com.example.liveklass.sale.SaleRecordRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    CreatorRepository creatorRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    SaleRecordRepository saleRecordRepository;

    @Autowired
    CancelRecordRepository cancelRecordRepository;

    private Course course1;
    private Course course2;
    private Course course3;
    private Course course4;

    @BeforeEach
    void setUp() {
        cancelRecordRepository.deleteAll();
        saleRecordRepository.deleteAll();
        courseRepository.deleteAll();
        creatorRepository.deleteAll();

        Creator creator1 = creatorRepository.save(new Creator("creator-1", "김강사", Instant.now()));
        Creator creator2 = creatorRepository.save(new Creator("creator-2", "이강사", Instant.now()));
        Creator creator3 = creatorRepository.save(new Creator("creator-3", "박강사", Instant.now()));
        course1 = courseRepository.save(new Course("course-1", creator1, "Spring Boot 입문", Instant.now()));
        course2 = courseRepository.save(new Course("course-2", creator1, "JPA 실전", Instant.now()));
        course3 = courseRepository.save(new Course("course-3", creator2, "테스트 자동화", Instant.now()));
        course4 = courseRepository.save(new Course("course-4", creator3, "클린 코드", Instant.now()));

        seed();
    }

    @Test
    void createsSaleRecord() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "sale-new",
                                  "courseId": "course-1",
                                  "studentId": "student-new",
                                  "amount": 30000,
                                  "paidAt": "2025-03-10T11:00:00+09:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sale-new"))
                .andExpect(jsonPath("$.courseId").value("course-1"))
                .andExpect(jsonPath("$.creatorId").value("creator-1"))
                .andExpect(jsonPath("$.studentId").value("student-new"))
                .andExpect(jsonPath("$.amount").value(30000))
                .andExpect(jsonPath("$.paidAt").value("2025-03-10T11:00:00+09:00"))
                .andExpect(jsonPath("$.canceled").value(false));
    }

    @Test
    void createsCancelRecord() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refundAmount": 20000,
                                  "canceledAt": "2025-03-25T15:00:00+09:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saleRecordId").value("sale-1"))
                .andExpect(jsonPath("$.refundAmount").value(20000))
                .andExpect(jsonPath("$.canceledAt").value("2025-03-25T15:00:00+09:00"));
    }

    @Test
    void listsSaleRecordsByCreatorAndDateRange() throws Exception {
        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("from", "2025-03-01")
                        .param("to", "2025-03-31")
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.content[0].id").value("sale-4"))
                .andExpect(jsonPath("$.content[0].canceled").value(true))
                .andExpect(jsonPath("$.content[0].cancelInfo.refundAmount").value(40000))
                .andExpect(jsonPath("$.content[2].id").value("sale-2"))
                .andExpect(jsonPath("$.content[2].canceled").value(false));
    }

    @Test
    void duplicateSaleIdReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "sale-1",
                                  "courseId": "course-1",
                                  "studentId": "student-duplicate",
                                  "amount": 30000,
                                  "paidAt": "2025-03-10T11:00:00+09:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_SALE_RECORD"));
    }

    @Test
    void duplicateCancelReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-3")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refundAmount": 10000,
                                  "canceledAt": "2025-03-29T15:00:00+09:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELED"));
    }

    @Test
    void futurePaidAtReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "sale-future",
                                  "courseId": "course-1",
                                  "studentId": "student-future",
                                  "amount": 30000,
                                  "paidAt": "2999-03-10T11:00:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("FUTURE_PAID_AT"));
    }

    @Test
    void canceledAtBeforePaidAtReturnsUnprocessableEntity() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refundAmount": 10000,
                                  "canceledAt": "2025-03-01T15:00:00+09:00"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("INVALID_CANCEL_DATE"));
    }

    @Test
    void monthlySettlementUsesKstMonthBoundaries() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(260000))
                .andExpect(jsonPath("$.totalRefundAmount").value(120000))
                .andExpect(jsonPath("$.netSalesAmount").value(140000))
                .andExpect(jsonPath("$.feeAmount").value(28000))
                .andExpect(jsonPath("$.payoutAmount").value(112000))
                .andExpect(jsonPath("$.saleCount").value(4))
                .andExpect(jsonPath("$.cancelCount").value(2));
    }

    @Test
    void refundBelongsToCancelMonthIndependentlyFromSaleMonth() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-2")
                        .param("creatorId", "creator-2")
                        .param("yearMonth", "2025-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(0))
                .andExpect(jsonPath("$.totalRefundAmount").value(60000))
                .andExpect(jsonPath("$.netSalesAmount").value(-60000))
                .andExpect(jsonPath("$.feeAmount").value(-12000))
                .andExpect(jsonPath("$.payoutAmount").value(-48000));
    }

    @Test
    void monthlySettlementRequiresMatchingCreatorHeader() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .param("creatorId", "creator-1")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-2")
                        .param("creatorId", "creator-1")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void missingRequiredParamsReturnBadRequestJsonError() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_YEAR_MONTH"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-1")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/admin/settlements/summary")
                        .header("X-Role", "ADMIN")
                        .param("to", "2025-03-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/admin/settlements/summary")
                        .header("X-Role", "ADMIN")
                        .param("from", "2025-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidYearMonthAndMalformedDatesReturnBadRequestJsonError() throws Exception {
        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("yearMonth", "2025-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_YEAR_MONTH"));

        mockMvc.perform(get("/api/v1/admin/settlements/summary")
                        .header("X-Role", "ADMIN")
                        .param("from", "not-a-date")
                        .param("to", "2025-03-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_FORMAT"));
    }

    @Test
    void cancelRefundValidationUsesContractCodesAndStatuses() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refundAmount": 60000,
                                  "canceledAt": "2025-03-25T15:00:00+09:00"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_AMOUNT"));

        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-2")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refundAmount": 0,
                                  "canceledAt": "2025-03-25T15:00:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_AMOUNT"));
    }

    @Test
    void invalidPageAndSizeReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("size", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void saleListMissingCreatorIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void saleListInvalidDateParamsReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("from", "not-a-date")
                        .param("to", "2025-03-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_FORMAT"));

        mockMvc.perform(get("/api/v1/sale-records")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("from", "2025-04-01")
                        .param("to", "2025-03-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void cancelRequestMissingOrInvalidBodyFieldsReturnContractErrors() throws Exception {
        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "canceledAt": "2025-03-25T15:00:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REFUND_AMOUNT"));

        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refundAmount": 10000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/sale-records/{saleRecordId}/cancel", "sale-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refundAmount": 10000,
                                  "canceledAt": "not-a-date"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_DATE_FORMAT"));
    }

    @Test
    void settlementIncludesFractionalSecondEndOfMonthRecords() throws Exception {
        sale("sale-boundary", course1, "student-boundary", 10_000, "2025-03-31T23:59:59.999999+09:00");
        SaleRecord refundSale = sale("sale-refund-boundary", course1, "student-refund-boundary", 20_000, "2025-03-30T10:00:00+09:00");
        cancelRecordRepository.save(new CancelRecord(refundSale, 5_000, OffsetDateTime.parse("2025-03-31T23:59:59.999999+09:00").toInstant(), Instant.now()));

        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(290000))
                .andExpect(jsonPath("$.totalRefundAmount").value(125000))
                .andExpect(jsonPath("$.saleCount").value(6))
                .andExpect(jsonPath("$.cancelCount").value(3));
    }

    @Test
    void settlementIncludesRecordsAtStartOfMonthBoundary() throws Exception {
        sale("sale-start-boundary", course1, "student-start-boundary", 10_000, "2025-03-01T00:00:00+09:00");
        SaleRecord refundSale = sale("sale-refund-start-boundary", course1, "student-refund-start-boundary", 20_000, "2025-02-28T23:59:59+09:00");
        cancelRecordRepository.save(new CancelRecord(refundSale, 5_000, OffsetDateTime.parse("2025-03-01T00:00:00+09:00").toInstant(), Instant.now()));

        mockMvc.perform(get("/api/v1/settlements/monthly")
                        .header("X-Creator-Id", "creator-1")
                        .param("creatorId", "creator-1")
                        .param("yearMonth", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSalesAmount").value(270000))
                .andExpect(jsonPath("$.totalRefundAmount").value(125000))
                .andExpect(jsonPath("$.saleCount").value(5))
                .andExpect(jsonPath("$.cancelCount").value(3));
    }

    @Test
    void adminSummaryRequiresAdminAndExcludesCreatorsWithoutActivity() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settlements/summary")
                        .header("X-Role", "ADMIN")
                        .param("from", "2025-03-01")
                        .param("to", "2025-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.summary.totalSalesAmount").value(320000))
                .andExpect(jsonPath("$.summary.totalRefundAmount").value(120000))
                .andExpect(jsonPath("$.summary.payoutAmount").value(160000));
    }

    @Test
    void adminSummaryRequiresAdminRoleHeader() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settlements/summary")
                        .param("from", "2025-03-01")
                        .param("to", "2025-03-31"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/settlements/summary")
                        .header("X-Role", "USER")
                        .param("from", "2025-03-01")
                        .param("to", "2025-03-31"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private void seed() {
        SaleRecord sale3 = sale("sale-3", course2, "student-3", 80_000, "2025-03-20T09:00:00+09:00");
        SaleRecord sale4 = sale("sale-4", course2, "student-4", 80_000, "2025-03-21T09:00:00+09:00");
        SaleRecord sale5 = sale("sale-5", course3, "student-5", 60_000, "2025-01-31T23:30:00+09:00");
        sale("sale-1", course1, "student-1", 50_000, "2025-03-05T10:00:00+09:00");
        sale("sale-2", course1, "student-2", 50_000, "2025-03-06T10:00:00+09:00");
        sale("sale-6", course3, "student-6", 60_000, "2025-03-15T12:00:00+09:00");
        sale("sale-7", course4, "student-7", 70_000, "2025-02-10T12:00:00+09:00");
        cancelRecordRepository.save(new CancelRecord(sale3, 80_000, OffsetDateTime.parse("2025-03-25T15:00:00+09:00").toInstant(), Instant.now()));
        cancelRecordRepository.save(new CancelRecord(sale4, 40_000, OffsetDateTime.parse("2025-03-28T10:00:00+09:00").toInstant(), Instant.now()));
        cancelRecordRepository.save(new CancelRecord(sale5, 60_000, OffsetDateTime.parse("2025-02-03T09:00:00+09:00").toInstant(), Instant.now()));
    }

    private SaleRecord sale(String id, Course course, String studentId, long amount, String paidAt) {
        return saleRecordRepository.save(new SaleRecord(id, course, studentId, amount, OffsetDateTime.parse(paidAt).toInstant(), Instant.now()));
    }
}
