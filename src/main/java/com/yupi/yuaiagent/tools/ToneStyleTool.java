package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 语气风格转换工具
 * <p>
 * 将文本转换为不同的语气风格
 */
public class ToneStyleTool {

    // 语气风格模板
    private static final Map<String, ToneTemplate> TONE_TEMPLATES = new HashMap<>();

    static {
        // 温柔体贴风格
        TONE_TEMPLATES.put("gentle", new ToneTemplate(
                "温柔体贴",
                new String[]{"亲爱的", "宝贝", ""},
                new String[]{"呢", "哦", "呀", "~"},
                new String[]{"❤️", "🥰", "💕", ""},
                new String[]{
                        "我理解你的感受",
                        "别担心",
                        "我会一直陪着你",
                        "你已经做得很好了"
                }
        ));

        // 幽默风趣风格
        TONE_TEMPLATES.put("humorous", new ToneTemplate(
                "幽默风趣",
                new String[]{"哈喽", "嘿", ""},
                new String[]{"哈哈", "嘻嘻", "😄", ""},
                new String[]{"😂", "🤣", "😜", ""},
                new String[]{
                        "这波操作我给满分",
                        "你可真是个小机灵鬼",
                        "笑死我了",
                        "这也太可爱了吧"
                }
        ));

        // 正式礼貌风格
        TONE_TEMPLATES.put("formal", new ToneTemplate(
                "正式礼貌",
                new String[]{"您好", ""},
                new String[]{"。", ""},
                new String[]{"", ""},
                new String[]{
                        "感谢您的理解",
                        "如有需要请随时告知",
                        "期待您的回复"
                }
        ));

        // 撒娇可爱风格
        TONE_TEMPLATES.put("cute", new ToneTemplate(
                "撒娇可爱",
                new String[]{"人家", "嘤嘤嘤", ""},
                new String[]{"嘛~", "啦~", "呜呜", ""},
                new String[]{"🥺", "😋", "🥹", ""},
                new String[]{
                        "人家好想你呀",
                        "你要对人家好一点哦",
                        "不理你了哼",
                        "抱抱~"
                }
        ));

        // 霸道总裁风格
        TONE_TEMPLATES.put("dominant", new ToneTemplate(
                "霸道总裁",
                new String[]{"", ""},
                new String[]{"。", "！", ""},
                new String[]{"", ""},
                new String[]{
                        "我说的话你最好记住",
                        "从现在开始你是我的人了",
                        "我不允许你离开我的视线",
                        "你只能看着我"
                }
        ));

        // 知心朋友风格
        TONE_TEMPLATES.put("friendly", new ToneTemplate(
                "知心朋友",
                new String[]{"老铁", "兄弟", "姐妹", ""},
                new String[]{"啊", "呢", ""},
                new String[]{"👍", "💪", ""},
                new String[]{
                        "我懂你",
                        "有什么事尽管说",
                        "我挺你",
                        "别怕，有我呢"
                }
        ));
    }

    @Tool(description = "将文本转换为指定的语气风格。支持的风格：gentle(温柔体贴)、humorous(幽默风趣)、formal(正式礼貌)、cute(撒娇可爱)、dominant(霸道总裁)、friendly(知心朋友)")
    public String convertTone(
            @ToolParam(description = "原始文本内容") String content,
            @ToolParam(description = "目标语气风格：gentle/humorous/formal/cute/dominant/friendly") String targetTone) {

        if (content == null || content.isEmpty()) {
            return "内容为空，无法转换";
        }

        ToneTemplate template = TONE_TEMPLATES.get(targetTone.toLowerCase());
        if (template == null) {
            return "不支持的语气风格：" + targetTone + "。支持的风格：gentle(温柔体贴)、humorous(幽默风趣)、formal(正式礼貌)、cute(撒娇可爱)、dominant(霸道总裁)、friendly(知心朋友)";
        }

        return applyTone(content, template);
    }

    @Tool(description = "获取所有支持的语气风格列表及其说明")
    public String listToneStyles() {
        StringBuilder result = new StringBuilder();
        result.append("【支持的语气风格】\n\n");

        result.append("1. gentle - 温柔体贴：适合表达关心、安慰\n");
        result.append("2. humorous - 幽默风趣：适合轻松愉快的对话\n");
        result.append("3. formal - 正式礼貌：适合正式场合\n");
        result.append("4. cute - 撒娇可爱：适合亲密关系中的撒娇\n");
        result.append("5. dominant - 霸道总裁：适合角色扮演\n");
        result.append("6. friendly - 知心朋友：适合朋友间的交流\n");

        return result.toString();
    }

    private String applyTone(String content, ToneTemplate template) {
        Random random = new Random();
        StringBuilder result = new StringBuilder();

        // 添加开头词
        String prefix = template.prefixes[random.nextInt(template.prefixes.length)];
        if (!prefix.isEmpty()) {
            result.append(prefix);
            if (!content.startsWith("，") && !content.startsWith(",")) {
                result.append("，");
            }
        }

        // 添加主体内容
        result.append(content);

        // 添加结尾词
        String suffix = template.suffixes[random.nextInt(template.suffixes.length)];
        if (!suffix.isEmpty() && !content.endsWith(suffix)) {
            // 移除原有的句号
            if (result.toString().endsWith("。") || result.toString().endsWith(".")) {
                result.deleteCharAt(result.length() - 1);
            }
            result.append(suffix);
        }

        // 添加表情
        String emoji = template.emojis[random.nextInt(template.emojis.length)];
        if (!emoji.isEmpty()) {
            result.append(" ").append(emoji);
        }

        return "【" + template.name + "风格】\n" + result.toString();
    }

    /**
     * 语气模板
     */
    private static class ToneTemplate {
        String name;
        String[] prefixes;
        String[] suffixes;
        String[] emojis;
        String[] phrases;

        ToneTemplate(String name, String[] prefixes, String[] suffixes, String[] emojis, String[] phrases) {
            this.name = name;
            this.prefixes = prefixes;
            this.suffixes = suffixes;
            this.emojis = emojis;
            this.phrases = phrases;
        }
    }
}
