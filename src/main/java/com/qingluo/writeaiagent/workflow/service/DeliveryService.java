package com.qingluo.writeaiagent.workflow.service;

import com.qingluo.writeaiagent.constant.FileConstant;
import com.qingluo.writeaiagent.workflow.TaskContext;
import com.qingluo.writeaiagent.workflow.TaskContext.Artifact;
import com.qingluo.writeaiagent.workflow.WorkflowStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 交付服务
 * 负责执行最终交付动作，如PDF生成、文件归档等
 */
@Slf4j
@Service
public class DeliveryService {

    /**
     * 执行交付
     * @param context 任务上下文
     * @return 任务上下文（更新了finalArtifacts）
     */
    public TaskContext deliver(TaskContext context) {
        log.info("开始交付阶段: taskId={}", context.getTaskId());

        List<Artifact> artifacts = new ArrayList<>();

        String content = context.getCurrentDraft();
        if (content == null || content.isEmpty()) {
            context.setError(TaskContext.ErrorInfo.ErrorType.DELIVERY_ERROR, "没有可交付的内容", null);
            return context;
        }

        if (context.getConstraints().getOutputRequirements().isRequirePdf()) {
            Artifact pdfArtifact = generatePdf(context, content);
            if (pdfArtifact != null) {
                artifacts.add(pdfArtifact);
            }
        }

        Artifact markdownArtifact = generateMarkdown(context, content);
        artifacts.add(markdownArtifact);

        context.setFinalArtifacts(artifacts);
        context.setCurrentStage(WorkflowStage.DELIVER);
        context.addExecutionLog(WorkflowStage.DELIVER, "DELIVER",
                String.format("交付完成，生成%d个产物", artifacts.size()), true);

        log.info("交付完成: taskId={}, artifacts={}", context.getTaskId(), artifacts.size());
        return context;
    }

    /**
     * 重新交付（用于交付失败后重试）
     */
    public TaskContext reDeliver(TaskContext context) {
        context.setFinalArtifacts(new ArrayList<>());
        return deliver(context);
    }

    private Artifact generatePdf(TaskContext context, String content) {
        String fileName = resolveFileName(context, "pdf");
        String filePath = FileConstant.FILE_SAVE_DIR + "/pdf/" + fileName;

        try {
            cn.hutool.core.io.FileUtil.mkdir(FileConstant.FILE_SAVE_DIR + "/pdf");

            com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(
                    new com.itextpdf.kernel.pdf.PdfWriter(filePath));
            com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdfDoc);

            com.itextpdf.kernel.font.PdfFont font = createSafeFont();
            if (font != null) {
                document.setFont(font);
            }

            String[] paragraphs = content.split("\n\n");
            for (String para : paragraphs) {
                if (!para.trim().isEmpty()) {
                    document.add(new com.itextpdf.layout.element.Paragraph(para.trim()));
                }
            }

            document.close();

            File file = new File(filePath);
            if (file.exists()) {
                log.info("PDF生成成功: {}", filePath);
                return Artifact.builder()
                        .artifactId(UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .type(Artifact.ArtifactType.PDF)
                        .name(fileName)
                        .filePath(filePath)
                        .fileSize(file.length())
                        .available(true)
                        .build();
            }
        } catch (Exception e) {
            log.error("PDF生成失败: {}", e.getMessage());
            context.setError(TaskContext.ErrorInfo.ErrorType.DELIVERY_ERROR, "PDF生成失败: " + e.getMessage(), e);
        }

        return null;
    }

    private com.itextpdf.kernel.font.PdfFont createSafeFont() throws Exception {
        java.util.List<String> fontCandidates = java.util.List.of(
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
                "/System/Library/Fonts/PingFang.ttc,0",
                "/Library/Fonts/Arial Unicode.ttf",
                "C:/Windows/Fonts/msyh.ttc,0",
                "C:/Windows/Fonts/simsun.ttc,0"
        );

        for (String fontPath : fontCandidates) {
            String[] parts = fontPath.split(",");
            String path = parts[0];
            if (!new File(path).exists()) {
                continue;
            }
            try {
                int ttcIndex = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                return com.itextpdf.kernel.font.PdfFontFactory.createTtcFont(
                        path,
                        ttcIndex,
                        com.itextpdf.io.font.PdfEncodings.IDENTITY_H,
                        com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED,
                        true
                );
            } catch (Exception e) {
                log.warn("加载字体失败: {}", fontPath);
            }
        }

        try {
            return com.itextpdf.kernel.font.PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
        } catch (Exception e) {
            log.warn("加载兜底CJK字体失败", e);
            return null;
        }
    }

