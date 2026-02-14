package com.yupi.yuaiagent.orchestration.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 结构化响应中的通用区块
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseBlock {

    private String type;
    private String id;
    private String title;
    private Map<String, Object> data;
}
