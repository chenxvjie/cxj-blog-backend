package com.cxj.blog.auth;

import com.cxj.blog.dto.PostRequest;
import com.cxj.blog.mapper.BlogPostMapper;
import com.cxj.blog.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostSlugTest {
  @ParameterizedTest
  @ValueSource(strings={"abc","abcdefghijklmnopqrstuvwxyz12345","中文链接","has space","has/slash"})
  void invalidNewSlugIsRejectedBeforeWriting(String slug) {
    var mapper=mock(BlogPostMapper.class);
    var db=mock(JdbcTemplate.class);
    var service=new PostService(mapper,db,new ObjectMapper());
    for(String role:new String[]{"USER","ADMIN"}) {
      var request=new PostRequest(1L,null,"Title",slug,null,"Body",null,"DRAFT",false);
      var error=assertThrows(ResponseStatusException.class,()->service.create(request,new AuthService.User(1,"test@example.test","Reader",role)));
      assertEquals(400,error.getStatusCode().value());
    }
    verifyNoInteractions(mapper,db);
  }
}
