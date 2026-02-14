package com.yupi.yuaiagent.orchestration.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一结构化响应协议（assistant_response_v2）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantResponseSchema {

    private String schemaVersion;
    private String responseId;
    private String chatId;
    private String intent;
    private String mode;
    private String summary;
    private SafetyMeta safety;
    private ConfidenceMeta confidence;
    private List<ResponseBlock> blocks;
    private FollowUpMeta followUp;
    private Long createdAt;
}
