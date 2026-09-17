package com.cxj.blog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cxj.blog.dto.ApiResponse;
import com.cxj.blog.dto.PostRequest;
import com.cxj.blog.entity.BlogPost;
import com.cxj.blog.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class PostController {
  private final PostService posts;
  public PostController(PostService posts) { this.posts = posts; }
  @GetMapping("/posts") public ApiResponse<Page<BlogPost>> list(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) { return ApiResponse.ok(posts.published(page, size)); }
  @GetMapping("/posts/{slug}") public ApiResponse<BlogPost> detail(@PathVariable String slug) {
    BlogPost post = posts.bySlug(slug); if (post == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在"); return ApiResponse.ok(post);
  }
  // Temporary development endpoint. Replace with @PreAuthorize("hasRole('ADMIN')") after email login is implemented.
  @PostMapping("/admin/posts") public ApiResponse<BlogPost> create(@Valid @RequestBody PostRequest request) { return ApiResponse.ok(posts.create(request)); }
}
