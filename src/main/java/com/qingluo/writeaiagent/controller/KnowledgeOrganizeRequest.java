package com.qingluo.writeaiagent.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeOrganizeRequest {

    @NotBlank(message = "内容不能为空")
    private String content;

    private String bookName;

    private String knowledgeType;
}