package com.cxj.blog.auth;

import com.cxj.blog.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public record Send(@NotBlank @Email @Size(max=320) String email, @Valid @NotNull GeetestVerifier.Proof captcha) {}
  public record Credentials(@NotBlank @Email @Size(max=320) String email, @NotNull @Pattern(regexp="[0-9]{6}") String code) {}
  public record Registration(@NotBlank @Email @Size(max=320) String email, @NotNull @Pattern(regexp="[0-9]{6}") String code, @NotBlank @Size(max=80) String nickname, @NotBlank @Size(min=8,max=72) String password) {}
  public record PasswordLogin(@NotBlank @Email @Size(max=320) String email, @NotBlank @Size(min=8,max=72) String password) {}
  private final AuthService auth;
  private final GeetestVerifier geetest;
  private final SessionCookie cookie;
  public AuthController(AuthService auth, GeetestVerifier geetest, SessionCookie cookie) {this.auth=auth;this.geetest=geetest;this.cookie=cookie;}
  private ApiResponse<AuthService.Login> signedIn(AuthService.Login login,HttpServletRequest req,HttpServletResponse res) {
    String previous=SessionCookie.token(req);
    if(previous!=null) auth.logout(previous);
    cookie.write(res,login.accessToken(),86400);
    return ApiResponse.ok(login);
  }
  @GetMapping("/captcha-config") public ApiResponse<GeetestVerifier.Config> captchaConfig() {return ApiResponse.ok(geetest.config());}
  @PostMapping("/email-code") public ApiResponse<Void> send(@Valid @RequestBody Send body,HttpServletRequest req) {
    // Never trust arbitrary X-Forwarded-For. Behind Nginx this is a conservative shared-IP quota.
    geetest.verify(body.captcha());
    auth.send(body.email(),req.getRemoteAddr()); return ApiResponse.ok(null);
  }
  @PostMapping("/email-login") public ApiResponse<AuthService.Login> login(@Valid @RequestBody Credentials body,HttpServletRequest req,HttpServletResponse res) {return signedIn(auth.login(body.email(),body.code(),null),req,res);}
  @PostMapping("/register") public ApiResponse<AuthService.Login> register(@Valid @RequestBody Registration body,HttpServletRequest req,HttpServletResponse res) {return signedIn(auth.register(body.email(),body.code(),body.nickname(),body.password()),req,res);}
  @PostMapping("/password-login") public ApiResponse<AuthService.Login> passwordLogin(@Valid @RequestBody PasswordLogin body,HttpServletRequest req,HttpServletResponse res) {return signedIn(auth.passwordLogin(body.email(),body.password(),req.getRemoteAddr()),req,res);}
  @GetMapping("/me") public ApiResponse<AuthService.User> me(@AuthenticationPrincipal AuthService.User user,HttpServletResponse res) {res.setHeader("Cache-Control","no-store");return ApiResponse.ok(user);}
  @PostMapping("/logout") public ApiResponse<Void> logout(HttpServletRequest req,HttpServletResponse res) {
    String header=req.getHeader("Authorization");
    if(header!=null && header.startsWith("Bearer ")) auth.logout(header.substring(7));
    String token=SessionCookie.token(req); if(token!=null) auth.logout(token);
    cookie.write(res,"",0);return ApiResponse.ok(null);
  }
}
