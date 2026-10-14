package com.cxj.blog.service;

import com.cxj.blog.auth.AuthService.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiStreamService {
  private final AiQuotaService quota;
  private final ObjectMapper json;
  private final Semaphore capacity = new Semaphore(16);
  public AiStreamService(AiQuotaService quota, ObjectMapper json) { this.quota=quota; this.json=json; }

  public SseEmitter open(RestClient client, String token, Map<String,Object> payload, User user, String address) {
    if(!capacity.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"AI服务繁忙，请稍后重试");
    AiQuotaService.Reservation reservation;
    try { reservation=quota.reserve(user,address); }
    catch(RuntimeException e) { capacity.release(); throw e; }
    var emitter=new SseEmitter(120000L);
    var run=new Run(emitter,reservation,user,address);
    emitter.onTimeout(run::abort);
    emitter.onError(e -> run.abort());
    emitter.onCompletion(run::abort);
    Thread worker=Thread.ofVirtual().unstarted(() -> {
      try {
        if(run.closed()) return;
        client.post().uri("/internal/ask/stream").header("Authorization","Bearer "+token)
          .body(payload).exchange((request,response) -> {
            if(!response.getStatusCode().is2xxSuccessful()) throw new IOException("Upstream unavailable");
            try(var input=response.getBody()) {
              run.input.set(input);
              if(run.closed()) return null;
              var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
              String event="", data="", line;
              int total=0;
              while(!run.closed() && (line=readLine(reader))!=null) {
                total+=line.length();
                if(total>1000000) throw new IOException("Stream too large");
                if(line.isEmpty()) {
                  if(!data.isEmpty()) run.event(event,data);
                  event="";data="";
                } else if(line.startsWith("event:")) event=line.substring(6).strip();
                else if(line.startsWith("data:")) data+=line.substring(5).strip();
              }
              if(!run.closed()) throw new IOException("Incomplete stream");
            } finally { run.input.set(null); }
            return null;
          });
      } catch(Exception e) { run.fail(); }
      finally { run.abort(); capacity.release(); }
    });
    run.worker.set(worker);
    worker.start();
    return emitter;
  }

  private static String readLine(Reader reader) throws IOException {
    var value=new StringBuilder();
    int ch;
    while((ch=reader.read())!=-1) {
      if(ch=='\n') return value.toString();
      if(ch!='\r') value.append((char)ch);
      if(value.length()>131072) throw new IOException("Event too large");
    }
    return value.isEmpty()?null:value.toString();
  }

  private final class Run {
    final SseEmitter emitter;
    final AiQuotaService.Reservation reservation;
    final User user;
    final String address;
    final AtomicReference<InputStream> input=new AtomicReference<>();
    final AtomicReference<Thread> worker=new AtomicReference<>();
    boolean terminal;
    Run(SseEmitter e,AiQuotaService.Reservation r,User u,String a) {emitter=e;reservation=r;user=u;address=a;}
    synchronized boolean closed(){return terminal;}
    synchronized void event(String name,String raw) throws IOException {
      if(terminal) return;
      var data=json.readTree(raw);
      if("done".equals(name)) {
        if(!data.path("answer").isTextual() || !data.path("sources").isArray() || !data.path("grounded").isBoolean()) throw new IOException("Invalid answer");
        emitter.send(SseEmitter.event().name("done").data(Map.of("answer",data,"quota",quota.quota(user,address))));
        quota.succeed(reservation);
        terminal=true;
        emitter.complete();
      } else if("error".equals(name)) {
        fail();
      } else if("delta".equals(name) || "status".equals(name) || "heartbeat".equals(name)) {
        emitter.send(SseEmitter.event().name(name).data(data));
      } else throw new IOException("Unknown event");
    }
    synchronized void fail() {
      if(terminal) return;
      terminal=true;
      quota.refund(reservation);
      try { emitter.send(SseEmitter.event().name("error").data(Map.of("message","回答生成失败，本次未扣次数，请重试"))); }
      catch(Exception ignored) { }
      emitter.complete();
    }
    void abort() {
      synchronized(this) {
        if(!terminal) { terminal=true; quota.refund(reservation); }
      }
      var stream=input.getAndSet(null);
      if(stream!=null) try {stream.close();} catch(IOException ignored) { }
      var thread=worker.get();
      if(thread!=null && thread!=Thread.currentThread()) thread.interrupt();
    }
  }
}
