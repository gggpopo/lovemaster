package com.yupi.yuaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yupi.yuaiagent.util.LogFieldUtil.kv;

/**
 * 网页搜索工具
 */
@Slf4j
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        log.info("[WebSearchTool-searchWeb] {}",
                kv("query", query, "queryLength", query == null ? 0 : query.length()));
        long start = System.currentTimeMillis();
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            int total = organicResults == null ? 0 : organicResults.size();
            int selectedCount = Math.min(total, 5);
            List<Object> objects = organicResults == null ? List.of() : organicResults.subList(0, selectedCount);
            // 拼接搜索结果为字符串
            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            long cost = System.currentTimeMillis() - start;
            log.info("[WebSearchTool-searchWeb] {}",
                    kv("query", query,
                            "durationMs", cost,
                            "selectedResultCount", selectedCount,
                            "resultLength", result.length()));
            return result;
        } catch (Exception e) {
            log.error("[WebSearchTool-searchWeb] {}",
                    kv("query", query, "status", "error", "durationMs", System.currentTimeMillis() - start), e);
            return "Error searching Baidu: " + e.getMessage();
        }
    }
}
