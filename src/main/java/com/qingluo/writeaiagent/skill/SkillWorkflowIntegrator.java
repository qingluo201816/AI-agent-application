package com.qingluo.writeaiagent.skill;

import com.qingluo.writeaiagent.skill.knowledge.KnowledgeOrganizeResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SkillWorkflowIntegrator {

    private final SkillExecutor skillExecutor;
    private final SkillTriggerDetector triggerDetector;

    public SkillWorkflowIntegrator(SkillExecutor skillExecutor, SkillTriggerDetector triggerDetector) {
        this.skillExecutor = skillExecutor;
        this.triggerDetector = triggerDetector;
    }

    public SkillExecutionResult tryExecuteKnowledgeOrganize(String userMessage, String content,
                                                            String bookName, String knowledgeType) {
        SkillTriggerDetector.SkillMatch match = triggerDetector.detectTrigger(userMessage);

        if (!match.isMatched() || !"knowledge_organize".equals(match.getSkillId())) {
            log.debug("未检测到 knowledge_organize 触发条件，跳过 Skill 执行");
            return null;
        }

        log.info("检测到知识编排 Skill 触发，原因: {}", match.getReason());

        SkillExecutionRequest request = SkillExecutionRequest.builder()
                .skillId("knowledge_organize")
                .skillType("KNOWLEDGE_ORGANIZE")
                .content(content)
                .metadata(java.util.Map.of(
                        "bookName", bookName != null ? bookName : "未命名书籍",
                        "primaryType", knowledgeType != null ? knowledgeType : "COMPREHENSIVE",
                        "triggerReason", match.getReason()
                ))
                .build();

        SkillExecutionResult result = skillExecutor.execute(request);

        if (result.isSuccess() && result.getResult() instanceof KnowledgeOrganizeResult organizeResult) {
            result.setMarkdownOutput(organizeResult.getGeneratedMarkdown());
        }

        return result;
    }

    public boolean shouldTriggerKnowledgeOrganize(String userMessage) {
        return triggerDetector.shouldTriggerKnowledgeOrganize(userMessage);
    }

    public SkillDefinition getSkillDefinition(String skillId) {
        return skillExecutor.getSkillDefinition(skillId);
    }
}