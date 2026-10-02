package com.cxj.blog.auth;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AccountRulesTest {
  JdbcTemplate db=mock(JdbcTemplate.class);
  AuthService auth=new AuthService(db,mock(SesCodeSender.class),"","",false);
  @Test void registrationRequiresPassword() {assertThrows(ResponseStatusException.class,()->auth.register("a@example.com","123456","a",null));verifyNoInteractions(db);}
  @Test void adminCannotRegister() {assertEquals(409,assertThrows(ResponseStatusException.class,()->auth.register(AuthService.ADMIN_EMAIL,"123456","a","test-password")).getStatusCode().value());verifyNoInteractions(db);}
  @Test void passwordLoginWorksWithoutCloud() {
    when(db.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(0);
    when(db.queryForList(anyString(),eq("a@example.com"))).thenReturn(List.of(Map.of("id",1L,"email","a@example.com","nickname","a","role","USER","password_hash",AuthService.PASSWORDS.encode("test-password"))));
    var login=auth.passwordLogin("a@example.com","test-password","127.0.0.1");
    assertEquals("USER",login.user().role());assertEquals(43,login.accessToken().length());
  }
  @Test void wrongPasswordRejected() {
    when(db.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(0);
    assertEquals(401,assertThrows(ResponseStatusException.class,()->auth.passwordLogin("a@example.com","wrong-password","127.0.0.1")).getStatusCode().value());
  }
  @Test void bootstrapDoesNotResetPassword() {
    when(db.queryForList(anyString(),eq(AuthService.ADMIN_EMAIL))).thenReturn(List.of(Map.of("role","ADMIN","password_hash","existing")));
    new AdminBootstrap(db,"different-password").run(null);
    verify(db,never()).update(anyString(),any(Object[].class));
  }
  @Test void bootstrapWillNotPromoteExistingUser() {
    when(db.queryForList(anyString(),eq(AuthService.ADMIN_EMAIL))).thenReturn(List.of(Map.of("role","USER")));
    assertThrows(IllegalStateException.class,()->new AdminBootstrap(db,"test-password").run(null));
  }
}
