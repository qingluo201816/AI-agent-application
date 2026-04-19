package com.qingluo.writeaiagent.workflow;

/**
 * 超级智能体工作流阶段枚举
 * 定义了任务执行的五个核心阶段
 */
public enum WorkflowStage {

    /**
     * 意图识别 + 结构化约束阶段
     * 分析用户输入，生成结构化任务信息
     */
    INTENT_PARSE("意图识别"),

    /**
     * 检索阶段
     * 根据任务类型和约束条件执行知识检索
     */
    RETRIEVE("知识检索"),

    /**
     * 生成阶段
     * 基于检索结果生成符合要求的内容
     */
    GENERATE("内容生成"),

    /**
     * 结果评估与路径决策阶段
     * 对生成结果进行硬约束和软质量检查，决定下一步
     */
    EVALUATE("结果评估"),

    /**
     * 生成提交阶段
     * 执行最终交付动作，如PDF生成
     */
    DELIVER("交付执行"),

    /**
     * 任务完成
     */
    COMPLETED("已完成"),

    /**
     * 任务失败
     */
    FAILED("已失败"),

    /**
     * 等待用户输入
     */
    WAITING_FOR_INPUT("等待输入"),

    /**
     * 空闲状态
     */
    IDLE("空闲");

    private final String description;

    WorkflowStage(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 检查是否是执行阶段（不包括 COMPLETED、FAILED、IDLE）
     */
    public boolean isExecutableStage() {
        return this == INTENT_PARSE || this == RETRIEVE ||
               this == GENERATE || this == EVALUATE || this == DELIVER;
    }

    /**
     * 获取下一执行阶段
     */
    public WorkflowStage nextStage() {
        return switch (this) {
            case INTENT_PARSE -> RETRIEVE;
            case RETRIEVE -> GENERATE;
            case GENERATE -> EVALUATE;
            case EVALUATE -> {
                // EVALUATE 会根据评估结果决定下一步，由 WorkflowEngine 处理
                yield DELIVER;
            }
            case DELIVER -> COMPLETED;
            default -> this;
        };
    }

    /**
     * 是否可以回跳到指定阶段
     */
    public boolean canFallbackTo(WorkflowStage target) {
        if (target == INTENT_PARSE) {
            return this == RETRIEVE || this == GENERATE || this == EVALUATE;
        }
        if (target == RETRIEVE) {
            return this == GENERATE || this == EVALUATE;
        }
        if (target == GENERATE) {
            return this == EVALUATE;
        }
        return false;
    }
}