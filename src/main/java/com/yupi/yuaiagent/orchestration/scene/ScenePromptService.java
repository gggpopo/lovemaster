package com.yupi.yuaiagent.orchestration.scene;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责场景选择、会话阶段推进和场景化 Prompt 组装。
 */
@Slf4j
@Service
public class ScenePromptService {

    private static final String DEFAULT_SCENE_ID = "general_relationship";

    private final Map<String, SceneDefinition> sceneDefinitionMap = buildSceneDefinitions();

    private final ConcurrentHashMap<String, SceneRuntimeState> sceneRuntimeMap = new ConcurrentHashMap<>();

    /**
     * 解析当前轮次场景上下文（按 chatId 做多轮更新）
     */
    public SceneContext resolve(String chatId, String requestedSceneId, String message) {
        String chatKey = StrUtil.blankToDefault(chatId, "anonymous");
        String normalizedRequestedSceneId = normalizeSceneId(requestedSceneId);
        String normalizedMessage = StrUtil.nullToDefault(message, "").trim();
        long start = System.currentTimeMillis();

        SceneRuntimeState runtimeState = sceneRuntimeMap.compute(chatKey, (key, oldState) ->
                updateRuntimeState(oldState, normalizedRequestedSceneId, normalizedMessage));

        SceneDefinition definition = sceneDefinitionMap.getOrDefault(runtimeState.getSceneId(), sceneDefinitionMap.get(DEFAULT_SCENE_ID));
        String stagePrompt = resolveStagePrompt(definition, runtimeState.getStage());

        SceneContext context = SceneContext.builder()
                .sceneId(runtimeState.getSceneId())
                .sceneName(runtimeState.getSceneName())
                .sceneStage(runtimeState.getStage())
                .turnCount(runtimeState.getTurnCount())
                .basePrompt(definition == null ? "" : StrUtil.nullToDefault(definition.getBasePrompt(), ""))
                .stagePrompt(stagePrompt)
                .recentUserMessages(new ArrayList<>(runtimeState.getRecentUserMessages()))
                .preferredTools(definition == null || definition.getPreferredTools() == null ? Set.of() : definition.getPreferredTools())
                .build();

        log.info("[ScenePromptService-resolve] chatId={}, requestedSceneId={}, resolvedSceneId={}, stage={}, turnCount={}, recentMsgCount={}, costMs={}",
                chatKey,
                normalizedRequestedSceneId,
                context.getSceneId(),
                context.getSceneStage(),
                context.getTurnCount(),
                context.getRecentUserMessages() == null ? 0 : context.getRecentUserMessages().size(),
                System.currentTimeMillis() - start);
        return context;
    }

    /**
     * 构建场景化 Prompt 文本
     */
    public String buildScenePrompt(SceneContext context) {
        if (context == null) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("【场景】").append(StrUtil.blankToDefault(context.getSceneName(), context.getSceneId())).append('\n');
        prompt.append("【阶段】").append(context.getSceneStage() == null ? SceneStage.DISCOVERY : context.getSceneStage()).append('\n');
        prompt.append("【轮次】当前是第 ").append(Math.max(1, context.getTurnCount())).append(" 轮对话。").append('\n');
        if (StrUtil.isNotBlank(context.getBasePrompt())) {
            prompt.append("【场景目标】").append(context.getBasePrompt().trim()).append('\n');
        }
        if (StrUtil.isNotBlank(context.getStagePrompt())) {
            prompt.append("【阶段任务】").append(context.getStagePrompt().trim()).append('\n');
        }
        if (context.getRecentUserMessages() != null && !context.getRecentUserMessages().isEmpty()) {
            prompt.append("【最近用户表达】").append('\n');
            for (int i = 0; i < context.getRecentUserMessages().size(); i++) {
                prompt.append(i + 1).append(". ").append(context.getRecentUserMessages().get(i)).append('\n');
            }
        }
        prompt.append("【输出要求】请围绕当前阶段推进，不要跳步，优先给出可执行建议。");
        return prompt.toString();
    }

    /**
     * 合并场景偏好工具和策略工具
     */
    public Set<String> mergeSuggestedTools(Set<String> policyTools, SceneContext context) {
        Set<String> baseTools = policyTools == null ? Set.of() : policyTools;
        if (context == null || context.getPreferredTools() == null || context.getPreferredTools().isEmpty()) {
            return baseTools;
        }
        Set<String> mergedTools = new java.util.LinkedHashSet<>(baseTools);
        mergedTools.addAll(context.getPreferredTools());
        return mergedTools;
    }

