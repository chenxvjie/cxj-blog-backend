package com.cxj.blog.auth;

import com.cxj.blog.controller.CommunityController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityPaginationTest {
  @Test void userPaginationReturnsTotalAndUsesSelectedSize() throws Exception {
    var db=mock(JdbcTemplate.class);
    when(db.queryForList(anyString(),eq(10),eq(20))).thenReturn(List.of(Map.of("id",21)));
    when(db.queryForObject(anyString(),eq(Long.class))).thenReturn(67L);
    var mvc=MockMvcBuilders.standaloneSetup(new CommunityController(db,mock(AuthService.class))).build();
    mvc.perform(get("/api/v1/admin/users").param("page","3").param("size","10").param("paginated","true"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(67)).andExpect(jsonPath("$.data.records[0].id").value(21));
    verify(db).queryForList(contains("LIMIT ? OFFSET ?"),eq(10),eq(20));
  }
  @Test void legacyArrayResponseAndSizeLimitArePreserved() throws Exception {
    var db=mock(JdbcTemplate.class);
    when(db.queryForList(anyString(),eq(50),eq(0))).thenReturn(List.of(Map.of("id",1)));
    var mvc=MockMvcBuilders.standaloneSetup(new CommunityController(db,mock(AuthService.class))).build();
    mvc.perform(get("/api/v1/admin/comments").param("page","0").param("size","1000"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(1));
    verify(db).queryForList(contains("LIMIT ? OFFSET ?"),eq(50),eq(0));
  }
  @Test void moderationSearchFiltersBothQueriesBeforePaginationAndPrioritizesReports() throws Exception {
    var db=mock(JdbcTemplate.class);
    when(db.queryForList(anyString(),eq("Alice%"),eq("Alice%"),eq(10),eq(10)))
      .thenReturn(List.of(Map.of("id",7)));
    when(db.queryForObject(anyString(),eq(Long.class),eq("Alice%"),eq("Alice%"))).thenReturn(12L);
    var mvc=MockMvcBuilders.standaloneSetup(new CommunityController(db,mock(AuthService.class))).build();
    mvc.perform(get("/api/v1/admin/comments").param("q"," Alice% ").param("page","2").param("size","10").param("paginated","true"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(12)).andExpect(jsonPath("$.data.records[0].id").value(7));
    verify(db).queryForList(argThat(sql -> sql.contains("strpos(lower(c.content)") && sql.contains("strpos(lower(u.nickname)") && sql.contains("ORDER BY EXISTS") && sql.contains("NOT r.resolved) DESC")),eq("Alice%"),eq("Alice%"),eq(10),eq(10));
    verify(db).queryForObject(contains("strpos(lower(c.content)"),eq(Long.class),eq("Alice%"),eq("Alice%"));
    mvc.perform(get("/api/v1/admin/comments").param("q","x".repeat(101))).andExpect(status().isBadRequest());
  }
}
