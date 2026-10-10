package com.cxj.blog.auth;

import com.cxj.blog.controller.CommunityController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=CommunityController.class)
@Import({SecurityConfig.class,ApiErrors.class,SessionCookie.class})
class CommunitySecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean AuthService auth;
  @MockitoBean JdbcTemplate db;
  private Cookie identity(String role) {
    when(auth.authenticate("test-session")).thenReturn(new AuthService.User(1,"local@example.test","Test",role));
    return new Cookie(SessionCookie.NAME,"test-session");
  }
  @Test void administratorCookieCanReadBothManagementEndpoints() throws Exception {
    var cookie=identity("ADMIN");
    mvc.perform(get("/api/v1/admin/users?page=1").cookie(cookie)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/comments?page=1").cookie(cookie)).andExpect(status().isOk());
  }
  @Test void readerCannotAccessAdministrativeEndpointsOrResolveReports() throws Exception {
    var cookie=identity("USER");
    mvc.perform(get("/api/v1/admin/users").cookie(cookie)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/comments").cookie(cookie)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/admin/comments/1/reports/resolve").cookie(cookie).header("X-Blog-Request","1")).andExpect(status().isForbidden());
    verifyNoInteractions(db);
  }
  @Test void resolvingReportsDoesNotChangeCommentPublicationStatus() throws Exception {
    mvc.perform(post("/api/v1/admin/comments/1/reports/resolve").cookie(identity("ADMIN")).header("X-Blog-Request","1")).andExpect(status().isOk());
    verify(db).update("UPDATE comment_report SET resolved=true WHERE comment_id=?",1L);
    verifyNoMoreInteractions(db);
  }
}
