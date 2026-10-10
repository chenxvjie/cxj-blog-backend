package com.cxj.blog.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cxj.blog.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean SecurityFilterChain security(HttpSecurity http, AuthService auth, ObjectMapper json) throws Exception {
    // Cookie writes require a non-simple custom header. Browser cross-origin
    // callers must pass the strict CORS allowlist before they can send it.
    http.csrf(c -> c.disable()).cors(c -> {})
      .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .requestCache(c -> c.disable())
      .authorizeHttpRequests(c -> c
        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
        .requestMatchers(HttpMethod.POST,"/api/v1/auth/password-login").permitAll()
        .requestMatchers("/api/v1/manage/posts","/api/v1/manage/posts/**").authenticated()
        .requestMatchers("/api/v1/manage/**").hasAnyRole("USER","ADMIN")
        .requestMatchers(HttpMethod.POST,"/api/v1/manage/images/upload-url","/api/v1/manage/images/complete").hasAnyRole("USER","ADMIN")
        .requestMatchers(HttpMethod.POST,"/api/v1/auth/email-code","/api/v1/auth/email-login","/api/v1/auth/register").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/v1/posts","/api/v1/posts/*","/actuator/health","/api/v1/auth/captcha-config").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/v1/posts/*/comments","/api/v1/authors/*","/api/v1/taxonomy","/api/v1/site").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/v1/auth/me","/api/v1/auth/logout").authenticated()
        .anyRequest().denyAll())
      .exceptionHandling(c -> c
        .authenticationEntryPoint((req,res,e) -> {res.setStatus(401);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),new ApiResponse<>(401,"请先登录",null));})
        .accessDeniedHandler((req,res,e) -> {res.setStatus(403);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),new ApiResponse<>(403,"权限不足",null));}))
      .addFilterBefore(new OncePerRequestFilter() {
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
          String header=req.getHeader("Authorization");
          boolean bearer=header != null && header.startsWith("Bearer ");
          String token=bearer?header.substring(7):SessionCookie.token(req);
          if(!bearer && token!=null && !List.of("GET","HEAD","OPTIONS").contains(req.getMethod()) && !"1".equals(req.getHeader("X-Blog-Request"))) {
            res.setStatus(403);res.setContentType("application/json;charset=UTF-8");
            json.writeValue(res.getOutputStream(),new ApiResponse<>(403,"请求校验失败，请刷新页面",null));return;
          }
          if(token!=null) {
            AuthService.User user=auth.authenticate(token);
            if(user != null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role()))));
          }
          chain.doFilter(req,res);
        }
      }, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
