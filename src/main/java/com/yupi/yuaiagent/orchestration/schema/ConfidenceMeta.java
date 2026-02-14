package com.yupi.yuaiagent.orchestration.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 置信度信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfidenceMeta {

    private Double overall;
}
