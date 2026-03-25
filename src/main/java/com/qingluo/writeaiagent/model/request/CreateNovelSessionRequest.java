package com.qingluo.writeaiagent.model.request;

import com.qingluo.writeaiagent.model.session.NovelSessionMode;
import jakarta.validation.constraints.NotNull;

public record CreateNovelSessionRequest(
        @NotNull(message = "mode cannot be null") NovelSessionMode mode
) {
}
