package com.cxj.blog.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AiStreamServiceTest {
  @RestController static class Endpoint {
    final AiStreamService service; final RestClient client;
    Endpoint(AiStreamService s,RestClient c){service=s;client=c;}
    @PostMapping(value="/stream",produces="text/event-stream") SseEmitter stream(){return service.open(client,"test",Map.of("question","test"),null,"127.0.0.1");}
  }
  void check(String body, boolean success) throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/internal/ask/stream", exchange -> {
      exchange.getRequestBody().readAllBytes();
      assertEquals("Bearer test",exchange.getRequestHeaders().getFirst("Authorization"));
      exchange.getResponseHeaders().set("Content-Type","text/event-stream");
      byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200,bytes.length);
      try(var output=exchange.getResponseBody()){output.write(bytes);}
    });
    server.start();
    try {
      var quota=mock(AiQuotaService.class);
      var reservation=new AiQuotaService.Reservation(UUID.randomUUID(),"guest:test",java.time.LocalDate.now());
      when(quota.reserve(null,"127.0.0.1")).thenReturn(reservation);
      when(quota.quota(null,"127.0.0.1")).thenReturn(new AiQuotaService.Quota(false,10,1,9,java.time.Instant.now()));
      var service=new AiStreamService(quota,new ObjectMapper());
      var mvc=MockMvcBuilders.standaloneSetup(new Endpoint(service,RestClient.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build())).build();
      var result=mvc.perform(post("/stream")).andReturn();
      result.getAsyncResult(5000);
      var response=mvc.perform(asyncDispatch(result)).andReturn().getResponse();
      assertEquals(200,response.getStatus());
      String content=response.getContentAsString(StandardCharsets.UTF_8);
      if(success) {
        assertTrue(content.contains("event:delta")); assertTrue(content.contains("event:done"));
        verify(quota).succeed(reservation);verify(quota,never()).refund(reservation);
      } else {
        assertTrue(content.contains("event:error"));assertFalse(content.contains("event:done"));
        verify(quota,never()).succeed(reservation);verify(quota).refund(reservation);
      }
    } finally {server.stop(0);}
  }
  @Test void completedStreamChargesOnce() throws Exception {
    check("event: delta\ndata: {\"text\":\"中文\"}\n\nevent: done\ndata: {\"answer\":\"中文[S1]\",\"sources\":[],\"grounded\":true}\n\n",true);
  }
  @Test void prematureEofRefundsOnce() throws Exception {check("event: delta\ndata: {\"text\":\"半截\"}\n\n",false);}
  @Test void upstreamValidationErrorRefundsOnce() throws Exception {check("event: error\ndata: {\"message\":\"invalid citation\"}\n\n",false);}
}
