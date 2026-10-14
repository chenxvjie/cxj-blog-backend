package com.cxj.blog.controller;

import com.cxj.blog.auth.AuthService.User;
import com.cxj.blog.dto.ApiResponse;
import com.cxj.blog.service.AiQuotaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
  private final AiQuotaService quota;
  private final com.cxj.blog.service.AiStreamService streams;
  private final boolean enabled;
  private final String token;
  private final RestClient client;
  private final List<IpAddressMatcher> trustedProxies;
  public AiController(AiQuotaService quota,com.cxj.blog.service.AiStreamService streams,RestClient.Builder builder,
      @Value("${app.ai.enabled:false}") boolean enabled,@Value("${app.ai.base-url:http://127.0.0.1:8000}") String base,
      @Value("${app.ai.internal-token:}") String token,@Value("${app.ai.trusted-proxies:}") String proxies) {
    this.streams=streams;this.quota=quota;this.enabled=enabled;this.token=token;
    var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(90));
    this.client=builder.baseUrl(base).requestFactory(factory).build();
    this.trustedProxies=Arrays.stream(proxies.split(",")).map(String::strip).filter(s->!s.isEmpty()).map(IpAddressMatcher::new).toList();
  }
  private void enabled(){if(!enabled || token.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI助手暂未启用");}
  private String address(HttpServletRequest request) {
    String remote=request.getRemoteAddr();
    if(trustedProxies.stream().anyMatch(proxy->proxy.matches(remote))) {
      String value=request.getHeader("X-Real-IP");
      if(value!=null && value.matches("[0-9a-fA-F:.]{3,45}"))return value;
    }
    return remote;
  }
  public record Turn(@NotBlank @Size(max=500) String question,@NotBlank @Size(max=8000) String answer) {}
  public record Question(@NotBlank @Size(max=500) String question,@Positive Long postId,@Size(max=3) List<@Valid Turn> history) {}
  @GetMapping("/quota") public ApiResponse<AiQuotaService.Quota> quota(@AuthenticationPrincipal User user,HttpServletRequest request){enabled();return ApiResponse.ok(quota.quota(user,address(request)));}
  @PostMapping("/ask") public ApiResponse<?> ask(@Valid @RequestBody Question body,@AuthenticationPrincipal User user,HttpServletRequest request) {
    enabled();
    if(!"1".equals(request.getHeader("X-Blog-Request")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请求校验失败，请刷新页面");
    var reservation=quota.reserve(user,address(request));
    Map<?,?> answer;
    try {
      var payload=new HashMap<String,Object>();payload.put("question",body.question().strip());if(body.postId()!=null)payload.put("post_id",body.postId());
      if(body.history()!=null)payload.put("history",body.history());
      answer=client.post().uri("/internal/ask").header("Authorization","Bearer "+token).body(payload).retrieve().body(Map.class);
      if(answer==null || !(answer.get("answer") instanceof String) || !(answer.get("sources") instanceof List))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI回答格式异常");
      quota.succeed(reservation);
    } catch(RuntimeException e) {
      quota.refund(reservation);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"AI服务暂不可用，本次未扣次数，请稍后重试");
    }
    return ApiResponse.ok(Map.of("answer",answer,"quota",quota.quota(user,address(request))));
  }
  @PostMapping(value="/ask/stream",produces="text/event-stream")
  public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(@Valid @RequestBody Question body,
      @AuthenticationPrincipal User user,HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) {
    enabled();
    if(!"1".equals(request.getHeader("X-Blog-Request")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"请求校验失败，请刷新页面");
    response.setHeader("Cache-Control","no-cache, no-transform");
    response.setHeader("X-Accel-Buffering","no");
    var payload=new HashMap<String,Object>();
    payload.put("question",body.question().strip());
    if(body.postId()!=null)payload.put("post_id",body.postId());
    if(body.history()!=null)payload.put("history",body.history());
    return streams.open(client,token,payload,user,address(request));
  }

}
