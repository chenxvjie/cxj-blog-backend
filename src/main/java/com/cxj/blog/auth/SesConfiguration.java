package com.cxj.blog.auth;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SesConfiguration {
  @Bean
  SesCodeSender sesCodeSender(
      @Value("${app.auth.email-enabled:false}") boolean enabled,
      @Value("${app.auth.ses.secret-id:}") String id,
      @Value("${app.auth.ses.secret-key:}") String key,
      @Value("${app.auth.ses.region:ap-hongkong}") String region,
      @Value("${app.auth.ses.template-id:219249}") long template,
      @Value("${app.auth.mail-from:}") String from) {
    if (!enabled) return new SesCodeSender(null, from, template);
    if (id.isBlank() || key.isBlank() || from.isBlank() || template <= 0
        || !(region.equals("ap-hongkong") || region.equals("ap-guangzhou"))) {
      throw new IllegalStateException("Enabled email authentication requires valid SES credentials, region, template ID and MAIL_FROM");
    }
    HttpProfile http = new HttpProfile();
    http.setEndpoint("ses.tencentcloudapi.com");
    http.setConnTimeout(5);
    http.setReadTimeout(10);
    http.setWriteTimeout(10);
    ClientProfile profile = new ClientProfile();
    profile.setDebug(false);
    profile.setHttpProfile(http);
    return new SesCodeSender(new SesClient(new Credential(id, key), region, profile), from, template);
  }
}
