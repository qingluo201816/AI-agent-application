package com.qingluo.writeaiagent.skill;

import com.qingluo.writeaiagent.skill.knowledge.KnowledgeOrganizeConfig;
import com.qingluo.writeaiagent.skill.knowledge.KnowledgeOrganizeInput;
import com.qingluo.writeaiagent.skill.knowledge.KnowledgeOrganizeResult;
import com.qingluo.writeaiagent.skill.knowledge.KnowledgeOrganizeSkill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SkillExecutor {

    private final KnowledgeOrganizeSkill knowledgeOrganizeSkill;

    public SkillExecutor(KnowledgeOrganizeSkill knowledgeOrganizeSkill) {
        this.knowledgeOrganizeSkill = knowledgeOrganizeSkill;
    }

    public SkillExecutionResult execute(SkillExecutionRequest request) {
        log.info("开始执行 Skill: {}, 类型: {}", request.getSkillId(), request.getSkillType());

        long startTime = System.currentTimeMillis();

        try {
            Object result = switch (request.getSkillId()) {
                case "knowledge_organize" -> executeKnowledgeOrganize(request);
                default -> {
                    log.warn("未知的 Skill ID: {}", request.getSkillId());
                    yield null;
                }
            };

            long duration = System.currentTimeMillis() - startTime;
            log.info("Skill 执行完成: {}, 耗时: {}ms", request.getSkillId(), duration);

            return SkillExecutionResult.builder()
                    .success(true)
                    .skillId(request.getSkillId())
                    .duration(duration)
                    .result(result)
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Skill 执行失败: {}, 耗时: {}ms, 错误: {}",
                    request.getSkillId(), duration, e.getMessage(), e);

            return SkillExecutionResult.builder()
                    .success(false)
                    .skillId(request.getSkillId())
                    .duration(duration)
                    .error(e.getMessage())
                    .build();
        }
    }

    private KnowledgeOrganizeResult executeKnowledgeOrganize(SkillExecutionRequest request) {
        KnowledgeOrganizeInput input = buildKnowledgeOrganizeInput(request);
        return knowledgeOrganizeSkill.execute(input);
    }

    private KnowledgeOrganizeInput buildKnowledgeOrganizeInput(SkillExecutionRequest request) {
        String content = request.getContent();
        String bookName = request.getMetadata().getOrDefault("bookName", "未命名书籍").toString();

        KnowledgeOrganizeConfig.KnowledgeType primaryType = KnowledgeOrganizeConfig.KnowledgeType.COMPREHENSIVE;
        if (request.getMetadata().containsKey("primaryType")) {
            try {
                primaryType = KnowledgeOrganizeConfig.KnowledgeType.valueOf(
                        request.getMetadata().get("primaryType").toString());
            } catch (IllegalArgumentException e) {
                log.warn("未知的 KnowledgeType: {}", request.getMetadata().get("primaryType"));
            }
        }

        KnowledgeOrganizeConfig config = KnowledgeOrganizeConfig.builder()
                .bookName(bookName)
                .primaryType(primaryType)
                .autoEnhanceMetadata(true)
                .contextTypes(request.getContextTypes())
                .extractionHints(request.getMetadata())
                .build();

        return KnowledgeOrganizeInput.builder()
                .content(content)
                .config(config)
                .originalFileName(request.getMetadata().getOrDefault("fileName", "").toString())
                .sourceChatId(request.getMetadata().getOrDefault("chatId", "").toString())
                .build();
    }

    public SkillDefinition getSkillDefinition(String skillId) {
        return switch (skillId) {
            case "knowledge_organize" -> knowledgeOrganizeSkill.getDefinition();
            default -> null;
        };
    }
}