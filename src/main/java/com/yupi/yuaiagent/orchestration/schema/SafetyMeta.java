package com.yupi.yuaiagent.orchestration.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 安全信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyMeta {

    private String level;
    private List<String> flags;
}
