package com.example.liveklass.course;

import com.example.liveklass.course.CourseService.CourseResponse;
import com.example.liveklass.course.CourseService.CreateCourseRequest;
import com.example.liveklass.creator.CreatorRepository;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse create(@Valid @RequestBody CreateCourseRequest request) {
        return courseService.create(request);
    }

    @Configuration
    static class CourseConfig {
        @Bean
        CourseService courseService(CourseRepository courseRepository, CreatorRepository creatorRepository) {
            return new CourseService(courseRepository, creatorRepository);
        }
    }
}
