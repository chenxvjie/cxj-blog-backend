package com.cxj.blog.auth;

import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SesCodeSenderTest {
  final SesClient client = mock(SesClient.class);
  final SesCodeSender sender = new SesCodeSender(client, "noreply@mail.example.com", 219249);

  @Test void sendsApprovedTemplateWithCodeAndSingleRecipient() throws Exception {
    var response = new SendEmailResponse(); response.setMessageId("accepted-message-id");
    when(client.SendEmail(any())).thenReturn(response);
    sender.send("reader@example.com", "012345");
    var captor = ArgumentCaptor.forClass(SendEmailRequest.class);
    verify(client).SendEmail(captor.capture());
    var request = captor.getValue();
    assertEquals("noreply@mail.example.com", request.getFromEmailAddress());
    assertArrayEquals(new String[]{"reader@example.com"}, request.getDestination());
    assertEquals(219249L, request.getTemplate().getTemplateID());
    assertEquals("{\"code\":\"012345\"}", request.getTemplate().getTemplateData());
    assertEquals(1L, request.getTriggerType());
  }

  @Test void providerFailureIs503WithoutExposingProviderMessage() throws Exception {
    when(client.SendEmail(any())).thenThrow(new TencentCloudSDKException("sensitive-provider-detail"));
    var error = assertThrows(ResponseStatusException.class, () -> sender.send("reader@example.com", "123456"));
    assertEquals(503, error.getStatusCode().value());
    assertFalse(error.toString().contains("sensitive-provider-detail"));
    verify(client, times(1)).SendEmail(any());
  }

  @Test void missingMessageIdIsNotReportedAsSuccess() throws Exception {
    when(client.SendEmail(any())).thenReturn(new SendEmailResponse());
    assertEquals(503, assertThrows(ResponseStatusException.class,
        () -> sender.send("reader@example.com", "123456")).getStatusCode().value());
  }

  @Test void disabledSenderFailsClosed() {
    var disabled = new SesCodeSender(null, "", 219249);
    assertEquals(503, assertThrows(ResponseStatusException.class,
        () -> disabled.send("reader@example.com", "123456")).getStatusCode().value());
  }

  @Test void invalidCodeCannotBecomeTemplateJson() {
    assertThrows(IllegalArgumentException.class, () -> sender.send("reader@example.com", "\"bad"));
    verifyNoInteractions(client);
  }
}
