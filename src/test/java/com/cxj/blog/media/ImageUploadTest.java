package com.cxj.blog.media;

import com.cxj.blog.auth.AuthService;
import com.qcloud.cos.exception.CosClientException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ImageUploadTest {
  private static final AuthService.User USER=new AuthService.User(8,"user@example.test","reader","USER");
  private static final ImageUploadService.Request FILE=new ImageUploadService.Request("photo.png","image/png",3,"kAFQmDzST7DWlj99KOF/cg==");
  private static final String MD5="900150983cd24fb0d6963f7d28e17f72";
  private final CosGateway cos=mock(CosGateway.class);
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final MutableClock clock=new MutableClock();
  private final ImageUploadService service=new ImageUploadService(
      new CosConfiguration.Settings(true,"images-123","https://img.example.test"),cos,db,clock);
  private static class MutableClock extends Clock {
    Instant now=Instant.parse("2026-10-03T00:00:00Z");
    public ZoneId getZone() {return ZoneOffset.UTC;}
    public Clock withZone(ZoneId zone) {return this;}
    public Instant instant() {return now;}
  }
  private String prepare() {
    when(cos.uploadUrl(anyString(),any(),anyMap())).thenReturn("https://images.cos.example.test/signed");
    return service.prepare(FILE,USER).uploadId();
  }
  private void validObject() {when(cos.head(anyString())).thenReturn(new CosGateway.Metadata(3,"image/png",MD5));}
  private void status(int expected,Runnable run) {
    assertEquals(expected,assertThrows(ResponseStatusException.class,run::run).getStatusCode().value());
  }
  @Test void signerBindsSizeMimeChecksumAndOverwriteProtection() {
    prepare();
    verify(cos).uploadUrl(matches("images/uploads/8/[a-f0-9-]+\\.png"),eq(clock.now.plusSeconds(300)),eq(Map.of(
      "Content-Type","image/png","Content-Length","3","Content-MD5",FILE.contentMd5(),"x-cos-forbid-overwrite","true")));
    verifyNoInteractions(db);
  }
  @Test void validUploadRecordedOnceAndRepeatedCompletionIsIdempotent() {
    String id=prepare();validObject();
    String url=service.complete(id,USER).publicUrl();
    assertTrue(url.startsWith("https://img.example.test/images/uploads/8/"));
    assertEquals(url,service.complete(id,USER).publicUrl());
    verify(db,times(1)).update(anyString(),eq(8L),anyString(),eq(url),eq("photo.png"),eq("image/png"),eq(3L));
    verify(cos,times(1)).head(anyString());
  }
  @Test void otherAccountCannotCompleteEvenIfAdmin() {
    String id=prepare();
    status(404,()->service.complete(id,new AuthService.User(9,"admin@example.test","admin","ADMIN")));
    verify(cos,never()).head(anyString());verifyNoInteractions(db);
  }
  @Test void arbitraryKeyCannotBeCompleted() {status(404,()->service.complete("images/uploads/other.png",USER));verifyNoInteractions(db);}
  @Test void expiredTicketCannotComplete() {String id=prepare();clock.now=clock.now.plusSeconds(601);status(404,()->service.complete(id,USER));}
  @Test void oversizedEmptySvgAndInvalidChecksumCannotBeSigned() {
    for (var request : new ImageUploadService.Request[]{
      new ImageUploadService.Request("x.png","image/png",ImageUploadService.MAX_BYTES+1,FILE.contentMd5()),
      new ImageUploadService.Request("x.png","image/png",0,FILE.contentMd5()),
      new ImageUploadService.Request("x.svg","image/svg+xml",3,FILE.contentMd5()),
      new ImageUploadService.Request("x.png","image/png",3,"invalid"),
      new ImageUploadService.Request("x.png",null,3,FILE.contentMd5())}) status(400,()->service.prepare(request,USER));
    verifyNoInteractions(cos,db);
  }
  @Test void badSizeTypeOrChecksumNeverRecorded() {
    String id=prepare();
    for (var metadata : new CosGateway.Metadata[]{new CosGateway.Metadata(4,"image/png",MD5),
        new CosGateway.Metadata(3,"text/html",MD5),new CosGateway.Metadata(3,"image/png","bad")}) {
      when(cos.head(anyString())).thenReturn(metadata);status(400,()->service.complete(id,USER));
    }
    verifyNoInteractions(db);
  }
  @Test void cosFailureDoesNotLeakProviderMessageOrRecordObject() {
    String id=prepare();when(cos.head(anyString())).thenThrow(new CosClientException("secret-signed-url"));
    var error=assertThrows(ResponseStatusException.class,()->service.complete(id,USER));
    assertEquals(503,error.getStatusCode().value());assertFalse(error.getReason().contains("secret"));verifyNoInteractions(db);
  }
  @Test void atMostFiveUnfinishedUploadsPerUser() {for(int i=0;i<5;i++)prepare();status(429,this::prepare);}
  @Test void atMostThirtyUploadsPerHourAndWindowResets() {
    validObject();for(int i=0;i<30;i++)service.complete(prepare(),USER);
    status(429,this::prepare);clock.now=clock.now.plusSeconds(3601);assertNotNull(prepare());
  }
  @Test void disabledUploadsReturn503WithoutUsingCos() {
    var disabled=new ImageUploadService(new CosConfiguration.Settings(false,"",""),cos,db,clock);
    status(503,()->disabled.prepare(FILE,USER));verifyNoInteractions(cos,db);
  }
  @Test void realSdkSignsMandatoryHeadersAndUsesHttpsWithoutNetwork() {
    var config=new CosConfiguration();
    var settings=config.cosSettings(true,"cxj-blog-images-1317285711","https://img.chenxujie-bolg.cn/");
    try(var gateway=config.cosGateway(settings,"ap-nanjing","testSecretId","privateTestKey")) {
      String url=URLDecoder.decode(gateway.uploadUrl("images/uploads/8/a.png",clock.now.plusSeconds(300),Map.of(
        "Content-Type","image/png","Content-Length","3","Content-MD5",FILE.contentMd5(),"x-cos-forbid-overwrite","true")),StandardCharsets.UTF_8);
      assertTrue(url.startsWith("https://cxj-blog-images-1317285711.cos.ap-nanjing.myqcloud.com/"));
      assertTrue(url.contains("q-header-list=content-length;content-md5;content-type;host;x-cos-forbid-overwrite"),url);
      assertFalse(url.contains("privateTestKey"));
    }
  }
}
