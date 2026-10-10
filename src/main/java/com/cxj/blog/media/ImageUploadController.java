package com.cxj.blog.media;

import com.cxj.blog.auth.AuthService;
import com.cxj.blog.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manage/images")
public class ImageUploadController {
  private final ImageUploadService uploads;
  public ImageUploadController(ImageUploadService uploads) { this.uploads=uploads; }
  @DeleteMapping("/{id}")
  public ApiResponse<Void> remove(@PathVariable long id,@AuthenticationPrincipal AuthService.User user) { uploads.remove(id,user);return ApiResponse.ok(null); }
  public record Completion(@NotBlank String uploadId) {}
  @PostMapping("/upload-url")
  public ApiResponse<ImageUploadService.Prepared> prepare(@RequestBody ImageUploadService.Request request,
      @AuthenticationPrincipal AuthService.User user) { return ApiResponse.ok(uploads.prepare(request,user)); }
  @PostMapping("/complete")
  public ApiResponse<ImageUploadService.Completed> complete(@Valid @RequestBody Completion request,
      @AuthenticationPrincipal AuthService.User user) { return ApiResponse.ok(uploads.complete(request.uploadId(),user)); }
}
