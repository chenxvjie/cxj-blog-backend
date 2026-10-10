package com.cxj.blog.auth;
import com.cxj.blog.entity.BlogPost;
import com.cxj.blog.mapper.BlogPostMapper;
import com.cxj.blog.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PostOwnershipTest {
  BlogPostMapper mapper=mock(BlogPostMapper.class);
  PostService service=new PostService(mapper,mock(org.springframework.jdbc.core.JdbcTemplate.class),new com.fasterxml.jackson.databind.ObjectMapper());
  BlogPost post() {var p=new BlogPost();p.setId(5L);p.setAuthorId(1L);when(mapper.selectById(5L)).thenReturn(p);return p;}
  @Test void ownerCanEdit() {var p=post();assertSame(p,service.editable(5,new AuthService.User(1,"a","a","USER")));}
  @Test void adminCanEditOthers() {var p=post();assertSame(p,service.editable(5,new AuthService.User(2,"b","b","ADMIN")));}
  @Test void otherUserCannotEditOrDelete() {post();var user=new AuthService.User(2,"b","b","USER");assertEquals(403,assertThrows(ResponseStatusException.class,()->service.editable(5,user)).getStatusCode().value());assertEquals(403,assertThrows(ResponseStatusException.class,()->service.delete(5,user)).getStatusCode().value());}
  @Test void missingPostIs404() {assertEquals(404,assertThrows(ResponseStatusException.class,()->service.editable(5,new AuthService.User(1,"a","a","ADMIN"))).getStatusCode().value());}
}
