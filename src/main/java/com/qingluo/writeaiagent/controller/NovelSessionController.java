package com.qingluo.writeaiagent.controller;

import com.qingluo.writeaiagent.model.request.CreateNovelSessionRequest;
import com.qingluo.writeaiagent.model.request.RenameNovelSessionRequest;
import com.qingluo.writeaiagent.model.response.ChatSessionDetailResponse;
import com.qingluo.writeaiagent.model.response.ChatSessionSummaryResponse;
import com.qingluo.writeaiagent.model.session.NovelSessionMode;
import com.qingluo.writeaiagent.service.ChatSessionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/novel/sessions")
public class NovelSessionController {

    @Resource
    private ChatSessionService chatSessionService;

    @GetMapping
    public List<ChatSessionSummaryResponse> listSessions(@RequestParam String mode) {
        return chatSessionService.listSessions(NovelSessionMode.fromValue(mode));
    }

    @PostMapping
    public ChatSessionDetailResponse createSession(@Valid @RequestBody CreateNovelSessionRequest request) {
        return chatSessionService.createSession(request.mode());
    }

    @GetMapping("/{chatId}")
    public ChatSessionDetailResponse getSessionDetail(@PathVariable String chatId) {
        return chatSessionService.getSessionDetail(chatId);
    }

    @PatchMapping("/{chatId}/title")
    public ChatSessionSummaryResponse renameSessionByPatch(
            @PathVariable String chatId,
            @Valid @RequestBody RenameNovelSessionRequest request
    ) {
        return chatSessionService.renameSession(chatId, request.title());
    }

    @PutMapping("/{chatId}/title")
    public ChatSessionSummaryResponse renameSession(
            @PathVariable String chatId,
            @Valid @RequestBody RenameNovelSessionRequest request
    ) {
        return chatSessionService.renameSession(chatId, request.title());
    }

    @DeleteMapping("/{chatId}")
    public void deleteSession(@PathVariable String chatId) {
        chatSessionService.deleteSession(chatId);
    }
}
