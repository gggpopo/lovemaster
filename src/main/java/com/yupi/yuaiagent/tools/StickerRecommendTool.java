package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 表情包推荐工具
 * <p>
 * 根据情感关键词推荐合适的表情包
 */
public class StickerRecommendTool {

    // 表情包库（按情感分类）
    private static final Map<String, List<String>> STICKER_LIBRARY = new HashMap<>();

    static {
        // 开心/高兴
        STICKER_LIBRARY.put("happy", List.of(
                "[开心] 😊",
                "[大笑] 😄",
                "[笑哭] 😂",
                "[比心] 🥰",
                "[撒花] 🎉",
                "[太棒了] 👍",
                "[耶] ✌️"
        ));

        // 爱情/浪漫
        STICKER_LIBRARY.put("love", List.of(
                "[爱心] ❤️",
                "[比心] 💕",
                "[亲亲] 😘",
                "[害羞] 😳",
                "[心动] 💓",
                "[玫瑰] 🌹",
                "[拥抱] 🤗"
        ));

        // 难过/伤心
        STICKER_LIBRARY.put("sad", List.of(
                "[难过] 😢",
                "[哭泣] 😭",
                "[委屈] 🥺",
                "[叹气] 😔",
                "[心碎] 💔",
                "[抱抱] 🫂"
        ));

        // 生气/愤怒
        STICKER_LIBRARY.put("angry", List.of(
                "[生气] 😠",
                "[愤怒] 😡",
                "[哼] 😤",
                "[翻白眼] 🙄",
                "[无语] 😑"
        ));

        // 惊讶/震惊
        STICKER_LIBRARY.put("surprised", List.of(
                "[惊讶] 😮",
                "[震惊] 😱",
                "[哇] 🤩",
                "[天哪] 😲",
                "[不敢相信] 🫢"
        ));

        // 思考/疑惑
        STICKER_LIBRARY.put("thinking", List.of(
                "[思考] 🤔",
                "[疑惑] 😕",
                "[好奇] 🧐",
                "[嗯] 🤨"
        ));

        // 撒娇/可爱
        STICKER_LIBRARY.put("cute", List.of(
                "[可爱] 🥹",
                "[撒娇] 🥺",
                "[卖萌] 😋",
                "[眨眼] 😉",
                "[嘟嘴] 😗"
        ));

        // 鼓励/加油
        STICKER_LIBRARY.put("encourage", List.of(
                "[加油] 💪",
                "[棒棒哒] 👏",
                "[冲鸭] 🦆",
                "[你可以的] ✨",
                "[相信你] 🌟"
        ));
    }

    // 情感关键词映射
    private static final Map<String, String> EMOTION_KEYWORDS = new HashMap<>();

    static {
        // 开心相关
        EMOTION_KEYWORDS.put("开心", "happy");
        EMOTION_KEYWORDS.put("高兴", "happy");
        EMOTION_KEYWORDS.put("快乐", "happy");
        EMOTION_KEYWORDS.put("哈哈", "happy");
        EMOTION_KEYWORDS.put("笑", "happy");
        EMOTION_KEYWORDS.put("棒", "happy");

        // 爱情相关
        EMOTION_KEYWORDS.put("爱", "love");
        EMOTION_KEYWORDS.put("喜欢", "love");
        EMOTION_KEYWORDS.put("想你", "love");
        EMOTION_KEYWORDS.put("心动", "love");
        EMOTION_KEYWORDS.put("浪漫", "love");
        EMOTION_KEYWORDS.put("甜蜜", "love");
        EMOTION_KEYWORDS.put("亲", "love");

        // 难过相关
        EMOTION_KEYWORDS.put("难过", "sad");
        EMOTION_KEYWORDS.put("伤心", "sad");
        EMOTION_KEYWORDS.put("哭", "sad");
        EMOTION_KEYWORDS.put("委屈", "sad");
        EMOTION_KEYWORDS.put("失落", "sad");
        EMOTION_KEYWORDS.put("心碎", "sad");

        // 生气相关
        EMOTION_KEYWORDS.put("生气", "angry");
        EMOTION_KEYWORDS.put("愤怒", "angry");
        EMOTION_KEYWORDS.put("烦", "angry");
        EMOTION_KEYWORDS.put("讨厌", "angry");

        // 惊讶相关
        EMOTION_KEYWORDS.put("惊讶", "surprised");
        EMOTION_KEYWORDS.put("震惊", "surprised");
        EMOTION_KEYWORDS.put("哇", "surprised");
        EMOTION_KEYWORDS.put("天哪", "surprised");

        // 思考相关
        EMOTION_KEYWORDS.put("思考", "thinking");
        EMOTION_KEYWORDS.put("想", "thinking");
        EMOTION_KEYWORDS.put("疑惑", "thinking");
        EMOTION_KEYWORDS.put("为什么", "thinking");

        // 可爱相关
        EMOTION_KEYWORDS.put("可爱", "cute");
        EMOTION_KEYWORDS.put("撒娇", "cute");
        EMOTION_KEYWORDS.put("卖萌", "cute");
        EMOTION_KEYWORDS.put("嘻嘻", "cute");

        // 鼓励相关
        EMOTION_KEYWORDS.put("加油", "encourage");
        EMOTION_KEYWORDS.put("鼓励", "encourage");
        EMOTION_KEYWORDS.put("支持", "encourage");
        EMOTION_KEYWORDS.put("相信", "encourage");
    }

    @Tool(description = "根据情感或场景推荐合适的表情包。输入情感关键词（如：开心、爱、难过、生气、惊讶、思考、可爱、鼓励），返回推荐的表情包列表。")
    public String recommendSticker(
            @ToolParam(description = "情感关键词，如：开心、爱、难过、生气、惊讶、思考、可爱、鼓励") String emotion,
            @ToolParam(description = "推荐数量，默认3个") int count) {

        if (count <= 0) {
            count = 3;
        }
        if (count > 5) {
            count = 5;
        }

        // 查找情感分类
        String category = findCategory(emotion);
        if (category == null) {
            return "未找到与「" + emotion + "」相关的表情包，支持的情感类型：开心、爱、难过、生气、惊讶、思考、可爱、鼓励";
        }

        // 获取表情包列表
        List<String> stickers = STICKER_LIBRARY.get(category);
        if (stickers == null || stickers.isEmpty()) {
            return "暂无「" + emotion + "」相关的表情包";
        }

        // 随机选择指定数量的表情包
        List<String> selected = new ArrayList<>();
        List<String> available = new ArrayList<>(stickers);
        Random random = new Random();

        for (int i = 0; i < count && !available.isEmpty(); i++) {
            int index = random.nextInt(available.size());
            selected.add(available.remove(index));
        }

        StringBuilder result = new StringBuilder();
        result.append("为「").append(emotion).append("」推荐的表情包：\n");
        for (int i = 0; i < selected.size(); i++) {
            result.append(i + 1).append(". ").append(selected.get(i)).append("\n");
        }

        return result.toString();
    }

    private String findCategory(String emotion) {
        if (emotion == null || emotion.isEmpty()) {
            return null;
        }

        // 直接匹配分类名
        if (STICKER_LIBRARY.containsKey(emotion.toLowerCase())) {
            return emotion.toLowerCase();
        }

        // 通过关键词匹配
        for (Map.Entry<String, String> entry : EMOTION_KEYWORDS.entrySet()) {
            if (emotion.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }
}
