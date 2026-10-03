package com.cxj.blog.auth;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeetestVerifierTest {
  JdbcTemplate db=mock(JdbcTemplate.class);
  RestClient.Builder builder=RestClient.builder().baseUrl("https://gcaptcha4.geetest.com");
  MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
  GeetestVerifier verifier=new GeetestVerifier("public-id","private-key",builder.build(),db);
  GeetestVerifier.Proof proof=new GeetestVerifier.Proof("lot","output","pass",Long.toString(Instant.now().getEpochSecond()));
  void respond(String json) {
    server.expect(requestTo("https://gcaptcha4.geetest.com/validate?captcha_id=public-id"))
      .andExpect(method(org.springframework.http.HttpMethod.POST))
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
      .andExpect(content().string(org.hamcrest.Matchers.containsString("sign_token="+GeetestVerifier.sign("lot","private-key"))))
      .andRespond(withSuccess(json,MediaType.APPLICATION_JSON));
  }
  @Test void successClaimsProof() {
    respond("{\"result\":\"success\"}");
    when(db.update(startsWith("INSERT"),anyString())).thenReturn(1);
    verifier.verify(proof); server.verify();
    verify(db).update(startsWith("INSERT"),eq(AuthService.hash("public-id:lot")));
  }
  @Test void acceptsJsonWithGeetestJavascriptContentType() {
    server.expect(anything()).andRespond(withSuccess("{\"result\":\"success\"}", MediaType.parseMediaType("text/javascript;charset=UTF-8")));
    when(db.update(startsWith("INSERT"),anyString())).thenReturn(1);
    verifier.verify(proof);
    server.verify();
    verify(db).update(startsWith("INSERT"),eq(AuthService.hash("public-id:lot")));
  }
  @Test void malformedJavascriptResponseFailsClosed() {
    server.expect(anything()).andRespond(withSuccess("callback({\"result\":\"success\"})", MediaType.parseMediaType("text/javascript")));
    assertEquals(503,assertThrows(ResponseStatusException.class,()->verifier.verify(proof)).getStatusCode().value());
    verifyNoInteractions(db);
  }
  @Test void javascriptContentTypeDoesNotAllowFailedProof() {
    server.expect(anything()).andRespond(withSuccess("{\"result\":\"fail\"}", MediaType.parseMediaType("text/javascript")));
    assertEquals(400,assertThrows(ResponseStatusException.class,()->verifier.verify(proof)).getStatusCode().value());
    verifyNoInteractions(db);
  }
  @Test void trailingNonJsonContentFailsClosed() {
    server.expect(anything()).andRespond(withSuccess("{\"result\":\"success\"} callback()", MediaType.parseMediaType("text/javascript")));
    assertEquals(503,assertThrows(ResponseStatusException.class,()->verifier.verify(proof)).getStatusCode().value());
    verifyNoInteractions(db);
  }
  @Test void failedProofDoesNotTouchDatabase() {
    respond("{\"result\":\"fail\"}");
    assertThrows(ResponseStatusException.class,()->verifier.verify(proof)); verifyNoInteractions(db);
  }
  @Test void replayRejected() {
    respond("{\"result\":\"success\"}");
    assertThrows(ResponseStatusException.class,()->verifier.verify(proof));
  }
  @Test void providerFailureFailsClosed() {
    server.expect(anything()).andRespond(withServerError());
    assertEquals(503,assertThrows(ResponseStatusException.class,()->verifier.verify(proof)).getStatusCode().value());
    verifyNoInteractions(db);
  }
  @Test void malformedResponseFailsClosed() {
    respond("not-json");
    assertEquals(503,assertThrows(ResponseStatusException.class,()->verifier.verify(proof)).getStatusCode().value());
    verifyNoInteractions(db);
  }
  @Test void missingProofRejected() { assertThrows(ResponseStatusException.class,()->verifier.verify(null)); verifyNoInteractions(db); }
  @Test void expiredProofRejected() {
    assertThrows(ResponseStatusException.class,()->verifier.verify(new GeetestVerifier.Proof("lot","output","pass","1")));
    verifyNoInteractions(db);
  }
  @Test void disabledDoesNotBypassVerification() {
    var disabled=new GeetestVerifier(false,"","",RestClient.builder(),db);
    assertEquals(503,assertThrows(ResponseStatusException.class,()->disabled.verify(proof)).getStatusCode().value());
  }
  @Test void missingSecretRejectsEnabledConfig() {
    assertThrows(IllegalStateException.class,()->new GeetestVerifier(true,"id","",RestClient.builder(),db));
  }
  @Test void signatureMatchesKnownHmacVector() {
    assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",GeetestVerifier.sign("The quick brown fox jumps over the lazy dog","key"));
  }
}
