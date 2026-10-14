package com.cxj.blog.service;

import com.cxj.blog.auth.AuthService.User;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiQuotaServiceTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final AiQuotaService service=new AiQuotaService(db,"local-test-secret".repeat(3),Clock.fixed(Instant.parse("2026-10-14T16:01:00Z"),ZoneOffset.UTC));
  private final User user=new User(42,"quota@example.test","Test","USER");
  @Test void administratorNeverConsumesDailyQuota() {
    var admin=new User(1,"admin@example.test","Admin","ADMIN");
    assertTrue(service.quota(admin,"127.0.0.1").unlimited());
    assertNull(service.reserve(admin,"127.0.0.1").id());
    verifyNoInteractions(db);
  }
  @Test void dailyBoundaryUsesShanghaiInsteadOfUtc() {
    when(db.queryForObject(anyString(),eq(Integer.class),eq("user:42"),eq(LocalDate.parse("2026-10-15")))).thenReturn(4);
    var result=service.quota(user,"127.0.0.1");
    assertEquals(20,result.limit());assertEquals(16,result.remaining());
    assertEquals(Instant.parse("2026-10-15T16:00:00Z"),result.resetsAt());
  }
  @Test void exhaustedQuotaRejectsWithoutCreatingReservation() {
    when(db.queryForList(anyString(),eq("user:42"),any(LocalDate.class),eq(20))).thenReturn(List.of());
    assertEquals(429,assertThrows(ResponseStatusException.class,()->service.reserve(user,"127.0.0.1")).getStatusCode().value());
    verify(db,never()).update(anyString(),any(),any(),any());
  }
  @Test void guestQuotaIsTenAndAddressIsNotStored() {
    when(db.queryForList(anyString(),startsWith("guest:"),any(LocalDate.class),eq(10))).thenReturn(List.of(Map.of("used",1)));
    var reservation=service.reserve(null,"203.0.113.8");
    assertTrue(reservation.subject().matches("guest:[a-f0-9]{64}"));
    assertFalse(reservation.subject().contains("203.0.113.8"));
  }
  @Test void doubleRefundOnlyDecrementsOnce() {
    var reservation=new AiQuotaService.Reservation(UUID.randomUUID(),"user:42",LocalDate.parse("2026-10-15"));
    when(db.update(contains("SET state='REFUNDED'"),eq(reservation.id()))).thenReturn(1,0);
    service.refund(reservation);service.refund(reservation);
    verify(db,times(1)).update(contains("used=greatest"),eq("user:42"),eq(reservation.day()));
  }
}
