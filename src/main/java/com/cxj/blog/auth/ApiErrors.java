package com.cxj.blog.auth;
import com.cxj.blog.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(new ApiResponse<>(e.getStatusCode().value(),e.getReason(),null));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(new ApiResponse<>(400,"请求参数格式错误",null));
  }
}
