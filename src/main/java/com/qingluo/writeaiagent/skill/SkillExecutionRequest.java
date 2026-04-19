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
public class SkillExecutionRequest {

    private String skillId;

    private String skillType;

    private String content;

    private Map<String, Object> metadata;

    private List<String> contextTypes;

    public static SkillExecutionRequest forKnowledgeOrganize(String content) {
        return SkillExecutionRequest.builder()
                .skillId("knowledge_organize")
                .skillType("KNOWLEDGE_ORGANIZE")
                .content(content)
                .build();
    }

    public static SkillExecutionRequest forKnowledgeOrganize(String content, String bookName, String knowledgeType) {
        return SkillExecutionRequest.builder()
                .skillId("knowledge_organize")
                .skillType("KNOWLEDGE_ORGANIZE")
                .content(content)
                .metadata(Map.of(
                        "bookName", bookName,
                        "primaryType", knowledgeType
                ))
                .build();
    }
}