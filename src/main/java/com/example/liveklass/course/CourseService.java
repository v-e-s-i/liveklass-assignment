package com.example.liveklass.course;

import com.example.liveklass.common.TimeUtils;
import com.example.liveklass.common.exception.BusinessException;
import com.example.liveklass.common.exception.ErrorCode;
import com.example.liveklass.creator.Creator;
import com.example.liveklass.creator.CreatorRepository;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public class CourseService {
    private final CourseRepository courseRepository;
    private final CreatorRepository creatorRepository;

    public CourseService(CourseRepository courseRepository, CreatorRepository creatorRepository) {
        this.courseRepository = courseRepository;
        this.creatorRepository = creatorRepository;
    }

    public CourseResponse create(CreateCourseRequest request) {
        if (courseRepository.existsById(request.id())) {
            throw new BusinessException(ErrorCode.DUPLICATE_COURSE);
        }
        Creator creator = creatorRepository.findById(request.creatorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        Course course = courseRepository.save(new Course(request.id(), creator, request.title(), TimeUtils.nowKst().toInstant()));
        return CourseResponse.from(course);
    }

    public record CreateCourseRequest(@NotBlank String id, @NotBlank String creatorId, @NotBlank String title) {
    }

    public record CourseResponse(String id, String creatorId, String title, OffsetDateTime createdAt) {
        static CourseResponse from(Course course) {
            return new CourseResponse(course.getId(), course.getCreator().getId(), course.getTitle(), TimeUtils.toKst(course.getCreatedAt()));
        }
    }
}
