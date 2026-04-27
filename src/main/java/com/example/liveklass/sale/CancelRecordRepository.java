package com.example.liveklass.sale;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancelRecordRepository extends JpaRepository<CancelRecord, Long> {
    boolean existsBySaleRecordId(String saleRecordId);

    Optional<CancelRecord> findBySaleRecordId(String saleRecordId);

    long countBySaleRecordCourseCreatorIdAndCanceledAtGreaterThanEqualAndCanceledAtLessThan(String creatorId, Instant from, Instant toExclusive);

    long countByCanceledAtGreaterThanEqualAndCanceledAtLessThan(Instant from, Instant toExclusive);

    @Query("select coalesce(sum(c.refundAmount), 0) from CancelRecord c where c.saleRecord.course.creator.id = :creatorId and c.canceledAt >= :from and c.canceledAt < :toExclusive")
    long sumRefundByCreatorAndCanceledAtInRange(@Param("creatorId") String creatorId, @Param("from") Instant from, @Param("toExclusive") Instant toExclusive);

    @Query("select c.saleRecord.course.creator.id, c.saleRecord.course.creator.name, coalesce(sum(c.refundAmount), 0), count(c) "
            + "from CancelRecord c where c.canceledAt >= :from and c.canceledAt < :toExclusive group by c.saleRecord.course.creator.id, c.saleRecord.course.creator.name")
    java.util.List<Object[]> aggregateRefundsByCreator(@Param("from") Instant from, @Param("toExclusive") Instant toExclusive);
}
