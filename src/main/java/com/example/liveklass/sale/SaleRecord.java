package com.example.liveklass.sale;

import com.example.liveklass.course.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.Instant;

@Entity
public class SaleRecord {
    @Id
    @Column(length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 100)
    private String studentId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private Instant paidAt;

    @Column(nullable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "saleRecord", fetch = FetchType.LAZY)
    private CancelRecord cancelRecord;

    protected SaleRecord() {
    }

    public SaleRecord(String id, Course course, String studentId, long amount, Instant paidAt, Instant createdAt) {
        this.id = id;
        this.course = course;
        this.studentId = studentId;
        this.amount = amount;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getStudentId() {
        return studentId;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public CancelRecord getCancelRecord() {
        return cancelRecord;
    }
}
