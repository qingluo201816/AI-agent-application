package com.qingluo.writeaiagent.skill.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeOrganizeInput {

    private String content;

    @Builder.Default
    private KnowledgeOrganizeConfig config = new KnowledgeOrganizeConfig();

    private String originalFileName;

    private String sourceChatId;

    public static KnowledgeOrganizeInput of(String content) {
        return KnowledgeOrganizeInput.builder()
                .content(content)
                .config(new KnowledgeOrganizeConfig())
                .build();
    }

    public static KnowledgeOrganizeInput of(String content, KnowledgeOrganizeConfig config) {
        return KnowledgeOrganizeInput.builder()
                .content(content)
                .config(config)
                .build();
    }

    public static KnowledgeOrganizeInput of(String content, String bookName, String knowledgeType) {
        KnowledgeOrganizeConfig config = KnowledgeOrganizeConfig.builder()
                .bookName(bookName)
                .primaryType(KnowledgeOrganizeConfig.KnowledgeType.valueOf(knowledgeType))
                .build();

        return KnowledgeOrganizeInput.builder()
                .content(content)
                .config(config)
                .build();
    }
}