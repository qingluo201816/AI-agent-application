package com.qingluo.writeaiagent.model.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 小说写作模块的通用聊天请求。
 *
 * @param message 用户输入
 * @param chatId  对话 ID，可为空
 */
public record NovelChatRequest(
        @NotBlank(message = "message 不能为空") String message,
        String chatId
) {
}
