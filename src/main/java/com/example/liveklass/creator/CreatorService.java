package com.example.liveklass.creator;

import com.example.liveklass.common.TimeUtils;
import com.example.liveklass.common.exception.BusinessException;
import com.example.liveklass.common.exception.ErrorCode;
import jakarta.validation.constraints.NotBlank;

public class CreatorService {
    private final CreatorRepository creatorRepository;

    public CreatorService(CreatorRepository creatorRepository) {
        this.creatorRepository = creatorRepository;
    }

    public CreatorResponse create(CreateCreatorRequest request) {
        if (creatorRepository.existsById(request.id())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CREATOR);
        }
        Creator creator = creatorRepository.save(new Creator(request.id(), request.name(), TimeUtils.nowKst().toInstant()));
        return CreatorResponse.from(creator);
    }

    public record CreateCreatorRequest(@NotBlank String id, @NotBlank String name) {
    }

    public record CreatorResponse(String id, String name, java.time.OffsetDateTime createdAt) {
        static CreatorResponse from(Creator creator) {
            return new CreatorResponse(creator.getId(), creator.getName(), TimeUtils.toKst(creator.getCreatedAt()));
        }
    }
}
