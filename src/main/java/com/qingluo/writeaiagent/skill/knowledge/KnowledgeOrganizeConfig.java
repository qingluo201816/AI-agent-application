package com.qingluo.writeaiagent.skill.knowledge;

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
public class KnowledgeOrganizeConfig {

    private String bookName;

    private KnowledgeType primaryType;

    private List<String> contextTypes;

    private Map<String, Object> extractionHints;

    private boolean autoEnhanceMetadata;

    public enum KnowledgeType {
        CHARACTER_SHEET("人物设定"),
        WORLDVIEW("世界观设定"),
        FACTION_LOCATION_TERM("势力/地点/术语"),
        TIMELINE("时间线"),
        PLOT_SUMMARY("主线剧情摘要"),
        MYSTERY_CLUE("伏笔/谜团/反转线索"),
        RELATIONSHIP("人物关系"),
        COMPREHENSIVE("综合知识手册");

        private final String description;

        KnowledgeType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}