package com.yupi.yuaiagent.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;

/**
 * 图片代理接口：解决高德图片防盗链问题。
 * <p>
 * 注意：本项目 server.servlet.context-path=/api，因此此 Controller 映射为 /proxy/**，最终路径为 /api/proxy/**。
 */
@Slf4j
@RestController
@RequestMapping("/proxy")
public class ImageProxyController {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            URI uri = URI.create(imageUrl);
            String host = uri.getHost();
            if (!isAllowedHost(host)) {
                return ResponseEntity.badRequest().build();
            }

            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
            requestHeaders.set("Referer", "https://www.amap.com/");
            HttpEntity<Void> entity = new HttpEntity<>(requestHeaders);

            ResponseEntity<byte[]> response = restTemplate.exchange(imageUrl, HttpMethod.GET, entity, byte[].class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            HttpHeaders responseHeaders = new HttpHeaders();

            MediaType upstreamType = response.getHeaders().getContentType();
            responseHeaders.setContentType(upstreamType != null ? upstreamType : MediaType.IMAGE_JPEG);
            responseHeaders.setCacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic());

            return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
        } catch (Exception e) {
            log.error("[ImageProxy] proxyImage failed, url={}", imageUrl, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isAllowedHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        return host.equalsIgnoreCase("store.is.autonavi.com")
                || host.equalsIgnoreCase("aos-comment.amap.com");
    }
}

