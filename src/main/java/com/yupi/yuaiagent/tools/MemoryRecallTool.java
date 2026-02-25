package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.chatmemory.MemoryRecallFacade;
import com.yupi.yuaiagent.chatmemory.model.MemoryCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 主动记忆召回工具。
 */
@Slf4j
@Component
public class MemoryRecallTool {

    private final MemoryRecallFacade memoryRecallFacade;

    public MemoryRecallTool(MemoryRecallFacade memoryRecallFacade) {
        this.memoryRecallFacade = memoryRecallFacade;
    }

    @Tool(description = "主动记忆召回工具：当用户询问“你还记得我之前说过什么”“我之前的预算/偏好/纪念日是什么”时调用。输入会话ID和查询语句，返回重排后的记忆候选。")
    public String recallUserMemory(
            @ToolParam(description = "会话ID（chatId/conversationId）") String conversationId,
            @ToolParam(description = "记忆查询语句，例如：我之前约会预算是多少") String query,
            @ToolParam(description = "返回条数，建议 3~8，默认 5") Integer topK
    ) {
        long startMs = System.currentTimeMillis();
        int safeTopK = topK == null || topK <= 0 ? 5 : Math.min(topK, 10);
        log.info("[MemoryRecallTool-recallUserMemory] conversationId={}, queryLength={}, topK={}",
                conversationId, query == null ? 0 : query.length(), safeTopK);

        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(query)) {
            return "记忆召回失败：conversationId 和 query 不能为空。";
        }

        try {
            List<MemoryCandidate> candidates = memoryRecallFacade.recall(conversationId, query, safeTopK);
            if (candidates == null || candidates.isEmpty()) {
                return "记忆召回结果：暂无相关历史记忆。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("记忆召回结果（按相关度排序）：\n");
            int index = 1;
            for (MemoryCandidate candidate : candidates) {
                if (candidate == null || !StringUtils.hasText(candidate.getContent())) {
                    continue;
                }
                sb.append(index++)
                        .append(". [").append(defaultText(candidate.getMemoryType(), "conversation")).append("]")
                        .append(" [").append(defaultText(candidate.getSource(), "unknown")).append("]")
                        .append(" score=").append(round(candidate.getFinalScore()))
                        .append(" content=").append(candidate.getContent());
                String reason = metadataReason(candidate.getMetadata());
                if (StringUtils.hasText(reason)) {
                    sb.append(" reason=").append(reason);
                }
                sb.append("\n");
            }
            log.info("[MemoryRecallTool-recallUserMemory] conversationId={}, resultCount={}, costMs={}",
                    conversationId, candidates.size(), System.currentTimeMillis() - startMs);
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[MemoryRecallTool-recallUserMemory] conversationId={}, query={}", conversationId, query, e);
            return "记忆召回失败：系统暂时无法检索历史记忆，请稍后重试。";
        }
    }

    private String metadataReason(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        Object reason = metadata.get("reason");
        if (reason != null) {
            return String.valueOf(reason);
        }
        Object formula = metadata.get("rerank_formula");
        return formula == null ? "" : String.valueOf(formula);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private double round(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.round(value * 1000D) / 1000D;
    }
}
