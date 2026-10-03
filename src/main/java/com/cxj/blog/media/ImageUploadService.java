package com.cxj.blog.media;

import com.cxj.blog.auth.AuthService;
import com.qcloud.cos.exception.CosClientException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImageUploadService {
  public static final long MAX_BYTES=5*1024*1024;
  private static final Map<String,String> TYPES=Map.of("image/png","png","image/jpeg","jpg","image/webp","webp","image/gif","gif");
  private static final Logger LOG=LoggerFactory.getLogger(ImageUploadService.class);
  private final CosConfiguration.Settings settings;
  private final CosGateway cos;
  private final JdbcTemplate db;
  private final Clock clock;
  // Bounded, short-lived tickets. A backend restart invalidates unfinished uploads.
  private final Map<String,Ticket> tickets=new HashMap<>();
  private final Map<Long,Window> windows=new HashMap<>();
  private record Window(Instant expires, int count) {}
  private static class Ticket {
    final long owner; final String key; final Request request; final Instant expires;
    volatile Completed completed;
    Ticket(long owner,String key,Request request,Instant expires) { this.owner=owner;this.key=key;this.request=request;this.expires=expires; }
  }
  public record Request(String filename,String contentType,long size,String contentMd5) {}
  public record Prepared(String uploadId,String uploadUrl,Map<String,String> headers,long expiresAt) {}
  public record Completed(String publicUrl) {}
  @org.springframework.beans.factory.annotation.Autowired
  public ImageUploadService(CosConfiguration.Settings settings,CosGateway cos,JdbcTemplate db) {
    this(settings,cos,db,Clock.systemUTC());
  }
  ImageUploadService(CosConfiguration.Settings settings,CosGateway cos,JdbcTemplate db,Clock clock) {
    this.settings=settings;this.cos=cos;this.db=db;this.clock=clock;
  }
  private void enabled() {
    if (!settings.enabled()) throw error(HttpStatus.SERVICE_UNAVAILABLE,"图片上传暂未启用");
  }
  public Prepared prepare(Request request,AuthService.User user) {
    enabled();
    if (request.filename()==null || request.filename().isBlank() || request.filename().length()>255
        || request.filename().chars().anyMatch(Character::isISOControl)
        || !TYPES.containsKey(request.contentType()==null ? "" : request.contentType())
        || request.size()<1 || request.size()>MAX_BYTES || request.contentMd5()==null
        || !request.contentMd5().matches("[A-Za-z0-9+/]{22}=="))
      throw error(HttpStatus.BAD_REQUEST,"请选择不超过 5 MB 的 PNG、JPEG、WebP 或 GIF 图片");
    Instant now=clock.instant();
    String id=UUID.randomUUID().toString();
    String key="images/uploads/"+user.id()+"/"+id+"."+TYPES.get(request.contentType());
    Map<String,String> headers=Map.of("Content-Type",request.contentType(),"Content-MD5",request.contentMd5(),
        "Content-Length",Long.toString(request.size()),"x-cos-forbid-overwrite","true");
    synchronized (tickets) {
      tickets.values().removeIf(t -> !t.expires.isAfter(now));
      windows.values().removeIf(w -> !w.expires().isAfter(now));
      Window window=windows.get(user.id());
      if (tickets.size()>=1000 || (window!=null && window.count()>=30)
          || tickets.values().stream().filter(t -> t.owner==user.id() && t.completed==null).count()>=5)
        throw error(HttpStatus.TOO_MANY_REQUESTS,"上传过于频繁，请稍后重试");
      String url;
      try { url=cos.uploadUrl(key,now.plusSeconds(300),headers); }
      catch (CosClientException e) { throw unavailable(e); }
      tickets.put(id,new Ticket(user.id(),key,request,now.plusSeconds(600)));
      windows.put(user.id(),new Window(window==null ? now.plusSeconds(3600) : window.expires(),window==null ? 1 : window.count()+1));
      return new Prepared(id,url,headers,now.plusSeconds(300).toEpochMilli());
    }
  }
  public Completed complete(String id,AuthService.User user) {
    enabled();
    Ticket ticket;
    synchronized (tickets) { ticket=tickets.get(id); }
    if (ticket==null || ticket.owner!=user.id() || !ticket.expires.isAfter(clock.instant()))
      throw error(HttpStatus.NOT_FOUND,"上传记录已失效，请重新上传");
    synchronized (ticket) {
      if (ticket.completed!=null) return ticket.completed;
      CosGateway.Metadata object;
      try { object=cos.head(ticket.key); }
      catch (CosClientException e) { throw unavailable(e); }
      String md5=HexFormat.of().formatHex(Base64.getDecoder().decode(ticket.request.contentMd5()));
      if (object.size()!=ticket.request.size() || !ticket.request.contentType().equals(object.type())
          || object.etag()==null || !md5.equalsIgnoreCase(object.etag().replace("\"","")))
        throw error(HttpStatus.BAD_REQUEST,"上传文件校验失败，请重新上传");
      String url=settings.publicBaseUrl()+"/"+ticket.key;
      db.update("INSERT INTO file_object(uploader_id,cos_key,public_url,original_name,mime_type,size_bytes,usage_type) VALUES (?,?,?,?,?,?,'IMAGE')",
          user.id(),ticket.key,url,ticket.request.filename(),ticket.request.contentType(),ticket.request.size());
      ticket.completed=new Completed(url);
      return ticket.completed;
    }
  }
  private ResponseStatusException unavailable(CosClientException e) {
    // SDK messages can include signed URLs. Log the type only.
    LOG.warn("COS upload unavailable: {}",e.getClass().getSimpleName());
    return error(HttpStatus.SERVICE_UNAVAILABLE,"图片存储暂不可用，请稍后重试");
  }
  private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
