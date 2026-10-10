package com.cxj.blog.auth;
import com.cxj.blog.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> conflict() {
    return ResponseEntity.status(409).body(new ApiResponse<>(409,"链接标识已存在或关联数据无效，请修改后重试",null));
  }
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(new ApiResponse<>(e.getStatusCode().value(),e.getReason(),null));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(400,"请求参数格式错误",null));
  }
}
