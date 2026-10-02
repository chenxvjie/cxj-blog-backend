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
  @Test void registrationWithoutPasswordRejected() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"email\":\"a@example.com\",\"code\":\"123456\",\"nickname\":\"reader\"}")).andExpect(status().isBadRequest());
    verify(auth,never()).register(any(),any(),any(),any());
  }
  @Test void userCanCreateThroughSharedEndpointWithSessionAuthor() throws Exception {
    when(auth.authenticate("user")).thenReturn(new AuthService.User(8,"a@example.com","user","USER"));
    mvc.perform(post("/api/v1/manage/posts").header("Authorization","Bearer user").contentType("application/json").content(BODY)).andExpect(status().isOk());
    verify(posts).create(argThat((PostRequest p)->p.authorId()==8));
  }
  @Test void anonymousCannotReadManagement() throws Exception {
    mvc.perform(get("/api/v1/manage/posts")).andExpect(status().isUnauthorized());
    verifyNoInteractions(posts);
  }
  @Autowired MockMvc mvc;
  @MockitoBean AuthService auth;
  @MockitoBean GeetestVerifier geetest;
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
  private static final String SEND="{\"email\":\"a@example.com\",\"captcha\":{\"lot_number\":\"lot\",\"captcha_output\":\"output\",\"pass_token\":\"pass\",\"gen_time\":\"1234567890\"}}";
  @Test void missingCaptchaCannotSend() throws Exception {
    mvc.perform(post("/api/v1/auth/email-code").contentType("application/json").content("{\"email\":\"a@example.com\"}"))
        .andExpect(status().isBadRequest());
    verify(auth,never()).send(anyString(),anyString());
  }
  @Test void rejectedCaptchaCannotSend() throws Exception {
    doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST))
        .when(geetest).verify(any());
    mvc.perform(post("/api/v1/auth/email-code").contentType("application/json").content(SEND)).andExpect(status().isBadRequest());
    verify(auth,never()).send(anyString(),anyString());
  }
  @Test void successfulCaptchaIsCheckedBeforeSending() throws Exception {
    mvc.perform(post("/api/v1/auth/email-code").contentType("application/json").content(SEND)).andExpect(status().isOk());
    var ordered=inOrder(geetest,auth);
    ordered.verify(geetest).verify(any()); ordered.verify(auth).send(eq("a@example.com"),anyString());
  }
  @Test void publicConfigContainsOnlyPublicId() throws Exception {
    when(geetest.config()).thenReturn(new GeetestVerifier.Config(true,"public-id"));
    mvc.perform(get("/api/v1/auth/captcha-config")).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.captchaId").value("public-id")).andExpect(jsonPath("$.data.captchaKey").doesNotExist());
  }
}
