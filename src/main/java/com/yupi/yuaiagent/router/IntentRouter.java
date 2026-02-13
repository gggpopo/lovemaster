package com.yupi.yuaiagent.router;

import com.yupi.yuaiagent.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图路由器：优先使用关键词正则快速分类；未命中时使用 LLM 辅助分类。
 */
@Slf4j
@Component
public class IntentRouter {

    /**
     * 意图类型。
     */
    public enum IntentType {
        CHITCHAT,
        EMOTION_SUPPORT,
        DATE_PLANNING,
        GIFT_ADVICE,
        LOVE_COPYWRITING,
        RELATIONSHIP_QA,
        IMAGE_REQUEST,
        UNSAFE
    }

    /**
     * 路由结果。
     */
    public static class RouteResult {
        private final IntentType intentType;
        private final double confidence;
        private final Set<String> suggestedTools;
        private final String modelProfile;
        private final float temperature;

        public RouteResult(IntentType intentType,
                           double confidence,
                           Set<String> suggestedTools,
                           String modelProfile,
                           float temperature) {
            this.intentType = intentType;
            this.confidence = confidence;
            this.suggestedTools = suggestedTools == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(suggestedTools));
            this.modelProfile = modelProfile;
            this.temperature = temperature;
        }

        public IntentType getIntentType() {
            return intentType;
        }

        public double getConfidence() {
            return confidence;
        }

        public Set<String> getSuggestedTools() {
            return suggestedTools;
        }

        public String getModelProfile() {
            return modelProfile;
        }

