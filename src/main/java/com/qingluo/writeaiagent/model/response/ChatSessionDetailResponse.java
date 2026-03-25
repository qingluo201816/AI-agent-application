package com.qingluo.writeaiagent.model.response;

import java.util.List;

public record ChatSessionDetailResponse(
        ChatSessionSummaryResponse session,
        List<ChatSessionMessageResponse> messages
) {
}
