package com.qingluo.writeaiagent.model.request;

import jakarta.validation.constraints.NotBlank;

public record RenameNovelSessionRequest(
        @NotBlank(message = "title cannot be blank") String title
) {
}
