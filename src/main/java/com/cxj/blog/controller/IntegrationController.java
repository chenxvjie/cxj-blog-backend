package com.cxj.blog.controller;

import com.cxj.blog.dto.ApiResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports optional services without calling third-party APIs. */
@RestController
@RequestMapping("/api/v1/system")
public class IntegrationController {
  @Value("${app.integrations.geetest-enabled:false}") boolean geetest;
  @Value("${app.integrations.cos-enabled:false}") boolean cos;
  @Value("${app.integrations.baidu-analytics-enabled:false}") boolean baidu;
  @GetMapping("/integrations") public ApiResponse<Map<String, String>> status() {
    return ApiResponse.ok(Map.of("geetest", geetest ? "configured" : "placeholder", "cos", cos ? "configured" : "placeholder", "baiduAnalytics", baidu ? "configured" : "placeholder", "feishu", "placeholder"));
  }
}
