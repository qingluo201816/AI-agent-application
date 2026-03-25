package com.qingluo.writeaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.qingluo.writeaiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * PDF 生成工具
 */
@Slf4j
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = createSafeFont();
                if (font != null) {
                    document.setFont(font);
                }
                // 创建段落
                Paragraph paragraph = new Paragraph(content);
                // 添加段落并关闭文档
                document.add(paragraph);
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private PdfFont createSafeFont() throws IOException {
        // 优先使用系统中的中文字体，避免中文内容乱码
        List<String> fontCandidates = List.of(
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
                return PdfFontFactory.createTtcFont(
                        path,
                        ttcIndex,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED,
                        true
                );

            } catch (Exception e) {
                log.warn("加载字体失败: {}", fontPath, e);
            }
        }
        // 兜底：使用 iText 的 CJK 字体映射
        try {
            return PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
        } catch (Exception e) {
            log.warn("加载兜底 CJK 字体失败", e);
            return null;
        }
    }
}
