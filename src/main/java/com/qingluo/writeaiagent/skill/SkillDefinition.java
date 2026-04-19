package com.qingluo.writeaiagent.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition {
    private String id;
    private String name;
    private String description;
    private SkillType type;
    private List<String> triggerKeywords;
    private Map<String, Object> metadata;

    public enum SkillType {
        KNOWLEDGE_ORGANIZE,
        CONTENT_GENERATION,
        EVALUATION,
        RETRIEVAL,
        DELIVERY
    }
}