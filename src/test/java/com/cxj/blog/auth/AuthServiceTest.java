package com.cxj.blog.auth;

import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthServiceTest {
  JdbcTemplate db=mock(JdbcTemplate.class);
  SesCodeSender mail=mock(SesCodeSender.class);
  AuthService service=new AuthService(db,mail,"test-secret-with-at-least-32-characters","blog@example.com",true);
  @Test void emailNormalization() {assertEquals("a@example.com",AuthService.normalize(" A@EXAMPLE.COM "));}
  @Test void sendsGeneratedSixDigitCodeThroughSes() {
    when(db.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(0);
    service.send(" A@EXAMPLE.COM ","127.0.0.1");
    verify(mail).send(eq("a@example.com"),matches("[0-9]{6}"));
  }
  @Test void sendFailurePropagatesForTransactionRollback() {
    when(db.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(0);
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"邮件发送失败"))
        .when(mail).send(anyString(),anyString());
    assertEquals(503,assertThrows(ResponseStatusException.class,
        ()->service.send("a@example.com","127.0.0.1")).getStatusCode().value());
  }
  @Test void disabledEmailFailsClosed() {
    var disabled=new AuthService(db,mail,"","",false);
    assertEquals(503,assertThrows(ResponseStatusException.class,()->disabled.send("a@example.com","127.0.0.1")).getStatusCode().value());
    verifyNoInteractions(db);
  }
  @Test void unsafeEnabledConfigurationRejected() {
    assertThrows(IllegalStateException.class,()->new AuthService(db,mail,"short","",true));
  }
  @Test void malformedTokenNeverQueriesDatabase() {
    assertNull(service.authenticate("not-a-token")); verifyNoInteractions(db);
  }
  @Test void logoutStoresOnlyDigest() {
    service.logout("secret-token");
    verify(db).update("DELETE FROM auth_session WHERE token_hash=?",AuthService.hash("secret-token"));
  }
  @Test void missingCodeCannotLogin() {
    when(db.queryForList(anyString(),eq("a@example.com"))).thenReturn(List.of());
    assertEquals(401,assertThrows(ResponseStatusException.class,()->service.login("a@example.com","123456",null)).getStatusCode().value());
  }
  @Test void wrongCodeCountsAttempt() {
    when(db.queryForList(anyString(),eq("a@example.com"))).thenReturn(List.of(Map.of("code_hash","invalid","attempts",0)));
    when(db.queryForObject(anyString(),eq(OffsetDateTime.class),eq("a@example.com"))).thenReturn(OffsetDateTime.now().plusMinutes(5));
    assertThrows(ResponseStatusException.class,()->service.login("a@example.com","123456",null));
    verify(db).update("UPDATE auth_code SET attempts=attempts+1 WHERE email=?","a@example.com");
    verify(db,never()).update("DELETE FROM auth_code WHERE email=?","a@example.com");
  }
  @Test void expiredCodeCannotLogin() {
    when(db.queryForList(anyString(),eq("a@example.com"))).thenReturn(List.of(Map.of("code_hash","invalid","attempts",0)));
    when(db.queryForObject(anyString(),eq(OffsetDateTime.class),eq("a@example.com"))).thenReturn(OffsetDateTime.now().minusMinutes(1));
    assertThrows(ResponseStatusException.class,()->service.login("a@example.com","123456",null));
    verify(db,never()).update("UPDATE auth_code SET attempts=attempts+1 WHERE email=?","a@example.com");
  }
  @Test void exhaustedCodeCannotLogin() {
    when(db.queryForList(anyString(),eq("a@example.com"))).thenReturn(List.of(Map.of("code_hash","invalid","attempts",5)));
    when(db.queryForObject(anyString(),eq(OffsetDateTime.class),eq("a@example.com"))).thenReturn(OffsetDateTime.now().plusMinutes(1));
    assertThrows(ResponseStatusException.class,()->service.login("a@example.com","123456",null));
    verify(db,never()).update("UPDATE auth_code SET attempts=attempts+1 WHERE email=?","a@example.com");
  }
}
