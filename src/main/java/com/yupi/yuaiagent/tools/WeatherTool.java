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

/**
 * 天气查询工具（高德天气 API）
 */
@Slf4j
@Component
public class WeatherTool {

    private static final String AMAP_GEO_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final String AMAP_WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";

    @Value("${amap.api-key}")
    private String amapApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public WeatherTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Tool(description = "查询指定城市的天气预报，帮助规划约会活动，提供未来3天天气信息")
    public String queryWeather(
            @ToolParam(description = "城市名称，如北京、上海、杭州") String city
    ) {
        if (!StringUtils.hasText(city)) {
            return "请提供城市名称（如：北京、上海、杭州）。";
        }

        try {
            // 1. 地理编码：城市名 -> adcode
            String adcode = resolveAdcode(city);
            if (adcode == null) {
                return "无法识别城市「" + city + "」，请检查城市名称是否正确。";
            }

            // 2. 查询天气预报
            URI weatherUri = UriComponentsBuilder.fromHttpUrl(AMAP_WEATHER_URL)
                    .queryParam("key", amapApiKey)
                    .queryParam("city", adcode)
                    .queryParam("extensions", "all")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            log.info("[Call-AmapWeather] city={}, adcode={}", city, adcode);
            long start = System.currentTimeMillis();
            ResponseEntity<String> weatherResp = restTemplate.getForEntity(weatherUri, String.class);
            long cost = System.currentTimeMillis() - start;
            log.info("[Call-AmapWeather] Response status={}, duration={}ms", weatherResp.getStatusCode(), cost);
            if (!weatherResp.getStatusCode().is2xxSuccessful() || weatherResp.getBody() == null) {
                return "查询天气失败，请稍后再试。";
            }

            JsonNode weatherRoot = objectMapper.readTree(weatherResp.getBody());
            if (!"1".equals(weatherRoot.path("status").asText())) {
                return "查询天气失败：" + weatherRoot.path("info").asText("未知错误") + "。";
            }

            JsonNode forecasts = weatherRoot.path("forecasts");
            if (!forecasts.isArray() || forecasts.isEmpty()) {
                return "暂无「" + city + "」的天气预报数据。";
            }

            JsonNode casts = forecasts.get(0).path("casts");
            if (!casts.isArray() || casts.isEmpty()) {
                return "暂无「" + city + "」的天气预报数据。";
            }

            // 3. 格式化未来 3 天天气
            String reportCity = forecasts.get(0).path("city").asText(city);
            StringBuilder sb = new StringBuilder();
            sb.append("「").append(reportCity).append("」未来天气预报：\n");

            int days = Math.min(3, casts.size());
            for (int i = 0; i < days; i++) {
                JsonNode day = casts.get(i);
                String date = day.path("date").asText("");
                String dayWeather = day.path("dayweather").asText("未知");
                String nightWeather = day.path("nightweather").asText("未知");
                String dayTemp = day.path("daytemp").asText("?");
                String nightTemp = day.path("nighttemp").asText("?");
                String dayWind = day.path("daywind").asText("");
                String dayPower = day.path("daypower").asText("");

                sb.append("\n").append(date).append("：\n");
                sb.append("  白天：").append(dayWeather).append("，").append(dayTemp).append("°C");
                if (StringUtils.hasText(dayWind)) {
                    sb.append("，").append(dayWind).append("风").append(dayPower).append("级");
                }
                sb.append("\n");
                sb.append("  夜间：").append(nightWeather).append("，").append(nightTemp).append("°C\n");
            }

            // 4. 约会建议
            sb.append("\n💡 约会建议：");
            String firstDayWeather = casts.get(0).path("dayweather").asText("");
            if (firstDayWeather.contains("雨")) {
                sb.append("今天有雨，建议选择室内活动，如看电影、逛商场、去咖啡厅等。记得带伞哦！");
            } else if (firstDayWeather.contains("雪")) {
                sb.append("今天有雪，可以一起赏雪、堆雪人，也可以选择温暖的室内约会。注意保暖！");
            } else if (firstDayWeather.contains("晴")) {
                sb.append("今天天气晴好，非常适合户外约会！可以去公园散步、骑行或野餐。");
            } else if (firstDayWeather.contains("多云") || firstDayWeather.contains("阴")) {
                sb.append("今天多云/阴天，气温适宜，适合户外逛街或公园漫步，不用担心暴晒。");
            } else {
                sb.append("出门前关注实时天气变化，灵活调整约会计划。");
            }

            return sb.toString();
        } catch (RestClientException e) {
            log.error("[Call-AmapWeather] Exception, city={}", city, e);
            return "查询天气失败（网络请求异常），请稍后再试。";
        } catch (Exception e) {
            log.error("[Call-AmapWeather] ParseException, city={}", city, e);
            return "解析天气数据失败，请稍后再试。";
        }
    }

    /**
     * 通过高德地理编码 API 将城市名转为 adcode
     */
    private String resolveAdcode(String city) throws Exception {
        URI geoUri = UriComponentsBuilder.fromHttpUrl(AMAP_GEO_URL)
                .queryParam("key", amapApiKey)
                .queryParam("address", city)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        log.info("[Call-AmapGeo] city={}", city);
        long start = System.currentTimeMillis();
        ResponseEntity<String> geoResp = restTemplate.getForEntity(geoUri, String.class);
        long cost = System.currentTimeMillis() - start;
        log.info("[Call-AmapGeo] Response status={}, duration={}ms", geoResp.getStatusCode(), cost);
        if (!geoResp.getStatusCode().is2xxSuccessful() || geoResp.getBody() == null) {
            return null;
        }

        JsonNode geoRoot = objectMapper.readTree(geoResp.getBody());
        if (!"1".equals(geoRoot.path("status").asText())) {
            return null;
        }

        JsonNode geocodes = geoRoot.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            return null;
        }

        return geocodes.get(0).path("adcode").asText(null);
    }
}
