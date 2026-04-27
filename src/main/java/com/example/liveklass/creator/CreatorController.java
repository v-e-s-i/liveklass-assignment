package com.example.liveklass.creator;

import com.example.liveklass.creator.CreatorService.CreateCreatorRequest;
import com.example.liveklass.creator.CreatorService.CreatorResponse;
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
@RequestMapping("/api/v1/creators")
public class CreatorController {
    private final CreatorService creatorService;

    public CreatorController(CreatorService creatorService) {
        this.creatorService = creatorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatorResponse create(@Valid @RequestBody CreateCreatorRequest request) {
        return creatorService.create(request);
    }

    @Configuration
    static class CreatorConfig {
        @Bean
        CreatorService creatorService(CreatorRepository creatorRepository) {
            return new CreatorService(creatorRepository);
        }
    }
}
