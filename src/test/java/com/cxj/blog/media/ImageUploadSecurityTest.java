package com.cxj.blog.media;
import com.cxj.blog.auth.*;
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

@WebMvcTest(controllers=ImageUploadController.class,properties="app.cors-origins=https://chenxujie-bolg.cn")
@Import({SecurityConfig.class,ApiErrors.class})
class ImageUploadSecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean AuthService auth;
  @MockitoBean ImageUploadService uploads;
  private static final String BODY="{\"filename\":\"a.png\",\"contentType\":\"image/png\",\"size\":3,\"contentMd5\":\"kAFQmDzST7DWlj99KOF/cg==\"}";
  @Test void anonymousCannotSignOrComplete() throws Exception {
    for(String path:new String[]{"upload-url","complete"})mvc.perform(post("/api/v1/manage/images/"+path).contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
    verifyNoInteractions(uploads);
  }
  @Test void userCanSignUsingSessionIdentity() throws Exception {
    var user=new AuthService.User(8,"a@example.test","user","USER");when(auth.authenticate("token")).thenReturn(user);
    mvc.perform(post("/api/v1/manage/images/upload-url").header("Authorization","Bearer token").header("Origin","https://chenxujie-bolg.cn")
      .contentType("application/json").content(BODY)).andExpect(status().isOk());
    verify(uploads).prepare(any(),eq(user));
  }
  @Test void unexpectedRoleDenied() throws Exception {
    when(auth.authenticate("token")).thenReturn(new AuthService.User(8,"a@example.test","reader","READER"));
    mvc.perform(post("/api/v1/manage/images/upload-url").header("Authorization","Bearer token").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
    verifyNoInteractions(uploads);
  }
  @Test void blankCompletionRejected() throws Exception {
    when(auth.authenticate("token")).thenReturn(new AuthService.User(8,"a@example.test","user","USER"));
    mvc.perform(post("/api/v1/manage/images/complete").header("Authorization","Bearer token").contentType("application/json").content("{\"uploadId\":\"\"}")).andExpect(status().isBadRequest());
    verifyNoInteractions(uploads);
  }
}
