package com.example.liveklass.common.init;

import com.example.liveklass.course.Course;
import com.example.liveklass.course.CourseRepository;
import com.example.liveklass.creator.Creator;
import com.example.liveklass.creator.CreatorRepository;
import com.example.liveklass.sale.CancelRecord;
import com.example.liveklass.sale.CancelRecordRepository;
import com.example.liveklass.sale.SaleRecord;
import com.example.liveklass.sale.SaleRecordRepository;
import java.time.OffsetDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {
    private final CreatorRepository creatorRepository;
    private final CourseRepository courseRepository;
    private final SaleRecordRepository saleRecordRepository;
    private final CancelRecordRepository cancelRecordRepository;

    public DataInitializer(
            CreatorRepository creatorRepository,
            CourseRepository courseRepository,
            SaleRecordRepository saleRecordRepository,
            CancelRecordRepository cancelRecordRepository
    ) {
        this.creatorRepository = creatorRepository;
        this.courseRepository = courseRepository;
        this.saleRecordRepository = saleRecordRepository;
        this.cancelRecordRepository = cancelRecordRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Creator creator1 = creator("creator-1", "김강사");
        Creator creator2 = creator("creator-2", "이강사");
        Creator creator3 = creator("creator-3", "박강사");

        Course course1 = course("course-1", creator1, "Spring Boot 입문");
        Course course2 = course("course-2", creator1, "JPA 실전");
        Course course3 = course("course-3", creator2, "테스트 자동화");
        Course course4 = course("course-4", creator3, "클린 코드");

        sale("sale-1", course1, "student-1", 50_000, "2025-03-05T10:00:00+09:00");
        sale("sale-2", course1, "student-2", 50_000, "2025-03-06T10:00:00+09:00");
        sale("sale-3", course2, "student-3", 80_000, "2025-03-20T09:00:00+09:00");
        sale("sale-4", course2, "student-4", 80_000, "2025-03-21T09:00:00+09:00");
        sale("sale-5", course3, "student-5", 60_000, "2025-01-31T23:30:00+09:00");
        sale("sale-6", course3, "student-6", 60_000, "2025-03-15T12:00:00+09:00");
        sale("sale-7", course4, "student-7", 70_000, "2025-02-10T12:00:00+09:00");

        cancel("sale-3", 80_000, "2025-03-25T15:00:00+09:00");
        cancel("sale-4", 40_000, "2025-03-28T10:00:00+09:00");
        cancel("sale-5", 60_000, "2025-02-03T09:00:00+09:00");
    }

    private Creator creator(String id, String name) {
        return creatorRepository.findById(id)
                .orElseGet(() -> creatorRepository.save(new Creator(id, name, now())));
    }

    private Course course(String id, Creator creator, String title) {
        return courseRepository.findById(id)
                .orElseGet(() -> courseRepository.save(new Course(id, creator, title, now())));
    }

    private void sale(String id, Course course, String studentId, long amount, String paidAt) {
        if (!saleRecordRepository.existsById(id)) {
            saleRecordRepository.save(new SaleRecord(id, course, studentId, amount, OffsetDateTime.parse(paidAt).toInstant(), now()));
        }
    }

    private void cancel(String saleId, long refundAmount, String canceledAt) {
        if (!cancelRecordRepository.existsBySaleRecordId(saleId)) {
            SaleRecord sale = saleRecordRepository.findById(saleId).orElseThrow();
            cancelRecordRepository.save(new CancelRecord(sale, refundAmount, OffsetDateTime.parse(canceledAt).toInstant(), now()));
        }
    }

    private java.time.Instant now() {
        return java.time.Instant.now();
    }
}
