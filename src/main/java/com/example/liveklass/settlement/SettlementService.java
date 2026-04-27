package com.example.liveklass.settlement;

import com.example.liveklass.common.TimeUtils;
import com.example.liveklass.common.exception.BusinessException;
import com.example.liveklass.common.exception.ErrorCode;
import com.example.liveklass.creator.Creator;
import com.example.liveklass.creator.CreatorRepository;
import com.example.liveklass.sale.CancelRecordRepository;
import com.example.liveklass.sale.SaleRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettlementService {
    private final CreatorRepository creatorRepository;
    private final SaleRecordRepository saleRecordRepository;
    private final CancelRecordRepository cancelRecordRepository;
    private final BigDecimal feeRate;

    public SettlementService(
            CreatorRepository creatorRepository,
            SaleRecordRepository saleRecordRepository,
            CancelRecordRepository cancelRecordRepository,
            BigDecimal feeRate
    ) {
        this.creatorRepository = creatorRepository;
        this.saleRecordRepository = saleRecordRepository;
        this.cancelRecordRepository = cancelRecordRepository;
        this.feeRate = feeRate;
    }

    public MonthlySettlementResponse monthly(String creatorId, String headerCreatorId, String yearMonthValue) {
        requireCreator(creatorId, headerCreatorId);
        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        YearMonth yearMonth = parseYearMonth(yearMonthValue);
        Instant from = yearMonth.atDay(1).atStartOfDay(TimeUtils.KST).toInstant();
        Instant toExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(TimeUtils.KST).toInstant();
        Totals totals = totalsForCreator(creatorId, creator.getName(), from, toExclusive);
        return new MonthlySettlementResponse(
                creatorId, creator.getName(), yearMonth.toString(),
                new MonthlyPeriod(
                        yearMonth.atDay(1).atStartOfDay(TimeUtils.KST).toOffsetDateTime(),
                        yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(TimeUtils.KST).toOffsetDateTime()),
                totals.totalSalesAmount(), totals.totalRefundAmount(), totals.netSalesAmount(), feeRate,
                totals.feeAmount(), totals.payoutAmount(), totals.saleCount(), totals.cancelCount());
    }

    public AdminSettlementSummaryResponse adminSummary(LocalDate fromDate, LocalDate toDate, String role) {
        requireAdmin(role);
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        Instant from = TimeUtils.startOfDayKst(fromDate);
        Instant toExclusive = TimeUtils.startOfNextDayKst(toDate);
        Map<String, MutableTotals> byCreator = new LinkedHashMap<>();
        for (Object[] row : saleRecordRepository.aggregateSalesByCreator(from, toExclusive)) {
            String creatorId = (String) row[0];
            byCreator.computeIfAbsent(creatorId, ignored -> new MutableTotals(creatorId, (String) row[1]))
                    .addSales(((Number) row[2]).longValue(), ((Number) row[3]).longValue());
        }
        for (Object[] row : cancelRecordRepository.aggregateRefundsByCreator(from, toExclusive)) {
            String creatorId = (String) row[0];
            byCreator.computeIfAbsent(creatorId, ignored -> new MutableTotals(creatorId, (String) row[1]))
                    .addRefunds(((Number) row[2]).longValue(), ((Number) row[3]).longValue());
        }
        List<AdminSettlementItem> items = byCreator.values().stream()
                .map(MutableTotals::toItem)
                .sorted(Comparator.comparingLong(AdminSettlementItem::payoutAmount).reversed())
                .toList();
        AdminSettlementItem summary = items.stream().reduce(
                new AdminSettlementItem(null, null, 0, 0, 0, 0, 0, 0, 0),
                AdminSettlementItem::merge);
        return new AdminSettlementSummaryResponse(new DatePeriod(fromDate, toDate), feeRate, items, summary);
    }

    private Totals totalsForCreator(String creatorId, String creatorName, Instant from, Instant toExclusive) {
        long totalSales = saleRecordRepository.sumAmountByCreatorAndPaidAtInRange(creatorId, from, toExclusive);
        long totalRefunds = cancelRecordRepository.sumRefundByCreatorAndCanceledAtInRange(creatorId, from, toExclusive);
        long saleCount = saleRecordRepository.countByCourseCreatorIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(creatorId, from, toExclusive);
        long cancelCount = cancelRecordRepository.countBySaleRecordCourseCreatorIdAndCanceledAtGreaterThanEqualAndCanceledAtLessThan(creatorId, from, toExclusive);
        return new MutableTotals(creatorId, creatorName)
                .addSales(totalSales, saleCount)
                .addRefunds(totalRefunds, cancelCount)
                .toTotals();
    }

    private YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_YEAR_MONTH);
        }
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireCreator(String creatorId, String headerCreatorId) {
        if (headerCreatorId == null || headerCreatorId.isBlank() || !headerCreatorId.equals(creatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private long fee(long netSales) {
        return BigDecimal.valueOf(netSales).multiply(feeRate).setScale(0, RoundingMode.FLOOR).longValue();
    }

    private record Totals(
            long totalSalesAmount, long totalRefundAmount, long netSalesAmount,
            long feeAmount, long payoutAmount, long saleCount, long cancelCount
    ) {
    }

    private final class MutableTotals {
        private final String creatorId;
        private final String creatorName;
        private long totalSalesAmount;
        private long totalRefundAmount;
        private long saleCount;
        private long cancelCount;

        private MutableTotals(String creatorId, String creatorName) {
            this.creatorId = creatorId;
            this.creatorName = creatorName;
        }

        private MutableTotals addSales(long amount, long count) {
            this.totalSalesAmount += amount;
            this.saleCount += count;
            return this;
        }

        private MutableTotals addRefunds(long amount, long count) {
            this.totalRefundAmount += amount;
            this.cancelCount += count;
            return this;
        }

        private Totals toTotals() {
            long net = totalSalesAmount - totalRefundAmount;
            long feeAmount = fee(net);
            return new Totals(totalSalesAmount, totalRefundAmount, net, feeAmount, net - feeAmount, saleCount, cancelCount);
        }

        private AdminSettlementItem toItem() {
            Totals totals = toTotals();
            return new AdminSettlementItem(
                    creatorId, creatorName, totals.totalSalesAmount(), totals.totalRefundAmount(), totals.netSalesAmount(),
                    totals.feeAmount(), totals.payoutAmount(), totals.saleCount(), totals.cancelCount());
        }
    }

    public record MonthlyPeriod(OffsetDateTime from, OffsetDateTime to) {
    }

    public record DatePeriod(LocalDate from, LocalDate to) {
    }

    public record MonthlySettlementResponse(
            String creatorId, String creatorName, String yearMonth, MonthlyPeriod period,
            long totalSalesAmount, long totalRefundAmount, long netSalesAmount, BigDecimal feeRate,
            long feeAmount, long payoutAmount, long saleCount, long cancelCount
    ) {
    }

    public record AdminSettlementSummaryResponse(DatePeriod period, BigDecimal feeRate, List<AdminSettlementItem> items, AdminSettlementItem summary) {
    }

    public record AdminSettlementItem(
            String creatorId, String creatorName, long totalSalesAmount, long totalRefundAmount, long netSalesAmount,
            long feeAmount, long payoutAmount, long saleCount, long cancelCount
    ) {
        AdminSettlementItem merge(AdminSettlementItem other) {
            long sales = totalSalesAmount + other.totalSalesAmount;
            long refunds = totalRefundAmount + other.totalRefundAmount;
            long net = netSalesAmount + other.netSalesAmount;
            long fees = feeAmount + other.feeAmount;
            long payout = payoutAmount + other.payoutAmount;
            return new AdminSettlementItem(null, null, sales, refunds, net, fees, payout, saleCount + other.saleCount, cancelCount + other.cancelCount);
        }
    }
}
