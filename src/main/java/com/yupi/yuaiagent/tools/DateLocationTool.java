package com.yupi.yuaiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 约会地点推荐工具（高德地图 POI 搜索）
 */
@Slf4j
@Component
public class DateLocationTool {

    private static final String AMAP_POI_TEXT_SEARCH_URL = "https://restapi.amap.com/v3/place/text";

    @Value("${amap.api-key}")
    private String amapApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper;

    public DateLocationTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Tool(description = "根据城市和关键词搜索约会地点，返回推荐的餐厅、咖啡厅、公园等约会好去处")
    public String searchDateLocations(
            @ToolParam(description = "城市名称") String city,
            @ToolParam(description = "地点类型，如餐厅、咖啡厅、电影院、公园、商场") String type,
            @ToolParam(description = "关键词，如浪漫、安静、约会") String keyword
    ) {
        if (!StringUtils.hasText(city)) {
            return "请先提供城市名称（如：北京、上海、杭州）。";
        }

        String keywords = Stream.of(keyword, type)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(""));
        if (!StringUtils.hasText(keywords)) {
            return "请至少提供地点类型或关键词（如：餐厅 / 浪漫 / 安静）。";
        }

        try {
            // 这里不要使用 build(true)：它要求 queryParam 已经是 URL 编码后的字符串，否则中文会触发 IllegalArgumentException
            URI uri = UriComponentsBuilder.fromHttpUrl(AMAP_POI_TEXT_SEARCH_URL)
                    .queryParam("key", amapApiKey)
                    .queryParam("keywords", keywords)
                    .queryParam("city", city)
                    .queryParam("citylimit", "true")
                    .queryParam("offset", 5)
                    .queryParam("extensions", "all")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            log.info("[Call-AmapPOI] city={}, keywords={}", city, keywords);
            long start = System.currentTimeMillis();
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(uri, String.class);
            long cost = System.currentTimeMillis() - start;
            log.info("[Call-AmapPOI] Response status={}, duration={}ms", responseEntity.getStatusCode(), cost);

            if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
                return "查询高德地图失败，请稍后再试。";
            }

            JsonNode root = objectMapper.readTree(responseEntity.getBody());
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                String info = root.path("info").asText("未知错误");
                return "查询高德地图失败：" + info + "。请检查 `amap.api-key` 是否配置正确，或稍后重试。";
            }

            JsonNode pois = root.path("pois");
            if (!pois.isArray() || pois.isEmpty()) {
                return "没有在「" + city + "」找到符合「" + keywords + "」的地点，可以换个关键词（如：氛围好/安静/情侣）或换成附近商圈名称再试。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("为你在「").append(city).append("」找到以下约会地点（最多 5 条）：\n");
            for (int i = 0; i < Math.min(5, pois.size()); i++) {
                JsonNode poi = pois.get(i);
                String name = poi.path("name").asText("未命名地点");
                String address = poi.path("address").asText("暂无");
                String tel = poi.path("tel").asText("暂无");
                String poiType = poi.path("type").asText("");
                String rating = poi.path("biz_ext").path("rating").asText("暂无");

                sb.append(i + 1).append(". ").append(name);
                if (StringUtils.hasText(poiType)) {
                    sb.append("（").append(poiType).append("）");
                }
                sb.append("\n");
                sb.append("地址：").append(StringUtils.hasText(address) ? address : "暂无").append("\n");
                sb.append("评分：").append(StringUtils.hasText(rating) ? rating : "暂无").append("\n");
                sb.append("电话：").append(StringUtils.hasText(tel) ? tel : "暂无").append("\n");
                if (i < Math.min(5, pois.size()) - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (RestClientException e) {
            log.error("[Call-AmapPOI] Exception, city={}, keywords={}", city, keywords, e);
            return "查询高德地图失败（网络请求异常），请稍后再试。";
        } catch (Exception e) {
            log.error("[Call-AmapPOI] ParseException, city={}, keywords={}", city, keywords, e);
            return "解析高德地图返回结果失败，请稍后再试。";
        }
    }
}
