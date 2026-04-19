package com.qingluo.writeaiagent.service;

import com.qingluo.writeaiagent.skill.SkillExecutionResult;
import com.qingluo.writeaiagent.skill.SkillWorkflowIntegrator;
import com.qingluo.writeaiagent.skill.knowledge.KnowledgeOrganizeResult;
import com.qingluo.writeaiagent.skill.knowledge.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KnowledgeOrganizeService {

    private final SkillWorkflowIntegrator skillWorkflowIntegrator;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeOrganizeService(SkillWorkflowIntegrator skillWorkflowIntegrator,
                                    KnowledgeBaseService knowledgeBaseService) {
        this.skillWorkflowIntegrator = skillWorkflowIntegrator;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public SkillExecutionResult organizeAndSave(String userMessage, String content,
                                                 String bookName, String knowledgeType,
                                                 boolean autoSave) {
        log.info("开始知识编排并保存，请求类型: {}, 自动保存: {}", knowledgeType, autoSave);

        SkillExecutionResult result = skillWorkflowIntegrator.tryExecuteKnowledgeOrganize(
                userMessage, content, bookName, knowledgeType);

        if (result == null) {
            log.warn("Skill 执行返回 null，可能是未触发");
            return null;
        }

        if (!result.isSuccess()) {
            log.error("知识编排失败: {}", result.getError());
            return result;
        }

        if (autoSave && result.getResult() instanceof KnowledgeOrganizeResult organizeResult) {
            String savedPath = knowledgeBaseService.saveToKnowledgeBase(organizeResult, null);
            if (savedPath != null) {
                log.info("知识文档已自动保存到: {}", savedPath);
            }
        }

        return result;
    }

    public String organizeOnly(String userMessage, String content,
                               String bookName, String knowledgeType) {
        SkillExecutionResult result = organizeAndSave(userMessage, content, bookName, knowledgeType, false);

        if (result != null && result.isSuccess() && result.getResult() instanceof KnowledgeOrganizeResult organizeResult) {
            return organizeResult.getGeneratedMarkdown();
        }

        return null;
    }

    public boolean shouldTriggerKnowledgeOrganize(String userMessage) {
        return skillWorkflowIntegrator.shouldTriggerKnowledgeOrganize(userMessage);
    }
}