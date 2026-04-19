package com.qingluo.writeaiagent.controller;

import com.qingluo.writeaiagent.skill.SkillDefinition;
import com.qingluo.writeaiagent.skill.SkillExecutionRequest;
import com.qingluo.writeaiagent.skill.SkillExecutionResult;
import com.qingluo.writeaiagent.skill.SkillExecutor;
import com.qingluo.writeaiagent.skill.SkillTriggerDetector;
import com.qingluo.writeaiagent.skill.knowledge.KnowledgeOrganizeResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/ai/skill")
@Validated
@Slf4j
public class SkillController {

    private final SkillExecutor skillExecutor;
    private final SkillTriggerDetector triggerDetector;

    public SkillController(SkillExecutor skillExecutor, SkillTriggerDetector triggerDetector) {
        this.skillExecutor = skillExecutor;
        this.triggerDetector = triggerDetector;
    }

    @PostMapping("/knowledge/organize")
    public Map<String, Object> organizeKnowledge(@Valid @RequestBody KnowledgeOrganizeRequest request) {
        log.info("收到知识编排请求，类型: {}, 内容长度: {}",
                request.getKnowledgeType(), request.getContent().length());

        SkillExecutionRequest executionRequest = SkillExecutionRequest.builder()
                .skillId("knowledge_organize")
                .skillType("KNOWLEDGE_ORGANIZE")
                .content(request.getContent())
                .metadata(Map.of(
                        "bookName", request.getBookName() != null ? request.getBookName() : "未命名书籍",
                        "primaryType", request.getKnowledgeType() != null ? request.getKnowledgeType() : "COMPREHENSIVE"
                ))
                .build();

        SkillExecutionResult result = skillExecutor.execute(executionRequest);

        if (result.isSuccess() && result.getResult() instanceof KnowledgeOrganizeResult organizeResult) {
            return Map.of(
                    "success", true,
                    "markdown", organizeResult.getGeneratedMarkdown(),
                    "sections", organizeResult.getSections().size(),
                    "entries", organizeResult.getSections().stream()
                            .mapToInt(s -> s.getEntries().size()).sum(),
                    "warnings", organizeResult.getWarnings() != null ?
                            organizeResult.getWarnings() : java.util.List.of()
            );
        } else {
            return Map.of(
                    "success", false,
                    "error", result.getError() != null ? result.getError() : "未知错误"
            );
        }
    }

    @PostMapping(value = "/knowledge/organize/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter organizeKnowledgeStream(@Valid @RequestBody KnowledgeOrganizeRequest request) {
        log.info("收到知识编排流式请求，类型: {}, 内容长度: {}",
                request.getKnowledgeType(), request.getContent().length());

        SseEmitter emitter = new SseEmitter(120000L);

        try {
            SkillExecutionRequest executionRequest = SkillExecutionRequest.builder()
                    .skillId("knowledge_organize")
                    .skillType("KNOWLEDGE_ORGANIZE")
                    .content(request.getContent())
                    .metadata(Map.of(
                            "bookName", request.getBookName() != null ? request.getBookName() : "未命名书籍",
                            "primaryType", request.getKnowledgeType() != null ? request.getKnowledgeType() : "COMPREHENSIVE"
                    ))
                    .build();

            emitter.send("【知识编排】开始分析内容...\n");

            SkillExecutionResult result = skillExecutor.execute(executionRequest);

            if (result.isSuccess() && result.getResult() instanceof KnowledgeOrganizeResult organizeResult) {
                emitter.send("【知识编排】分析完成，正在生成结构化文档...\n");

                String markdown = organizeResult.getGeneratedMarkdown();
                if (markdown != null) {
                    emitter.send("【生成结果】\n" + markdown + "\n");
                }

                emitter.send("【完成】知识编排完成，共生成 " + organizeResult.getSections().size()
                        + " 个section，" + organizeResult.getSections().stream()
                                .mapToInt(s -> s.getEntries().size()).sum() + " 个entry\n");

                log.info("知识编排完成，markdown 长度: {}", markdown != null ? markdown.length() : 0);
            } else {
                emitter.send("【错误】知识编排失败: " + (result.getError() != null ?
                        result.getError() : "未知错误") + "\n");
            }

            emitter.complete();

        } catch (Exception e) {
            log.error("知识编排流式请求失败: {}", e.getMessage(), e);
            try {
                emitter.send("【错误】" + e.getMessage());
                emitter.complete();
            } catch (Exception ex) {
                log.warn("发送错误信息失败: {}", ex.getMessage());
            }
        }

        return emitter;
    }

    @GetMapping("/detect/{message}")
    public Map<String, Object> detectTrigger(@PathVariable @NotBlank String message) {
        SkillTriggerDetector.SkillMatch match = triggerDetector.detectTrigger(message);

        return Map.of(
                "message", message,
                "matched", match.isMatched(),
                "skillId", match.getSkillId() != null ? match.getSkillId() : "",
                "reason", match.getReason() != null ? match.getReason() : ""
        );
    }

    @GetMapping("/definition/{skillId}")
    public Map<String, Object> getSkillDefinition(@PathVariable @NotBlank String skillId) {
        SkillDefinition definition = skillExecutor.getSkillDefinition(skillId);

        if (definition != null) {
            return Map.of(
                    "success", true,
                    "definition", definition
            );
        } else {
            return Map.of(
                    "success", false,
                    "error", "Skill not found: " + skillId
            );
        }
    }
}