    private Artifact generateMarkdown(TaskContext context, String content) {
        String fileName = resolveFileName(context, "md");
        String fileDir = FileConstant.FILE_SAVE_DIR + "/markdown";
        String filePath = fileDir + "/" + fileName;

        try {
            cn.hutool.core.io.FileUtil.mkdir(fileDir);

            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(context.getConstraints().getTaskType() != null ?
                    context.getConstraints().getTaskType().getDescription() : "小说内容").append("\n\n");
            sb.append("**任务ID**: ").append(context.getTaskId()).append("\n");
            sb.append("**生成时间**: ").append(java.time.LocalDateTime.now()).append("\n");
            sb.append("**字数**: ").append(content.length()).append("字\n\n");
            sb.append("---\n\n");
            sb.append(content);

            cn.hutool.core.io.FileUtil.writeString(sb.toString(), filePath, java.nio.charset.StandardCharsets.UTF_8);

            File file = new File(filePath);
            log.info("Markdown生成成功: {}", filePath);

            return Artifact.builder()
                    .artifactId(UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .type(Artifact.ArtifactType.MARKDOWN)
                    .name(fileName)
                    .filePath(filePath)
                    .fileSize(file.length())
                    .available(true)
                    .build();

        } catch (Exception e) {
            log.error("Markdown生成失败: {}", e.getMessage());
            context.setError(TaskContext.ErrorInfo.ErrorType.DELIVERY_ERROR, "Markdown生成失败: " + e.getMessage(), e);
            return null;
        }
    }

    private String resolveFileName(TaskContext context, String extension) {
        String specifiedName = context.getConstraints().getOutputRequirements().getFileName();
        if (specifiedName != null && !specifiedName.isBlank()) {
            return specifiedName + "." + extension;
        }

        String taskType = context.getConstraints().getTaskType() != null ?
                context.getConstraints().getTaskType().getDescription() : "创作";
        String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        return String.format("%s_%s.%s", taskType, timestamp, extension);
    }

    /**
     * 构建交付报告
     */
    public String buildDeliveryReport(TaskContext context) {
        StringBuilder report = new StringBuilder();
        report.append("【交付报告】\n\n");

        if (context.getFinalArtifacts() == null || context.getFinalArtifacts().isEmpty()) {
            report.append("❌ 交付失败：没有生成任何产物\n");
            if (context.getErrorInfo() != null) {
                report.append("错误信息: ").append(context.getErrorInfo().getMessage()).append("\n");
            }
            return report.toString();
        }

        report.append("✅ 交付成功！\n\n");
        report.append("【产物列表】\n");

        for (Artifact artifact : context.getFinalArtifacts()) {
            report.append(String.format("- %s: %s (%s)\n",
                    artifact.getType().getDescription(),
                    artifact.getName(),
                    formatFileSize(artifact.getFileSize())));
            if (artifact.getFilePath() != null) {
                report.append("  路径: ").append(artifact.getFilePath()).append("\n");
            }
        }

        report.append("\n【任务摘要】\n");
        report.append(String.format("- 任务ID: %s\n", context.getTaskId()));
        report.append(String.format("- 任务类型: %s\n",
                context.getConstraints().getTaskType() != null ?
                        context.getConstraints().getTaskType().getDescription() : "未知"));
        report.append(String.format("- 修订次数: %d\n", context.getRevisionCount()));
        report.append(String.format("- 字数: %d字\n", context.getCurrentDraft() != null ? context.getCurrentDraft().length() : 0));

        return report.toString();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
}