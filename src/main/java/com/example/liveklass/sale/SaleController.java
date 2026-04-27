package com.example.liveklass.sale;

import com.example.liveklass.course.CourseRepository;
import com.example.liveklass.sale.SaleService.CancelRecordResponse;
import com.example.liveklass.sale.SaleService.CancelSaleRecordRequest;
import com.example.liveklass.sale.SaleService.CreateSaleRecordRequest;
import com.example.liveklass.sale.SaleService.SaleRecordPageResponse;
import com.example.liveklass.sale.SaleService.SaleRecordResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sale-records")
public class SaleController {
    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleRecordResponse create(@Valid @RequestBody CreateSaleRecordRequest request) {
        return saleService.create(request);
    }

    @PostMapping("/{saleRecordId}/cancel")
    @ResponseStatus(HttpStatus.CREATED)
    public CancelRecordResponse cancel(@PathVariable String saleRecordId, @Valid @RequestBody CancelSaleRecordRequest request) {
        return saleService.cancel(saleRecordId, request);
    }

    @GetMapping
    public SaleRecordPageResponse list(
            @RequestParam String creatorId,
            @RequestHeader(value = "X-Creator-Id", required = false) String headerCreatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return saleService.list(creatorId, headerCreatorId, from, to, page, size);
    }

    @Configuration
    static class SaleConfig {
        @Bean
        SaleService saleService(SaleRecordRepository saleRecordRepository, CancelRecordRepository cancelRecordRepository, CourseRepository courseRepository) {
            return new SaleService(saleRecordRepository, cancelRecordRepository, courseRepository);
        }
    }
}