    private SceneRuntimeState updateRuntimeState(SceneRuntimeState oldState,
                                                 String requestedSceneId,
                                                 String message) {
        SceneRuntimeState state = oldState == null ? new SceneRuntimeState() : oldState;
        String previousSceneId = state.getSceneId();
        String targetSceneId = resolveTargetSceneId(previousSceneId, requestedSceneId);
        SceneDefinition definition = sceneDefinitionMap.getOrDefault(targetSceneId, sceneDefinitionMap.get(DEFAULT_SCENE_ID));

        boolean sceneChanged = !StrUtil.equals(previousSceneId, targetSceneId);
        if (sceneChanged) {
            state.setSceneId(targetSceneId);
            state.setSceneName(definition == null ? targetSceneId : definition.getSceneName());
            state.setStage(SceneStage.DISCOVERY);
            state.setTurnCount(0);
            state.setRecentUserMessages(new ArrayList<>());
        }

        int turnCount = state.getTurnCount() + 1;
        SceneStage nextStage = decideStage(state.getStage(), turnCount, message);
        state.setTurnCount(turnCount);
        state.setStage(nextStage);
        state.setUpdatedAt(System.currentTimeMillis());
        updateRecentUserMessages(state, message);
        return state;
    }

    private void updateRecentUserMessages(SceneRuntimeState state, String message) {
        if (state == null || StrUtil.isBlank(message)) {
            return;
        }
        List<String> recent = state.getRecentUserMessages();
        if (recent == null) {
            recent = new ArrayList<>();
            state.setRecentUserMessages(recent);
        }
        recent.add(message.trim());
        while (recent.size() > 3) {
            recent.remove(0);
        }
    }

    private SceneStage decideStage(SceneStage currentStage, int turnCount, String message) {
        String text = StrUtil.nullToDefault(message, "").trim();
        if (containsAny(text, "复盘", "回顾", "反馈", "执行后", "结果")) {
            return SceneStage.REVIEW;
        }
        if (containsAny(text, "怎么做", "怎么办", "步骤", "计划", "下一步", "具体做法", "可执行")) {
            return SceneStage.ACTION_PLAN;
        }
        if (turnCount <= 1) {
            return SceneStage.DISCOVERY;
        }
        if (turnCount <= 2) {
            return SceneStage.GOAL_ALIGN;
        }
        if (currentStage == SceneStage.REVIEW) {
            return SceneStage.REVIEW;
        }
        return SceneStage.ACTION_PLAN;
    }

