package com.cxj.blog.auth;

import com.cxj.blog.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public record Send(@NotBlank @Email @Size(max=320) String email) {}
  public record Credentials(@NotBlank @Email @Size(max=320) String email, @NotNull @Pattern(regexp="[0-9]{6}") String code) {}
  public record Registration(@NotBlank @Email @Size(max=320) String email, @NotNull @Pattern(regexp="[0-9]{6}") String code, @NotBlank @Size(max=80) String nickname) {}
  private final AuthService auth;
  public AuthController(AuthService auth) {this.auth=auth;}
  @PostMapping("/email-code") public ApiResponse<Void> send(@Valid @RequestBody Send body,HttpServletRequest req) {
    // Never trust arbitrary X-Forwarded-For. Behind Nginx this is a conservative shared-IP quota.
    auth.send(body.email(),req.getRemoteAddr()); return ApiResponse.ok(null);
  }
  @PostMapping("/email-login") public ApiResponse<AuthService.Login> login(@Valid @RequestBody Credentials body) {return ApiResponse.ok(auth.login(body.email(),body.code(),null));}
  @PostMapping("/register") public ApiResponse<AuthService.Login> register(@Valid @RequestBody Registration body) {return ApiResponse.ok(auth.login(body.email(),body.code(),body.nickname()));}
  @GetMapping("/me") public ApiResponse<AuthService.User> me(@AuthenticationPrincipal AuthService.User user) {return ApiResponse.ok(user);}
  @PostMapping("/logout") public ApiResponse<Void> logout(@RequestHeader("Authorization") String header) {auth.logout(header.substring(7)); return ApiResponse.ok(null);}
}
