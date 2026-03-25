package com.qingluo.writeaiagent.model.response;

import java.time.Instant;

public record ChatSessionMessageResponse(
        String id,
        String role,
        String content,
        Instant timestamp
) {
}
