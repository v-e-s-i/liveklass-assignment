package com.example.liveklass.settlement;

import com.example.liveklass.creator.CreatorRepository;
import com.example.liveklass.sale.CancelRecordRepository;
import com.example.liveklass.sale.SaleRecordRepository;
import com.example.liveklass.settlement.SettlementService.AdminSettlementSummaryResponse;
import com.example.liveklass.settlement.SettlementService.MonthlySettlementResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {
    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/monthly")
    public MonthlySettlementResponse monthly(
            @RequestHeader("X-Creator-Id") String headerCreatorId,
            @RequestParam String creatorId,
            @RequestParam String yearMonth
    ) {
        return settlementService.monthly(creatorId, headerCreatorId, yearMonth);
    }

    @RestController
    @RequestMapping("/api/v1/admin/settlements")
    static class AdminSettlementController {
        private final SettlementService settlementService;

        AdminSettlementController(SettlementService settlementService) {
            this.settlementService = settlementService;
        }

        @GetMapping("/summary")
        AdminSettlementSummaryResponse summary(
                @RequestHeader(value = "X-Role", required = false) String role,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
        ) {
            return settlementService.adminSummary(from, to, role);
        }
    }

    @Configuration
    static class SettlementConfig {
        @Bean
        SettlementService settlementService(
                CreatorRepository creatorRepository,
                SaleRecordRepository saleRecordRepository,
                CancelRecordRepository cancelRecordRepository,
                @Value("${platform.fee-rate}") BigDecimal feeRate
        ) {
            return new SettlementService(creatorRepository, saleRecordRepository, cancelRecordRepository, feeRate);
        }
    }
}
