package com.example.liveklass.sale;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRecordRepository extends JpaRepository<SaleRecord, String> {
    @EntityGraph(attributePaths = {"course", "course.creator", "cancelRecord"})
    Page<SaleRecord> findByCourseCreatorIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(String creatorId, Instant from, Instant toExclusive, Pageable pageable);

    long countByCourseCreatorIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(String creatorId, Instant from, Instant toExclusive);

    long countByPaidAtGreaterThanEqualAndPaidAtLessThan(Instant from, Instant toExclusive);

    @Query("select coalesce(sum(s.amount), 0) from SaleRecord s where s.course.creator.id = :creatorId and s.paidAt >= :from and s.paidAt < :toExclusive")
    long sumAmountByCreatorAndPaidAtInRange(@Param("creatorId") String creatorId, @Param("from") Instant from, @Param("toExclusive") Instant toExclusive);

    @Query("select s.course.creator.id, s.course.creator.name, coalesce(sum(s.amount), 0), count(s) "
            + "from SaleRecord s where s.paidAt >= :from and s.paidAt < :toExclusive group by s.course.creator.id, s.course.creator.name")
    java.util.List<Object[]> aggregateSalesByCreator(@Param("from") Instant from, @Param("toExclusive") Instant toExclusive);
}