        public float getTemperature() {
            return temperature;
        }
    }

    private static class RouteConfig {
        private final Set<String> tools;
        private final String modelProfile;
        private final float temperature;

        private RouteConfig(Set<String> tools, String modelProfile, float temperature) {
            this.tools = tools == null ? Set.of() : Set.copyOf(tools);
            this.modelProfile = modelProfile;
            this.temperature = temperature;
        }
    }

    // 关键词层：尽量覆盖 70%+，避免走 LLM
    private static final Pattern UNSAFE_PATTERN = Pattern.compile(
            // 脏话/辱骂（示例）
            "(傻[逼B]|煞笔|草泥马|妈的|你妈|滚你|操你|他妈的|垃圾|废物|去死|死全家|" +
                    // 自残/违法/敏感（示例）
                    "自杀|爆炸|炸弹|毒品|枪|走私|诈骗|" +
                    // 色情
                    "色情|黄片|成人视频|约炮|" +
                    // 其他敏感
                    "恐怖袭击|极端主义)"
    );

    private static final Pattern DATE_PLANNING_PATTERN = Pattern.compile(
            "去哪|约会|餐厅|电影院|景点|美食|好吃|探店|吃什么|店铺|"
                    + "推荐(?:一下|些)?(?:店|餐厅|饭店|馆子|火锅|烤肉|烧烤|咖啡|咖啡店|甜品)|"
                    + "附近(?:美食|好吃|餐厅|吃的)|打卡"
    );
    private static final Pattern GIFT_ADVICE_PATTERN = Pattern.compile("送什么|礼物|纪念日");
    private static final Pattern LOVE_COPYWRITING_PATTERN = Pattern.compile("写一段|表白|情话|文案");
    private static final Pattern IMAGE_REQUEST_PATTERN = Pattern.compile("找图|图片|壁纸");

    // LLM 输出解析：CATEGORY|0.95
    private static final Pattern LLM_RESULT_PATTERN = Pattern.compile("([A-Z_]+)\\s*\\|\\s*([0-9]+(?:\\.[0-9]+)?)");

    private static final ChatOptions LLM_CLASSIFY_OPTIONS = new StaticChatOptions(50, 0.0);

    /**
     * 由于项目为避免类冲突可能未引入 spring-ai-core 的 ChatOptionsBuilder，这里用最小实现。
     */
    private static class StaticChatOptions implements ChatOptions {

        private final Integer maxTokens;
        private final Double temperature;

        private StaticChatOptions(Integer maxTokens, Double temperature) {
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }

        @Override
        public String getModel() {
            return null;
        }

        @Override
        public Double getFrequencyPenalty() {
            return null;
        }

        @Override
        public Integer getMaxTokens() {
            return maxTokens;
        }

        @Override
        public Double getPresencePenalty() {
            return null;
        }

        @Override
        public List<String> getStopSequences() {
            return null;
        }

        @Override
        public Double getTemperature() {
            return temperature;
        }

        @Override
        public Integer getTopK() {
            return null;
        }

        @Override
        public Double getTopP() {
            return null;
        }

        @Override
        public ChatOptions copy() {
            return new StaticChatOptions(maxTokens, temperature);
        }
    }

    private static final Map<IntentType, RouteConfig> ROUTE_CONFIGS;

    static {
        Map<IntentType, RouteConfig> m = new EnumMap<>(IntentType.class);
        m.put(IntentType.CHITCHAT, new RouteConfig(Set.of(), "fast", 0.7f));
        m.put(IntentType.EMOTION_SUPPORT, new RouteConfig(Set.of("emotionDetection"), "standard", 0.6f));
        m.put(IntentType.DATE_PLANNING, new RouteConfig(Set.of("dateLocation", "weather"), "standard", 0.3f));
        m.put(IntentType.GIFT_ADVICE, new RouteConfig(Set.of("giftRecommend", "webSearch"), "standard", 0.5f));
        m.put(IntentType.LOVE_COPYWRITING, new RouteConfig(Set.of(), "creative", 0.9f));
        m.put(IntentType.RELATIONSHIP_QA, new RouteConfig(Set.of(), "standard", 0.5f));
        m.put(IntentType.IMAGE_REQUEST, new RouteConfig(Set.of("imageSearch"), "fast", 0.3f));
        m.put(IntentType.UNSAFE, new RouteConfig(Set.of(), "fast", 0.0f));
        ROUTE_CONFIGS = Collections.unmodifiableMap(m);
    }

    private final ChatModel chatModel;

    /**
     * 注入 ChatModel（仅用于 LLM 辅助分类；不走 ChatClient，避免 Advisor/工具/记忆循环）。
     */
    public IntentRouter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 对用户消息进行意图分类。
     *
     * - 关键词层命中：< 100ms
     * - LLM 层兜底：< 500ms（依赖网络/模型）
     */
    public RouteResult classify(String userMessage) {
        long startMs = System.currentTimeMillis();

        String userId = TenantContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            userId = "anonymous";
        }

        String msg = userMessage == null ? "" : userMessage.trim();
        if (!StringUtils.hasText(msg)) {
            RouteResult r = buildResult(IntentType.CHITCHAT, 0.5);
            log.info("[INTENT] userId={} intent={} confidence={} cost={}ms", userId, r.getIntentType(), r.getConfidence(), System.currentTimeMillis() - startMs);
            return r;
        }

        // 第一层：关键词快速匹配（UNSAFE 绝不调用 LLM）
        if (UNSAFE_PATTERN.matcher(msg).find()) {
            RouteResult r = buildResult(IntentType.UNSAFE, 1.0);
            log.info("[INTENT] userId={} intent={} confidence={} cost={}ms", userId, r.getIntentType(), r.getConfidence(), System.currentTimeMillis() - startMs);
            return r;
        }
        if (DATE_PLANNING_PATTERN.matcher(msg).find()) {
            log.info("[IntentRouter-keyword] userId={} intent={} msgLen={}",
                    userId, IntentType.DATE_PLANNING, msg.length());
            RouteResult r = buildResult(IntentType.DATE_PLANNING, 0.92);
            log.info("[INTENT] userId={} intent={} confidence={} cost={}ms", userId, r.getIntentType(), r.getConfidence(), System.currentTimeMillis() - startMs);
            return r;
        }
        if (GIFT_ADVICE_PATTERN.matcher(msg).find()) {
            RouteResult r = buildResult(IntentType.GIFT_ADVICE, 0.92);
            log.info("[INTENT] userId={} intent={} confidence={} cost={}ms", userId, r.getIntentType(), r.getConfidence(), System.currentTimeMillis() - startMs);
            return r;
        }
        if (LOVE_COPYWRITING_PATTERN.matcher(msg).find()) {
            RouteResult r = buildResult(IntentType.LOVE_COPYWRITING, 0.92);
            log.info("[INTENT] userId={} intent={} confidence={} cost={}ms", userId, r.getIntentType(), r.getConfidence(), System.currentTimeMillis() - startMs);
            return r;
        }
        if (IMAGE_REQUEST_PATTERN.matcher(msg).find()) {
            RouteResult r = buildResult(IntentType.IMAGE_REQUEST, 0.92);
            log.info("[INTENT] userId={} intent={} confidence={} cost={}ms", userId, r.getIntentType(), r.getConfidence(), System.currentTimeMillis() - startMs);
            return r;
        }

        // 第二层：LLM 辅助分类（仅在关键词未命中时触发；不挂工具、不走记忆/RAG）
        RouteResult llmResult = classifyByLlm(msg);
        log.info("[INTENT] userId={} intent={} confidence={} cost={}ms", userId, llmResult.getIntentType(), llmResult.getConfidence(), System.currentTimeMillis() - startMs);
        return llmResult;
    }

    private RouteResult classifyByLlm(String msg) {
        long startMs = System.currentTimeMillis();

        String categories = String.join(", ",
                IntentType.CHITCHAT.name(),
                IntentType.EMOTION_SUPPORT.name(),
                IntentType.DATE_PLANNING.name(),
                IntentType.GIFT_ADVICE.name(),
                IntentType.LOVE_COPYWRITING.name(),
                IntentType.RELATIONSHIP_QA.name(),
                IntentType.IMAGE_REQUEST.name()
        );

        String systemPrompt = "将以下用户消息分类为以下类别之一：[" + categories + "]。" +
                "只返回类别名和置信度，格式：CATEGORY|0.95";

        try {
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemPrompt), new UserMessage(msg)),
                    LLM_CLASSIFY_OPTIONS
            );
            String text = chatModel.call(prompt).getResult().getOutput().getText();
            RouteResult parsed = parseLlmResult(text);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("[IntentRouter] LLM classify failed, costMs={}, msgLen={}", System.currentTimeMillis() - startMs, msg.length(), e);
        }
        // 兜底：无法解析或调用失败
        return buildResult(IntentType.CHITCHAT, 0.55);
    }

    private RouteResult parseLlmResult(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        // 兼容模型输出多行时取第一行
        int n = s.indexOf('\n');
        if (n > 0) {
            s = s.substring(0, n).trim();
        }
        Matcher m = LLM_RESULT_PATTERN.matcher(s);
        if (!m.find()) {
            return null;
        }
        String cat = m.group(1);
        String confStr = m.group(2);

        IntentType intentType;
        try {
            intentType = IntentType.valueOf(cat.toUpperCase(Locale.ROOT));
        } catch (Exception ignore) {
            return null;
        }
        if (intentType == IntentType.UNSAFE) {
            // 约束：UNSAFE 绝不由 LLM 返回（即便返回也丢弃）
            return null;
        }

        double conf;
        try {
            conf = Double.parseDouble(confStr);
        } catch (Exception e) {
            conf = 0.6;
        }
        // 合理区间
        if (conf < 0) conf = 0;
        if (conf > 1) conf = 1;
        return buildResult(intentType, conf);
    }

    private RouteResult buildResult(IntentType intentType, double confidence) {
        RouteConfig cfg = ROUTE_CONFIGS.getOrDefault(intentType, ROUTE_CONFIGS.get(IntentType.CHITCHAT));
        return new RouteResult(intentType, confidence, cfg.tools, cfg.modelProfile, cfg.temperature);
    }
}
