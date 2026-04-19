package com.qingluo.writeaiagent.skill.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeOrganizeResult {

    private String rawInput;

    private KnowledgeOrganizeConfig config;

    @Builder.Default
    private List<Section> sections = new ArrayList<>();

    private Map<String, Object> frontMatter;

    private String generatedMarkdown;

    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String title;
        private String sectionType;
        private String normalizedSectionType;
        @Builder.Default
        private List<Entry> entries = new ArrayList<>();
        private String bodyContent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String title;
        private String entryType;
        @Builder.Default
        private Map<String, Object> metadata = new LinkedHashMap<>();
        private String content;
        private int entryOrder;
    }

    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }

    public String buildMarkdown() {
        StringBuilder sb = new StringBuilder();

        sb.append("---\n");
        if (frontMatter != null) {
            for (Map.Entry<String, Object> entry : frontMatter.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("---\n\n");

        for (Section section : sections) {
            sb.append("## ").append(section.getTitle()).append("\n\n");

            if (section.getEntries().isEmpty() && section.getBodyContent() != null
                    && !section.getBodyContent().isBlank()) {
                sb.append(section.getBodyContent()).append("\n\n");
            } else {
                for (Entry entry : section.getEntries()) {
                    sb.append("### ").append(entry.getTitle()).append("\n");

                    if (entry.getMetadata() != null && !entry.getMetadata().isEmpty()) {
                        for (Map.Entry<String, Object> meta : entry.getMetadata().entrySet()) {
                            if (meta.getValue() != null && !meta.getValue().toString().isBlank()) {
                                sb.append("- ").append(meta.getKey()).append(": ")
                                        .append(meta.getValue()).append("\n");
                            }
                        }
                    }

                    if (entry.getContent() != null && !entry.getContent().isBlank()) {
                        sb.append("\n").append(entry.getContent()).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    public static KnowledgeOrganizeResult empty() {
        return KnowledgeOrganizeResult.builder()
                .sections(new ArrayList<>())
                .frontMatter(new LinkedHashMap<>())
                .warnings(new ArrayList<>())
                .build();
    }
}