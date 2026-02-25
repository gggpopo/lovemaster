package com.yupi.yuaiagent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DateLocationToolTest {

    @Test
    void searchDateLocations_shouldIncludeMarkdownImageLinks_whenAmapReturnsPhotos() {
        DateLocationTool tool = new DateLocationTool(new ObjectMapper());
        ReflectionTestUtils.setField(tool, "amapApiKey", "test-key");

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(tool, "restTemplate");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        String amapResponse = """
                {
                  "status": "1",
                  "count": "1",
                  "pois": [
                    {
                      "name": "五谷丰登生态园(复兴店)",
                      "address": "前进大街259号",
                      "tel": "0310-2518888",
                      "location": "114.4901,36.6123",
                      "biz_ext": { "rating": "4.7", "cost": "63.00" },
                      "photos": [
                        {
                          "title": [],
                          "url": "https://store.is.autonavi.com/showpic/b3cb443d0184cf5297359638b3cbd536?operate=original"
                        }
                      ]
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/v3/place/text")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(amapResponse, MediaType.APPLICATION_JSON));

        String result = tool.searchDateLocations("特色美食", "邯郸", "restaurant");

        mockServer.verify();
        assertTrue(result.contains("<!--LOCATION_CARD:"));
        assertTrue(result.contains("![实景图1](/api/proxy/image?url="));
    }
}
