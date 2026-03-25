package com.qingluo.writeaiagent.controller;

import com.qingluo.writeaiagent.app.WriteApp;
import com.qingluo.writeaiagent.model.request.NovelChatRequest;
import com.qingluo.writeaiagent.service.NovelInspirationAssistService;
import com.qingluo.writeaiagent.service.NovelKeywordContinuationService;
import com.qingluo.writeaiagent.service.NovelStateMemoryService;
import com.qingluo.writeaiagent.service.NovelTaskExecutionPdfService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private WriteApp writeApp;

    @Resource
    private NovelStateMemoryService novelStateMemoryService;

    @Resource
    private NovelInspirationAssistService novelInspirationAssistService;

    @Resource
    private NovelKeywordContinuationService novelKeywordContinuationService;

    @Resource
    private NovelTaskExecutionPdfService novelTaskExecutionPdfService;

    @GetMapping("/write_app/chat/sync")
    public String doChatWithWriteAppSync(String message, String chatId) {
        return writeApp.doChatWithRag(message, chatId);
    }

    @GetMapping(value = "/write_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithWriteAppSse(String message, String chatId) {
        return writeApp.doChatWithRagByStream(message, chatId)
                .concatWith(Mono.just("[DONE]"));
    }

    @PostMapping(
            value = "/novel/state-memory/sse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter doNovelStateMemory(@Valid @RequestBody NovelChatRequest request) {
        return novelStateMemoryService.chat(request);
    }

    @PostMapping(
            value = "/novel/inspiration-assist/sse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter doNovelInspirationAssist(@Valid @RequestBody NovelChatRequest request) {
        return novelInspirationAssistService.chat(request);
    }

    @PostMapping(
            value = "/novel/keyword-continuation/sse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter doNovelKeywordContinuation(@Valid @RequestBody NovelChatRequest request) {
        return novelKeywordContinuationService.chat(request);
    }

    @PostMapping(
            value = "/novel/task-execution-pdf/sse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter doNovelTaskExecutionPdf(@Valid @RequestBody NovelChatRequest request) {
        return novelTaskExecutionPdfService.chat(request);
    }

    /**
     * @deprecated 前端请改用 /ai/novel/task-execution-pdf/sse。
     */
    @Deprecated
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId) {
        return novelTaskExecutionPdfService.chat(new NovelChatRequest(message, chatId));
    }
}
