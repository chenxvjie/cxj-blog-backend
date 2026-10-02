package com.cxj.blog.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final JdbcTemplate db;
  private final String password;
  public AdminBootstrap(JdbcTemplate db,@Value("${ADMIN_INITIAL_PASSWORD:}") String password) {this.db=db;this.password=password;}
  @Override @Transactional public void run(ApplicationArguments args) {
    // Serialize bootstrap across instances. Never reset an existing password on restart.
    db.execute("SELECT pg_advisory_xact_lock(74192021)");
    var rows=db.queryForList("SELECT role,password_hash FROM sys_user WHERE lower(email)=? AND deleted_at IS NULL",AuthService.ADMIN_EMAIL);
    if (!rows.isEmpty() && !"ADMIN".equals(rows.getFirst().get("role")))
      throw new IllegalStateException("内置管理员邮箱已被普通账户使用，请人工核实归属；不会自动提升权限");
    if (!rows.isEmpty() && rows.getFirst().get("password_hash")!=null) return;
    if (password.isBlank()) {
      org.slf4j.LoggerFactory.getLogger(getClass()).warn("内置管理员初始化已跳过：请设置 ADMIN_INITIAL_PASSWORD 后重新启动；其他功能可继续使用。");
      return;
    }
    AuthService.validatePassword(password);
    String hash=AuthService.PASSWORDS.encode(password);
    if (rows.isEmpty()) db.update("INSERT INTO sys_user(email,nickname,role,password_hash) VALUES (?,'管理员','ADMIN',?)",AuthService.ADMIN_EMAIL,hash);
    else db.update("UPDATE sys_user SET password_hash=? WHERE lower(email)=? AND role='ADMIN' AND password_hash IS NULL AND deleted_at IS NULL",hash,AuthService.ADMIN_EMAIL);
  }
}
