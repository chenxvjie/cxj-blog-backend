package com.cxj.blog.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CosConfigurationTest {
  private final ApplicationContextRunner context=new ApplicationContextRunner()
    .withUserConfiguration(CosConfiguration.class,ImageUploadService.class)
    .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class));
  @Test void disabledUploadsBootWithoutCredentials() {
    context.run(c -> {assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(ImageUploadService.class);
      assertFalse(c.getBean(CosConfiguration.Settings.class).enabled());});
  }
  @Test void enabledUploadsRequireDedicatedCredentials() {
    context.withPropertyValues("app.integrations.cos-enabled=true","app.cos.bucket=images-123",
      "app.cos.public-base-url=https://img.example.test").run(c -> assertThat(c).hasFailed());
  }
  @Test void invalidCdnOriginRejectedBeforeStartup() {
    var config=new CosConfiguration();
    for(String url:new String[]{"http://img.example.test","https://user@img.example.test","https://img.example.test/path",
      "https://img.example.test?key=secret","https://img.example.test#fragment"})
      assertThrows(IllegalStateException.class,()->config.cosSettings(true,"images-123",url));
  }
}
