package com.cxj.blog.auth;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Sends only an approved transactional template; never logs recipients, codes or credentials. */
public class SesCodeSender {
  private static final Logger log = LoggerFactory.getLogger(SesCodeSender.class);
  private final SesClient client;
  private final String from;
  private final long templateId;

  SesCodeSender(SesClient client, String from, long templateId) {
    this.client = client; this.from = from; this.templateId = templateId;
  }

  public void send(String email, String code) {
    if (client == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮箱验证码服务未配置");
    if (!code.matches("[0-9]{6}")) throw new IllegalArgumentException("Expected a six-digit verification code");
    Template template = new Template();
    template.setTemplateID(templateId);
    template.setTemplateData("{\"code\":\"" + code + "\"}");
    SendEmailRequest request = new SendEmailRequest();
    request.setFromEmailAddress(from);
    request.setDestination(new String[]{email});
    request.setSubject("CXJ Blog 邮箱验证码");
    request.setTemplate(template);
    request.setTriggerType(1L);
    try {
      var response = client.SendEmail(request);
      if (response == null || response.getMessageId() == null || response.getMessageId().isBlank()) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试");
      }
    } catch (TencentCloudSDKException e) {
      // Do not log the raw exception: provider messages may contain personal data.
      log.warn("SES SendEmail failed; errorCode={}, requestId={}", e.getErrorCode(), e.getRequestId());
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试");
    }
  }
}
