package com.cxj.blog.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalProfileTest {
  ApplicationContextRunner runner=new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(SesConfiguration.class, AuthService.class, GeetestVerifier.class)
      .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class))
      .withBean(RestClient.Builder.class,RestClient::builder);

  @Test void localIgnoresProductionSwitchesAndDatabaseUrl() {
    runner.withPropertyValues("spring.profiles.active=local", "AUTH_EMAIL_ENABLED=true", "GEETEST_ENABLED=true",
        "DB_URL=jdbc:postgresql://production.invalid:5432/prod", "AUTH_CODE_SECRET=", "MAIL_FROM=",
        "TENCENTCLOUD_SECRET_ID=", "TENCENTCLOUD_SECRET_KEY=", "GEETEST_CAPTCHA_ID=", "GEETEST_CAPTCHA_KEY=")
      .run(context->{
        assertNull(context.getStartupFailure());
        assertEquals("jdbc:postgresql://localhost:5433/cxj_blog",context.getEnvironment().getProperty("spring.datasource.url"));
        assertEquals("127.0.0.1",context.getEnvironment().getProperty("server.address"));
        assertFalse(context.getBean(GeetestVerifier.class).config().enabled());
        var auth=context.getBean(AuthService.class);
        // Spring invokes JdbcTemplate.afterPropertiesSet during bean initialization.
        clearInvocations(context.getBean(JdbcTemplate.class));
        assertEquals(503,assertThrows(ResponseStatusException.class,()->auth.send("test@example.com","127.0.0.1")).getStatusCode().value());
        var error=assertThrows(ResponseStatusException.class,()->auth.login("test@example.com","123456",null));
        assertTrue(error.getReason().contains("已停止"));
        verifyNoInteractions(context.getBean(JdbcTemplate.class));
      });
  }

  @Test void prodDoesNotSilentlyDisableMissingCloudConfiguration() {
    runner.withPropertyValues("spring.profiles.active=prod", "AUTH_EMAIL_ENABLED=true",
        "TENCENTCLOUD_SECRET_ID=", "TENCENTCLOUD_SECRET_KEY=")
      .run(context->assertNotNull(context.getStartupFailure()));
  }

  @Test void localIsTheDefaultProfile() {
    runner.withPropertyValues("spring.profiles.active=", "AUTH_EMAIL_ENABLED=true", "GEETEST_ENABLED=true")
      .run(context->{
        assertNull(context.getStartupFailure());
        assertEquals("local",context.getEnvironment().getDefaultProfiles()[0]);
        assertFalse(context.getBean(GeetestVerifier.class).config().enabled());
      });
  }
}
