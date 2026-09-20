package com.cxj.blog.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  public record User(long id, String email, String nickname, String role) {}
  public record Login(String accessToken, String tokenType, long expiresIn, User user) {}
  private final JdbcTemplate db;
  private final ObjectProvider<JavaMailSender> mail;
  private final SecureRandom random = new SecureRandom();
  private final String secret;
  private final String from;
  private final boolean enabled;
  public AuthService(JdbcTemplate db, ObjectProvider<JavaMailSender> mail,
      @Value("${app.auth.code-secret:}") String secret,
      @Value("${app.auth.mail-from:}") String from,
      @Value("${app.auth.email-enabled:false}") boolean enabled) {
    this.db = db; this.mail = mail; this.secret = secret; this.from = from; this.enabled = enabled;
    if (enabled && (secret.length() < 32 || from.isBlank()))
      throw new IllegalStateException("Email authentication requires a 32+ character AUTH_CODE_SECRET and MAIL_FROM");
  }
  static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
  static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  private String codeHash(String email, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
  private void quota(String scope, int max) {
    db.update("INSERT INTO auth_send_limit(scope) VALUES (?) ON CONFLICT DO NOTHING", scope);
    db.queryForObject("SELECT count FROM auth_send_limit WHERE scope=? FOR UPDATE", Integer.class, scope);
    db.update("UPDATE auth_send_limit SET count=0, window_start=now() WHERE scope=? AND window_start < now()-interval '1 hour'", scope);
    int count = db.queryForObject("SELECT count FROM auth_send_limit WHERE scope=?", Integer.class, scope);
    if (count >= max) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后重试");
    db.update("UPDATE auth_send_limit SET count=count+1 WHERE scope=?", scope);
  }
  @Transactional
  public void send(String rawEmail, String ip) {
    if (!enabled || mail.getIfAvailable() == null)
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮箱验证码服务未配置");
    String email = normalize(rawEmail);
    // Global row serializes sends across replicas; email and IP quotas remain persistent.
    quota("global", 100); quota("ip:" + ip, 20); quota("email:" + email, 5);
    Integer recent = db.queryForObject("SELECT count(*) FROM auth_code WHERE email=? AND sent_at > now()-interval '60 seconds'", Integer.class, email);
    if (recent > 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请等待60秒再发送");
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    db.update("INSERT INTO auth_code(email,code_hash,expires_at) VALUES (?,?,now()+interval '5 minutes') ON CONFLICT(email) DO UPDATE SET code_hash=excluded.code_hash, expires_at=excluded.expires_at, sent_at=now(), attempts=0", email, codeHash(email, code));
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from); message.setTo(email); message.setSubject("CXJ Blog 邮箱验证码");
    message.setText("您的验证码为：" + code + "，5分钟内有效，可用于登录或注册。请勿向他人提供。");
    try { mail.getObject().send(message); }
    catch (org.springframework.mail.MailException e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试"); }
  }
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Login login(String rawEmail, String code, String nickname) {
    if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮箱验证码服务未配置");
    String email = normalize(rawEmail);
    var rows = db.queryForList("SELECT code_hash, expires_at, attempts FROM auth_code WHERE email=? FOR UPDATE", email);
    if (rows.isEmpty()) throw invalidCode();
    var row = rows.getFirst();
    OffsetDateTime expiry = db.queryForObject("SELECT expires_at FROM auth_code WHERE email=?", OffsetDateTime.class, email);
    if (expiry.isBefore(OffsetDateTime.now()) || ((Number)row.get("attempts")).intValue() >= 5) throw invalidCode();
    db.update("UPDATE auth_code SET attempts=attempts+1 WHERE email=?", email);
    if (!MessageDigest.isEqual(codeHash(email, code).getBytes(StandardCharsets.US_ASCII), row.get("code_hash").toString().getBytes(StandardCharsets.US_ASCII))) throw invalidCode();
    db.update("DELETE FROM auth_code WHERE email=?", email);
    var users = db.query("SELECT id,email,nickname,role FROM sys_user WHERE lower(email)=? AND deleted_at IS NULL AND status='ACTIVE' FOR UPDATE", (rs,n)->new User(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),email);
    User user;
    if (nickname != null) {
      int exists = db.queryForObject("SELECT count(*) FROM sys_user WHERE lower(email)=? AND deleted_at IS NULL", Integer.class,email);
      if (exists > 0) throw new ResponseStatusException(HttpStatus.CONFLICT,"账户已存在，请登录");
      user = db.queryForObject("INSERT INTO sys_user(email,nickname,role) VALUES (?,?,'READER') RETURNING id,email,nickname,role", (rs,n)->new User(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),email,nickname.strip());
    } else {
      if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"账户不存在或不可用");
      user = users.getFirst();
    }
    byte[] bytes = new byte[32]; random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update("DELETE FROM auth_session WHERE expires_at < now()");
    db.update("INSERT INTO auth_session(token_hash,user_id,expires_at) VALUES (?,?,now()+interval '24 hours')",hash(token),user.id());
    db.update("UPDATE sys_user SET last_login_at=now() WHERE id=?",user.id());
    return new Login(token,"Bearer",86400,user);
  }
  private ResponseStatusException invalidCode() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"验证码无效或已过期"); }
  public User authenticate(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
    var users = db.query("SELECT u.id,u.email,u.nickname,u.role FROM auth_session s JOIN sys_user u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>now() AND u.status='ACTIVE' AND u.deleted_at IS NULL", (rs,n)->new User(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)),hash(token));
    return users.isEmpty() ? null : users.getFirst();
  }
  public void logout(String token) { db.update("DELETE FROM auth_session WHERE token_hash=?",hash(token)); }
}
