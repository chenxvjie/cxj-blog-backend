package com.cxj.blog.auth;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookie {
  public static final String NAME = "cxj_session";
  private final boolean secure;
  public SessionCookie(@Value("${app.auth.cookie-secure:true}") boolean secure) { this.secure=secure; }
  public static String token(HttpServletRequest request) {
    if (request.getCookies()!=null) for (Cookie cookie:request.getCookies())
      if (NAME.equals(cookie.getName())) return cookie.getValue();
    return null;
  }
  public void write(HttpServletResponse response,String token,long seconds) {
    response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(NAME,token).httpOnly(true)
      .secure(secure).sameSite("Lax").path("/api/v1").maxAge(seconds).build().toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
  }
}
