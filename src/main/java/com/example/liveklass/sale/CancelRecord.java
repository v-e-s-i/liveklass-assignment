package com.example.liveklass.sale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.Instant;

@Entity
public class CancelRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_record_id", nullable = false, unique = true)
    private SaleRecord saleRecord;

    @Column(nullable = false)
    private long refundAmount;

    @Column(nullable = false)
    private Instant canceledAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected CancelRecord() {
    }

    public CancelRecord(SaleRecord saleRecord, long refundAmount, Instant canceledAt, Instant createdAt) {
        this.saleRecord = saleRecord;
        this.refundAmount = refundAmount;
        this.canceledAt = canceledAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public SaleRecord getSaleRecord() {
        return saleRecord;
    }

    public long getRefundAmount() {
        return refundAmount;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
