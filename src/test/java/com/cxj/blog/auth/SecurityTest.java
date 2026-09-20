package com.cxj.blog.auth;

import com.cxj.blog.controller.PostController;
import com.cxj.blog.dto.PostRequest;
import com.cxj.blog.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={AuthController.class,PostController.class},properties="app.cors-origins=https://chenxujie-bolg.cn")
@Import({SecurityConfig.class,ApiErrors.class})
class SecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean AuthService auth;
  @MockitoBean PostService posts;
  private static final String BODY="{\"authorId\":999,\"title\":\"test\",\"slug\":\"test\",\"contentMd\":\"hello\"}";
  @Test void anonymousCannotWrite() throws Exception {
    mvc.perform(post("/api/v1/admin/posts").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
    verifyNoInteractions(posts);
  }
  @Test void readerCannotWrite() throws Exception {
    when(auth.authenticate("reader")).thenReturn(new AuthService.User(1,"a@example.com","reader","READER"));
    mvc.perform(post("/api/v1/admin/posts").header("Authorization","Bearer reader").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
    verifyNoInteractions(posts);
  }
  @Test void adminAuthorComesFromSession() throws Exception {
    when(auth.authenticate("admin")).thenReturn(new AuthService.User(7,"a@example.com","admin","ADMIN"));
    mvc.perform(post("/api/v1/admin/posts").header("Authorization","Bearer admin").contentType("application/json").content(BODY)).andExpect(status().isOk());
    verify(posts).create(argThat((PostRequest p)->p.authorId()==7));
  }
  @Test void meRequiresValidToken() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer expired")).andExpect(status().isUnauthorized());
  }
  @Test void publicReadStillWorks() throws Exception {
    mvc.perform(get("/api/v1/posts")).andExpect(status().isOk());
  }
  @Test void invalidEmailRejected() throws Exception {
    mvc.perform(post("/api/v1/auth/email-code").contentType("application/json").content("{\"email\":\"bad\"}")).andExpect(status().isBadRequest());
    verify(auth,never()).send(anyString(),anyString());
  }
  @Test void logoutRevokesToken() throws Exception {
    when(auth.authenticate("token")).thenReturn(new AuthService.User(7,"a@example.com","reader","READER"));
    mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer token")).andExpect(status().isOk());
    verify(auth).logout("token");
  }
}
