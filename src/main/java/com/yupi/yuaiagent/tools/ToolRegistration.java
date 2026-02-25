package com.yupi.yuaiagent.tools;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Resource
    private DateLocationTool dateLocationTool;

    @Resource
    private WeatherTool weatherTool;

    @Resource
    private DateCalendarTool dateCalendarTool;

    @Resource
    private EmotionDetectTool emotionDetectTool;

    @Resource
    private MemoryRecallTool memoryRecallTool;

    // 工具开关配置
    @Value("${app.tools.sticker.enabled:true}")
    private boolean stickerEnabled;

    @Value("${app.tools.content-safety.enabled:true}")
    private boolean contentSafetyEnabled;

    @Value("${app.tools.tone-style.enabled:true}")
    private boolean toneStyleEnabled;

    @Bean
    public ToolCallback[] allTools() {
        // 基础工具（始终启用）
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();

        List<Object> tools = new ArrayList<>(Arrays.asList(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                dateLocationTool,
                weatherTool,
                dateCalendarTool,
                emotionDetectTool,
                memoryRecallTool
        ));

        // 可选工具（根据配置开关）
        if (stickerEnabled) {
            tools.add(new StickerRecommendTool());
        }

        if (contentSafetyEnabled) {
            tools.add(new ContentSafetyTool());
        }

        if (toneStyleEnabled) {
            tools.add(new ToneStyleTool());
        }

        return ToolCallbacks.from(tools.toArray());
    }
}
