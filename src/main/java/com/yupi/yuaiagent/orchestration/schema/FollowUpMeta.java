package com.yupi.yuaiagent.orchestration.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 追问建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpMeta {

    private String question;
    private List<String> choices;
}