    private boolean containsAny(String text, String... keywords) {
        if (StrUtil.isBlank(text) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (StrUtil.isNotBlank(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTargetSceneId(String previousSceneId, String requestedSceneId) {
        if (StrUtil.isNotBlank(requestedSceneId) && sceneDefinitionMap.containsKey(requestedSceneId)) {
            return requestedSceneId;
        }
        if (StrUtil.isNotBlank(previousSceneId) && sceneDefinitionMap.containsKey(previousSceneId)) {
            return previousSceneId;
        }
        return DEFAULT_SCENE_ID;
    }

    private String resolveStagePrompt(SceneDefinition definition, SceneStage stage) {
        if (definition == null || definition.getStagePrompts() == null || definition.getStagePrompts().isEmpty()) {
            return "";
        }
        SceneStage finalStage = stage == null ? SceneStage.DISCOVERY : stage;
        String prompt = definition.getStagePrompts().get(finalStage);
        if (StrUtil.isNotBlank(prompt)) {
            return prompt;
        }
        return StrUtil.nullToDefault(definition.getStagePrompts().get(SceneStage.DISCOVERY), "");
    }

    private String normalizeSceneId(String sceneId) {
        if (StrUtil.isBlank(sceneId)) {
            return "";
        }
        return sceneId.trim().toLowerCase();
    }

    private Map<String, SceneDefinition> buildSceneDefinitions() {
        Map<String, SceneDefinition> definitions = new LinkedHashMap<>();
        definitions.put(DEFAULT_SCENE_ID, buildGeneralScene());
        definitions.put("first_date_planning", buildFirstDateScene());
        definitions.put("cold_war_repair", buildColdWarScene());
        definitions.put("breakup_recovery", buildBreakupRecoveryScene());
        return definitions;
    }

    private SceneDefinition buildGeneralScene() {
        Map<SceneStage, String> stagePrompts = new EnumMap<>(SceneStage.class);
        stagePrompts.put(SceneStage.DISCOVERY, "先收集事实：发生了什么、对方怎么回应、你最在意什么。");
        stagePrompts.put(SceneStage.GOAL_ALIGN, "明确本轮目标：你更想修复关系、推进关系，还是先稳定情绪。");
        stagePrompts.put(SceneStage.ACTION_PLAN, "输出 3 步可执行方案，并给出具体沟通话术。");
        stagePrompts.put(SceneStage.REVIEW, "基于执行结果做复盘：有效点、风险点、下一轮调整策略。");
        return SceneDefinition.builder()
                .sceneId(DEFAULT_SCENE_ID)
                .sceneName("通用关系咨询")
                .basePrompt("你是恋爱咨询顾问，回答要温柔但边界清晰，建议必须可执行、可落地。")
                .stagePrompts(stagePrompts)
                .preferredTools(Set.of())
                .build();
    }

    private SceneDefinition buildFirstDateScene() {
        Map<SceneStage, String> stagePrompts = new EnumMap<>(SceneStage.class);
        stagePrompts.put(SceneStage.DISCOVERY, "确认约会对象偏好、预算、时间、城市和关系进度。");
        stagePrompts.put(SceneStage.GOAL_ALIGN, "明确本次约会目标：破冰、升温、确认关系，不同目标给不同策略。");
        stagePrompts.put(SceneStage.ACTION_PLAN, "给出地点+流程+开场白+结束锚点，地点类内容优先调用工具返回卡片。");
        stagePrompts.put(SceneStage.REVIEW, "复盘约会反馈，给出下一次邀约优化建议。");
        return SceneDefinition.builder()
                .sceneId("first_date_planning")
                .sceneName("首次约会规划")
                .basePrompt("你是高情商约会教练，优先帮助用户在自然、尊重的前提下推进关系。")
                .stagePrompts(stagePrompts)
                .preferredTools(Set.of("dateLocation"))
                .build();
    }

    private SceneDefinition buildColdWarScene() {
        Map<SceneStage, String> stagePrompts = new EnumMap<>(SceneStage.class);
        stagePrompts.put(SceneStage.DISCOVERY, "先识别冷战导火索、双方情绪峰值和最近一次沟通失败点。");
        stagePrompts.put(SceneStage.GOAL_ALIGN, "明确优先级：先止损情绪，再恢复沟通，不急于争对错。");
        stagePrompts.put(SceneStage.ACTION_PLAN, "给出破冰话术、边界表达模板和 24 小时内可执行动作。");
        stagePrompts.put(SceneStage.REVIEW, "根据对方最新反馈调整策略，避免二次冲突。");
        return SceneDefinition.builder()
                .sceneId("cold_war_repair")
                .sceneName("冷战修复")
                .basePrompt("你是关系修复顾问，目标是降低冲突、恢复连接，并避免控制型表达。")
                .stagePrompts(stagePrompts)
                .preferredTools(Set.of())
                .build();
    }

    private SceneDefinition buildBreakupRecoveryScene() {
        Map<SceneStage, String> stagePrompts = new EnumMap<>(SceneStage.class);
        stagePrompts.put(SceneStage.DISCOVERY, "收集分手背景、主要冲突和当前情绪波动。");
        stagePrompts.put(SceneStage.GOAL_ALIGN, "明确目标：情绪恢复、理性复盘，或评估是否复合。");
        stagePrompts.put(SceneStage.ACTION_PLAN, "给出 7 天恢复计划，包含情绪稳定、社交修复和风险提醒。");
        stagePrompts.put(SceneStage.REVIEW, "复盘执行结果，判断是否继续推进复合或转向自我成长。");
        return SceneDefinition.builder()
                .sceneId("breakup_recovery")
                .sceneName("分手恢复")
                .basePrompt("你是分手恢复教练，先稳定用户情绪，再提供清晰、可执行的恢复路径。")
                .stagePrompts(stagePrompts)
                .preferredTools(Set.of())
                .build();
    }
}
