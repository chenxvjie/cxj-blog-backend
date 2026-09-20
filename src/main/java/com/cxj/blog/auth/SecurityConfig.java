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
    // API accepts explicit Bearer tokens only, never cookie-based authentication.
    http.csrf(c -> c.disable()).cors(c -> {})
      .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .requestCache(c -> c.disable())
      .authorizeHttpRequests(c -> c
        .requestMatchers(HttpMethod.POST,"/api/v1/auth/email-code","/api/v1/auth/email-login","/api/v1/auth/register").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/v1/posts","/api/v1/posts/*","/actuator/health").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/v1/auth/me","/api/v1/auth/logout").authenticated()
        .anyRequest().denyAll())
      .exceptionHandling(c -> c
        .authenticationEntryPoint((req,res,e) -> {res.setStatus(401);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),new ApiResponse<>(401,"请先登录",null));})
        .accessDeniedHandler((req,res,e) -> {res.setStatus(403);res.setContentType("application/json;charset=UTF-8");json.writeValue(res.getOutputStream(),new ApiResponse<>(403,"权限不足",null));}))
      .addFilterBefore(new OncePerRequestFilter() {
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
          String header=req.getHeader("Authorization");
          if(header != null && header.startsWith("Bearer ")) {
            AuthService.User user=auth.authenticate(header.substring(7));
            if(user != null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role()))));
          }
          chain.doFilter(req,res);
        }
      }, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
