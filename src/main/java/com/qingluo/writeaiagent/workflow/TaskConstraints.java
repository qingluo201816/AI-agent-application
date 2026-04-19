package com.qingluo.writeaiagent.workflow;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 任务约束结构化对象
 * 在意图识别阶段生成，包含任务的所有约束条件
 */
@Data
@Builder
public class TaskConstraints {

    /**
     * 任务类型
     */
    private TaskType taskType;

    /**
     * 用户输入的原始描述
     */
    private String originalInput;

    /**
     * 字数要求
     */
    private WordCountRequirement wordCount;

    /**
     * 视角要求 (POV)
     */
    private String pov;

    /**
     * 风格要求
     */
    private String style;

    /**
     * 氛围/基调要求
     */
    private String tone;

    /**
     * 任务目标描述
     */
    private String goalDescription;

    /**
     * 必须包含的内容列表
     */
    private List<String> mustInclude;

    /**
     * 必须避免的内容列表
     */
    private List<String> mustAvoid;

    /**
     * 输出要求
     */
    private OutputRequirements outputRequirements;

    /**
     * 后续检索需要的上下文类型
     */
    private List<ContextType> contextTypes;

    /**
     * 扩展约束字段（用于灵活扩展）
     */
    private Map<String, Object> extraConstraints;

    /**
     * 任务类型枚举
     */
    public enum TaskType {
        CHAPTER_CONTINUE("chapter_continue", "章节续写"),
        PLOT_TRACE("plot_trace", "情节追溯"),
        INSPIRATION("inspiration", "灵感提供"),
        SETTING_CHECK("setting_check", "设定核查"),
        CHARACTER_ARCHIVE("character_archive", "角色档案整理"),
        WORLD_BUILDING("world_building", "世界观设定"),
        TIMELINE_SORT("timeline_sort", "时间线整理"),
        EXPORT_PDF("export_pdf", "导出PDF"),
        CUSTOM_TASK("custom_task", "自定义任务");

        private final String code;
        private final String description;

        TaskType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 字数要求
     */
    @Data
    @Builder
    public static class WordCountRequirement {
        private Integer min;
        private Integer max;
        private Integer target;
        private String unit;

        public static WordCountRequirement of(Integer min, Integer max) {
            return WordCountRequirement.builder()
                    .min(min)
                    .max(max)
                    .target((min + max) / 2)
                    .unit("字")
                    .build();
        }

        public static WordCountRequirement of(Integer target) {
            return WordCountRequirement.builder()
                    .target(target)
                    .min((int) (target * 0.8))
                    .max((int) (target * 1.2))
                    .unit("字")
                    .build();
        }
    }

    /**
     * 输出要求
     */
    @Data
    @Builder
    public static class OutputRequirements {
        private boolean requirePdf;
        private String fileName;
        private String format;
        private boolean includeMetadata;
        private String outputDirectory;

        public static OutputRequirements defaultRequirements() {
            return OutputRequirements.builder()
                    .requirePdf(false)
                    .format("markdown")
                    .includeMetadata(true)
                    .outputDirectory("output")
                    .build();
        }

        public static OutputRequirements pdfOnly(String fileName) {
            return OutputRequirements.builder()
                    .requirePdf(true)
                    .fileName(fileName)
                    .format("pdf")
                    .includeMetadata(true)
                    .outputDirectory("output/pdf")
                    .build();
        }
    }

    /**
     * 上下文类型 - 用于指导检索方向
     */
    public enum ContextType {
        RECENT_CHAPTERS("recent_chapters", "最近章节"),
        CHARACTER_PROFILES("character_profiles", "角色档案"),
        ACTIVE_PLOTLINE("active_plotline", "进行中的情节线"),
        TIMELINE("timeline", "时间线"),
        MYSTERY_CLUES("mystery_clues", "悬念线索"),
        WORLD_SETTING("world_setting", "世界观设定"),
        RELATIONSHIP_MAP("relationship_map", "关系图谱"),
        PREVIOUS_DRAFTS("previous_drafts", "之前草稿"),
        THEME_ANALYSIS("theme_analysis", "主题分析"),
        WRITING_STYLE("writing_style", "文风指南");

        private final String code;
        private final String description;

        ContextType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }
}