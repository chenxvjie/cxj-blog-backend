package com.cxj.blog.service;

import com.cxj.blog.auth.AuthService.User;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiQuotaService {
  private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
  private final JdbcTemplate db;
  private final String secret;
  private final Clock clock;
  @org.springframework.beans.factory.annotation.Autowired
  public AiQuotaService(JdbcTemplate db,@Value("${app.ai.quota-secret:}") String secret) {this(db,secret,Clock.systemUTC());}
  AiQuotaService(JdbcTemplate db,String secret,Clock clock) {this.db=db;this.secret=secret;this.clock=clock;}
  public record Quota(boolean unlimited,Integer limit,int used,Integer remaining,Instant resetsAt) {}
  public record Reservation(UUID id,String subject,LocalDate day) {}
  private LocalDate day(){return LocalDate.now(clock.withZone(ZONE));}
  private int limit(User user){return user==null?10:20;}
  private boolean admin(User user){return user!=null && "ADMIN".equals(user.role());}
  public String subject(User user,String address) {
    if(user!=null)return "user:"+user.id();
    if(secret.length()<32)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"AI游客额度配置未完成");
    try {
      var mac=javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
      return "guest:"+HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
    } catch(java.security.GeneralSecurityException e){throw new IllegalStateException("AI quota identifier failed");}
  }
  public Quota quota(User user,String address) {
    LocalDate day=day(); Instant reset=day.plusDays(1).atStartOfDay(ZONE).toInstant();
    if(admin(user))return new Quota(true,null,0,null,reset);
    Integer used=db.queryForObject("SELECT coalesce((SELECT used FROM ai_daily_usage WHERE subject=? AND usage_day=?),0)",Integer.class,subject(user,address),day);
    int count=used==null?0:used;
    return new Quota(false,limit(user),count,Math.max(0,limit(user)-count),reset);
  }
  @Transactional public Reservation reserve(User user,String address) {
    if(admin(user))return new Reservation(null,null,null);
    String subject=subject(user,address); LocalDate day=day(); UUID id=UUID.randomUUID();
    var rows=db.queryForList("""
      INSERT INTO ai_daily_usage(subject,usage_day,used) VALUES (?,?,1)
      ON CONFLICT(subject,usage_day) DO UPDATE SET used=ai_daily_usage.used+1
      WHERE ai_daily_usage.used<? RETURNING used
      """,subject,day,limit(user));
    if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"今日AI问答次数已用完，明天再来吧");
    db.update("INSERT INTO ai_usage_reservation(id,subject,usage_day,state) VALUES (?,?,?,'RESERVED')",id,subject,day);
    return new Reservation(id,subject,day);
  }
  public void succeed(Reservation reservation) {
    if(reservation.id()!=null)db.update("UPDATE ai_usage_reservation SET state='SUCCEEDED' WHERE id=? AND state='RESERVED'",reservation.id());
  }
  @Transactional public void refund(Reservation reservation) {
    if(reservation.id()==null)return;
    if(db.update("UPDATE ai_usage_reservation SET state='REFUNDED' WHERE id=? AND state='RESERVED'",reservation.id())==1)
      db.update("UPDATE ai_daily_usage SET used=greatest(0,used-1) WHERE subject=? AND usage_day=?",reservation.subject(),reservation.day());
  }
}
