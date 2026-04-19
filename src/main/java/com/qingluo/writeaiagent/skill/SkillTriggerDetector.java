package com.qingluo.writeaiagent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SkillTriggerDetector {

    private static final List<SkillTrigger> TRIGGERS = List.of(
            new SkillTrigger(
                    "knowledge_organize",
                    List.of(
                            "整理成可检索", "生成设定文档", "结构化沉淀", "沉淀成知识",
                            "整理成知识", "归档", "生成知识文档", "人物设定文档",
                            "世界观设定", "时间线整理", "剧情摘要", "势力设定",
                            "角色档案", "关系图谱", "知识沉淀"
                    ),
                    List.of(
                            Pattern.compile("整理.*人物.*档案"),
                            Pattern.compile("沉淀.*知识"),
                            Pattern.compile("生成.*设定.*文档"),
                            Pattern.compile("整理成.*可检索")
                    )
            )
    );

    public SkillMatch detectTrigger(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return SkillMatch.noMatch();
        }

        String lowerMessage = userMessage.toLowerCase();

        for (SkillTrigger trigger : TRIGGERS) {
            for (String keyword : trigger.keywords()) {
                if (lowerMessage.contains(keyword.toLowerCase())) {
                    log.info("检测到 Skill 触发关键字: {} -> {}", keyword, trigger.skillId());
                    return SkillMatch.matched(trigger.skillId(), "keyword:" + keyword);
                }
            }

            for (Pattern pattern : trigger.patterns()) {
                if (pattern.matcher(userMessage).find()) {
                    log.info("检测到 Skill 触发模式: {} -> {}", pattern.pattern(), trigger.skillId());
                    return SkillMatch.matched(trigger.skillId(), "pattern:" + pattern.pattern());
                }
            }
        }

        return SkillMatch.noMatch();
    }

    public boolean shouldTriggerKnowledgeOrganize(String userMessage) {
        SkillMatch match = detectTrigger(userMessage);
        return match.isMatched() && "knowledge_organize".equals(match.skillId());
    }

    private record SkillTrigger(String skillId, List<String> keywords, List<Pattern> patterns) {}

    public record SkillMatch(boolean matched, String skillId, String reason) {
        public static SkillMatch matched(String skillId, String reason) {
            return new SkillMatch(true, skillId, reason);
        }

        public static SkillMatch noMatch() {
            return new SkillMatch(false, null, null);
        }

        public boolean isMatched() {
            return matched;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getReason() {
            return reason;
        }
    }
}