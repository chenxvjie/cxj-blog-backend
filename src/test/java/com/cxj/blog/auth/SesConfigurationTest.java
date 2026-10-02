package com.cxj.blog.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class SesConfigurationTest {
  final SesConfiguration config = new SesConfiguration();

  @Test void disabledEmailStartsWithoutCredentials() {
    new ApplicationContextRunner().withUserConfiguration(SesConfiguration.class).run(context -> {
      assertNull(context.getStartupFailure());
      assertNotNull(context.getBean(SesCodeSender.class));
    });
  }

  @Test void missingCredentialsRejectEnabledEmail() {
    assertThrows(IllegalStateException.class, () -> config.sesCodeSender(true, "", "", "ap-hongkong", 219249, "sender@example.com"));
  }

  @Test void enabledConfigurationCreatesClientWithoutMakingNetworkCalls() {
    assertNotNull(config.sesCodeSender(true, "test-id", "test-key", "ap-hongkong", 219249, "sender@example.com"));
  }

  @Test void invalidRegionRejected() {
    assertThrows(IllegalStateException.class, () -> config.sesCodeSender(true, "test-id", "test-key", "invalid", 219249, "sender@example.com"));
  }

  @Test void invalidTemplateRejected() {
    assertThrows(IllegalStateException.class, () -> config.sesCodeSender(true, "test-id", "test-key", "ap-hongkong", 0, "sender@example.com"));
  }

  @Test void smtpSenderAbsentFromRuntimeClasspath() {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("org.springframework.mail.javamail.JavaMailSender"));
  }
}
