package com.qingluo.writeaiagent.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillExecutionResult {

    private boolean success;

    private String skillId;

    private long duration;

    private Object result;

    private String error;

    private String markdownOutput;

    public boolean isSuccess() {
        return success;
    }

    public Object getResult() {
        return result;
    }
}