package com.cxj.blog.auth;

import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local & !prod")
public class LocalDevelopmentNotice implements ApplicationRunner {
  @Override public void run(ApplicationArguments args) {
    LoggerFactory.getLogger(getClass()).warn("本地开发模式：邮件、极验、COS及统计已停用；密码登录与文章功能仍需本地PostgreSQL。验证码发送、验证码登录及注册将提示服务未启用，不会伪造成功。生产部署请指定SPRING_PROFILES_ACTIVE=prod。");
  }
}
