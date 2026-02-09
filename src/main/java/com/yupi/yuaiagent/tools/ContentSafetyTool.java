package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 内容安全检测工具
 * <p>
 * 检测文本内容是否包含敏感词或不当内容
 */
public class ContentSafetyTool {

    // 敏感词列表（示例，实际应用中应使用更完整的词库）
    private static final List<String> SENSITIVE_WORDS = List.of(
            // 侮辱性词汇
            "傻逼", "白痴", "蠢货", "废物", "垃圾",
            // 暴力相关
            "打死", "杀了", "弄死",
            // 骚扰相关
            "跟踪", "偷拍", "骚扰",
            // PUA 相关
            "你不配", "没人要你", "离开你谁都行"
    );

    // 不健康恋爱模式关键词
    private static final List<String> UNHEALTHY_PATTERNS = List.of(
            "控制", "监视", "查手机", "不许和别人说话",
            "威胁分手", "你必须听我的", "都是你的错"
    );

    // 电话号码正则
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    // 身份证号正则
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");

    // 银行卡号正则（简化版）
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("\\d{16,19}");

    @Tool(description = "检测文本内容的安全性，包括敏感词、不健康恋爱模式、隐私信息泄露等。返回检测结果和建议。")
    public String checkContentSafety(
            @ToolParam(description = "需要检测的文本内容") String content) {

        if (content == null || content.isEmpty()) {
            return "内容为空，无需检测";
        }

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        // 1. 检测敏感词
        for (String word : SENSITIVE_WORDS) {
            if (content.contains(word)) {
                issues.add("包含敏感词：「" + word + "」");
            }
        }

        // 2. 检测不健康恋爱模式
        for (String pattern : UNHEALTHY_PATTERNS) {
            if (content.contains(pattern)) {
                issues.add("可能存在不健康恋爱模式：「" + pattern + "」");
                suggestions.add("健康的恋爱关系应该建立在相互尊重和信任的基础上");
            }
        }

        // 3. 检测隐私信息
        if (PHONE_PATTERN.matcher(content).find()) {
            issues.add("包含疑似手机号码");
            suggestions.add("请注意保护个人隐私，避免泄露手机号码");
        }

        if (ID_CARD_PATTERN.matcher(content).find()) {
            issues.add("包含疑似身份证号");
            suggestions.add("请注意保护个人隐私，避免泄露身份证号");
        }

        if (BANK_CARD_PATTERN.matcher(content).find()) {
            issues.add("包含疑似银行卡号");
            suggestions.add("请注意保护个人隐私，避免泄露银行卡号");
        }

        // 构建返回结果
        StringBuilder result = new StringBuilder();
        result.append("【内容安全检测报告】\n\n");

        if (issues.isEmpty()) {
            result.append("✅ 检测通过，未发现安全问题\n");
        } else {
            result.append("⚠️ 发现 ").append(issues.size()).append(" 个问题：\n\n");
            for (int i = 0; i < issues.size(); i++) {
                result.append(i + 1).append(". ").append(issues.get(i)).append("\n");
            }

            if (!suggestions.isEmpty()) {
                result.append("\n💡 建议：\n");
                for (String suggestion : suggestions) {
                    result.append("- ").append(suggestion).append("\n");
                }
            }
        }

        return result.toString();
    }

    @Tool(description = "过滤文本中的敏感内容，将敏感词替换为 ***")
    public String filterSensitiveContent(
            @ToolParam(description = "需要过滤的文本内容") String content) {

        if (content == null || content.isEmpty()) {
            return content;
        }

        String filtered = content;

        // 替换敏感词
        for (String word : SENSITIVE_WORDS) {
            filtered = filtered.replace(word, "***");
        }

        // 替换手机号（保留前3后4）
        filtered = PHONE_PATTERN.matcher(filtered).replaceAll(m -> {
            String phone = m.group();
            return phone.substring(0, 3) + "****" + phone.substring(7);
        });

        // 替换身份证号（保留前4后4）
        filtered = ID_CARD_PATTERN.matcher(filtered).replaceAll(m -> {
            String id = m.group();
            return id.substring(0, 4) + "**********" + id.substring(14);
        });

        return filtered;
    }
}
