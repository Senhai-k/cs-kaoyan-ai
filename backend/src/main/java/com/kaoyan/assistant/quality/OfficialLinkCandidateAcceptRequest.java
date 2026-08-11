package com.kaoyan.assistant.quality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OfficialLinkCandidateAcceptRequest(
        @NotBlank @Size(max = 500) String sourceUrl
) {
}
