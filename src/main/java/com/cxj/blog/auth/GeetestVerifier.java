package com.cxj.blog.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeetestVerifier {
  private static final ObjectMapper JSON = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeetestVerifier.class);
  public record Proof(
      @JsonProperty("lot_number") @NotBlank @Size(max=128) String lotNumber,
      @JsonProperty("captcha_output") @NotBlank @Size(max=8192) String captchaOutput,
      @JsonProperty("pass_token") @NotBlank @Size(max=2048) String passToken,
      @JsonProperty("gen_time") @NotBlank @Size(max=20) String genTime) {}
  public record Config(boolean enabled, String captchaId) {}
  private final boolean enabled;
  private final String id;
  private final String key;
  private final RestClient http;
  private final JdbcTemplate db;

  @org.springframework.beans.factory.annotation.Autowired
  public GeetestVerifier(@Value("${app.integrations.geetest-enabled:false}") boolean enabled,
      @Value("${app.geetest.captcha-id:}") String id,
      @Value("${app.geetest.captcha-key:}") String key, RestClient.Builder builder, JdbcTemplate db) {
    this.enabled=enabled; this.id=id; this.key=key; this.db=db;
    if (enabled && (id.isBlank() || key.isBlank())) throw new IllegalStateException("GEETEST_CAPTCHA_ID and GEETEST_CAPTCHA_KEY are required");
    var factory=new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
    this.http=builder.requestFactory(factory).baseUrl("https://gcaptcha4.geetest.com").build();
  }
  public Config config() { return new Config(enabled, enabled ? id : null); }
  GeetestVerifier(String id, String key, RestClient http, JdbcTemplate db) {
    this.enabled=true; this.id=id; this.key=key; this.http=http; this.db=db;
  }
  static String sign(String lot, String key) {
    try {
      Mac mac=Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(lot.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Captcha signing failed",e); }
  }
  public void verify(Proof proof) {
    // Sending email never silently bypasses verification when configuration is absent.
    if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"人机验证服务未启用");
    if (proof==null || proof.lotNumber()==null || proof.captchaOutput()==null || proof.passToken()==null || proof.genTime()==null)
      throw rejected();
    long time;
    try { time=Long.parseLong(proof.genTime()); } catch (NumberFormatException e) { throw rejected(); }
    long now=Instant.now().getEpochSecond();
    if (time < now-600 || time > now+60) throw rejected();
    var body=new LinkedMultiValueMap<String,String>();
    body.add("lot_number",proof.lotNumber()); body.add("captcha_output",proof.captchaOutput());
    body.add("pass_token",proof.passToken()); body.add("gen_time",proof.genTime());
    body.add("sign_token",sign(proof.lotNumber(),key));
    JsonNode response;
    try {
      // GeeTest returns JSON with Content-Type text/javascript. Read text first,
      // then parse JSON strictly; never execute JavaScript or accept JSONP.
      String raw=http.post().uri(uri->uri.path("/validate").queryParam("captcha_id",id).build())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(String.class);
      response=raw==null?null:JSON.readTree(raw);
    } catch (RestClientException | JsonProcessingException e) {
      // Log only exception types/status, never the URL, proof, response or secret.
      int status=e instanceof org.springframework.web.client.RestClientResponseException httpError ? httpError.getStatusCode().value() : 0;
      log.warn("Geetest verification failed; type={}, causeType={}, httpStatus={}", e.getClass().getSimpleName(),
          e.getCause()==null?"none":e.getCause().getClass().getSimpleName(),status);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"人机验证服务暂不可用，请重试");
    }
    if (response==null || !"success".equals(response.path("result").asText())) throw rejected();
    // Atomic persistent claim prevents simultaneous/replayed sends across replicas.
    // It is committed before the email transaction; a failed send requires a fresh captcha.
    db.update("DELETE FROM auth_captcha_used WHERE consumed_at < now()-interval '1 day'");
    int claimed=db.update("INSERT INTO auth_captcha_used(lot_hash) VALUES (?) ON CONFLICT DO NOTHING",AuthService.hash(id+":"+proof.lotNumber()));
    if (claimed!=1) throw rejected();
  }
  private ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"人机验证无效、过期或已使用，请重新验证"); }
}
