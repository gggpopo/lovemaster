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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    @Tool(description = "约会地点推荐工具：根据城市和关键词搜索推荐适合约会的餐厅、景点、咖啡厅等地点，返回地点名称、地址、评分、联系电话和实景图片。当用户询问去哪约会、推荐餐厅景点、找约会场所时使用此工具。")
    public String searchDateLocations(
            @ToolParam(description = "搜索关键词，如：西餐厅、公园、咖啡厅、电影院、景点") String keywords,
            @ToolParam(description = "城市名称，如：北京、上海、杭州") String city,
            @ToolParam(description = "搜索类型：restaurant(餐厅)、scenic(景点)、cafe(咖啡厅)、cinema(电影院)、mall(商场)、park(公园)、bar(酒吧)") String type
    ) {
        long startMs = System.currentTimeMillis();
        log.info("[DateLocationTool] searchDateLocations start, city={}, keywords={}, type={}", city, keywords, type);

        if (!StringUtils.hasText(city)) {
            return "请先提供城市名称（如：北京、上海、杭州）。";
        }

        // 兼容：keywords 为空时，用 type 或兜底关键词
        String finalKeywords = StringUtils.hasText(keywords)
                ? keywords.trim()
                : (StringUtils.hasText(type) ? type.trim() : "约会");

        String poiTypeCode = getPoiTypeCode(type);

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(AMAP_POI_TEXT_SEARCH_URL)
                    .queryParam("key", amapApiKey)
                    .queryParam("keywords", finalKeywords)
                    .queryParam("city", city)
                    .queryParam("types", poiTypeCode)
                    .queryParam("citylimit", "true")
                    .queryParam("offset", 6)
                    // 重要：extensions=all 才会返回 photos
                    .queryParam("extensions", "all")
                    .queryParam("output", "json")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            log.info("[Call-AmapPOI] uri={}", uri);
            long httpStart = System.currentTimeMillis();
            ResponseEntity<String> responseEntity = restTemplate.getForEntity(uri, String.class);
            log.info("[Call-AmapPOI] status={}, duration={}ms", responseEntity.getStatusCode(), System.currentTimeMillis() - httpStart);

            if (!responseEntity.getStatusCode().is2xxSuccessful() || responseEntity.getBody() == null) {
                return "查询高德地图失败，请稍后再试。";
            }

            JsonNode root = objectMapper.readTree(responseEntity.getBody());
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                String info = root.path("info").asText("未知错误");
                return "高德API调用失败：" + info + "（请稍后重试）";
            }

            JsonNode pois = root.path("pois");
            if (!pois.isArray() || pois.isEmpty()) {
                return "未找到相关地点，请尝试更换关键词或城市。";
            }

            int limit = Math.min(6, pois.size());
            int withPhotos = 0;

            StringBuilder sb = new StringBuilder();
            sb.append("为你在「").append(city).append("」找到以下适合约会的地点：\n\n");

            for (int i = 0; i < limit; i++) {
                JsonNode poi = pois.get(i);
                String name = poi.path("name").asText("未命名地点");
                String address = poi.path("address").asText("");
                String tel = poi.path("tel").asText("");
                String location = poi.path("location").asText("");
                String rating = extractRating(poi);
                String cost = extractCost(poi);
                List<String> photos = extractPhotos(poi, 3);
                if (!photos.isEmpty()) {
                    withPhotos++;
                }

                Map<String, Object> card = new LinkedHashMap<>();
                card.put("name", name);
                card.put("address", address);
                card.put("rating", rating);
                card.put("cost", cost);
                card.put("tel", tel);
                card.put("type", StringUtils.hasText(type) ? type.trim() : "");
                card.put("location", location);
                card.put("photos", photos);
                card.put("mapUrl", buildMapUrl(location, name));

                String json = objectMapper.writeValueAsString(card);
                sb.append("<!--LOCATION_CARD:").append(json).append("-->\n");
                sb.append("🏠 ").append(name)
                        .append(" | ⭐").append(StringUtils.hasText(rating) ? rating : "暂无")
                        .append(" | 💰").append(StringUtils.hasText(cost) ? (cost + "元") : "暂无")
                        .append(" | 📍").append(StringUtils.hasText(address) ? address : "暂无")
                        .append(" | 📞").append(StringUtils.hasText(tel) ? tel : "暂无")
                        .append(photos.isEmpty() ? " | 📸暂无实景图" : " | 📸已附实景图")
                        .append("\n\n");
            }

            sb.append("共找到 ").append(limit).append(" 个推荐地点，地点卡片会展示可访问的实景图片。\n");
            log.info("[DateLocationTool] searchDateLocations done, count={}, withPhotos={}, costMs={}",
                    limit, withPhotos, System.currentTimeMillis() - startMs);
            return sb.toString();
        } catch (RestClientException e) {
            log.error("[DateLocationTool] searchDateLocations http error, city={}, keywords={}, type={}", city, finalKeywords, type, e);
            return "查询高德地图失败（网络请求异常），请稍后再试。";
        } catch (Exception e) {
            log.error("[DateLocationTool] searchDateLocations error, city={}, keywords={}, type={}", city, finalKeywords, type, e);
            return "查询约会地点失败，请稍后再试。";
        }
    }

    private String getPoiTypeCode(String type) {
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "restaurant" -> "050000"; // 餐饮服务
            case "scenic" -> "110000";     // 风景名胜
            case "cafe" -> "050500";       // 咖啡厅
            case "cinema" -> "080300";     // 电影院
            case "mall" -> "060100";       // 商场
            case "park" -> "110101";       // 公园
            case "bar" -> "050400";        // 酒吧
            default -> "";
        };
    }

    private String extractRating(JsonNode poi) {
        String rating = poi.path("biz_ext").path("rating").asText("");
        if (!StringUtils.hasText(rating) || "[]".equals(rating)) {
            rating = poi.path("rating").asText("");
        }
        return normalizeEmpty(rating);
    }

    private String extractCost(JsonNode poi) {
        String cost = poi.path("biz_ext").path("cost").asText("");
        if (!StringUtils.hasText(cost) || "[]".equals(cost)) {
            cost = poi.path("cost").asText("");
        }
        return normalizeEmpty(cost);
    }

    private List<String> extractPhotos(JsonNode poi, int maxCount) {
        List<String> result = new ArrayList<>();
        JsonNode photos = poi.path("photos");
        if (!photos.isArray() || photos.isEmpty()) {
            return result;
        }
        for (int i = 0; i < photos.size() && result.size() < maxCount; i++) {
            JsonNode photo = photos.get(i);
            String url = photo.path("url").asText("");
            if (!StringUtils.hasText(url)) {
                continue;
            }
            // 通过后端代理绕过防盗链
            String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8);
            result.add("/api/proxy/image?url=" + encoded);
        }
        return result;
    }

    private String buildMapUrl(String location, String name) {
        if (!StringUtils.hasText(location) || !StringUtils.hasText(name)) {
            return "";
        }
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        return "https://uri.amap.com/marker?position=" + location + "&name=" + encodedName;
    }

    private String normalizeEmpty(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String v = value.trim();
        if (v.isEmpty() || "[]".equals(v) || "null".equalsIgnoreCase(v)) {
            return "";
        }
        return v;
    }
}
