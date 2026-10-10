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
  @GetMapping("/posts") public ApiResponse<Page<BlogPost>> list(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size,@RequestParam(required=false) @jakarta.validation.constraints.Size(max=100) String q,@RequestParam(required=false) Long category,@RequestParam(required=false) Long tag,@RequestParam(required=false) Long author,@RequestParam(required=false) String month) { return ApiResponse.ok(posts.search(page,size,q,category,tag,author,month)); }
  @GetMapping("/posts/{slug}") public ApiResponse<BlogPost> detail(@PathVariable String slug) {
    BlogPost post = posts.bySlug(slug); if (post == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章不存在"); return ApiResponse.ok(post);
  }
  @GetMapping("/manage/posts") public ApiResponse<Page<BlogPost>> managed(@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="10") long size,@RequestParam(required=false) String status,@org.springframework.security.core.annotation.AuthenticationPrincipal com.cxj.blog.auth.AuthService.User user) {return ApiResponse.ok(posts.managed(page,size,user,status));}
  @GetMapping("/manage/posts/{id}") public ApiResponse<BlogPost> editable(@PathVariable long id,@org.springframework.security.core.annotation.AuthenticationPrincipal com.cxj.blog.auth.AuthService.User user) {return ApiResponse.ok(posts.editable(id,user));}
  @PutMapping("/manage/posts/{id}") public ApiResponse<BlogPost> update(@PathVariable long id,@Valid @RequestBody PostRequest request,@org.springframework.security.core.annotation.AuthenticationPrincipal com.cxj.blog.auth.AuthService.User user) {return ApiResponse.ok(posts.update(id,request,user));}
  @DeleteMapping("/manage/posts/{id}") public ApiResponse<Void> delete(@PathVariable long id,@org.springframework.security.core.annotation.AuthenticationPrincipal com.cxj.blog.auth.AuthService.User user) {posts.delete(id,user);return ApiResponse.ok(null);}
  @PostMapping({"/admin/posts","/manage/posts"}) public ApiResponse<BlogPost> create(@Valid @RequestBody PostRequest request,
      @org.springframework.security.core.annotation.AuthenticationPrincipal com.cxj.blog.auth.AuthService.User user) {
    return ApiResponse.ok(posts.create(new PostRequest(user.id(), request.categoryId(), request.title(), request.slug(), request.summary(), request.contentMd(), request.coverUrl(), request.status(), request.isTop(),request.tagIds()),user));
  }
  public record Review(@jakarta.validation.constraints.NotNull Boolean approved,@jakarta.validation.constraints.Size(max=1000) String reason) {}
  @PostMapping("/manage/posts/{id}/withdraw") public ApiResponse<BlogPost> withdraw(@PathVariable long id,@org.springframework.security.core.annotation.AuthenticationPrincipal com.cxj.blog.auth.AuthService.User user) {return ApiResponse.ok(posts.withdraw(id,user));}
  @PostMapping("/manage/posts/{id}/review") public ApiResponse<BlogPost> review(@PathVariable long id,@Valid @RequestBody Review body,@org.springframework.security.core.annotation.AuthenticationPrincipal com.cxj.blog.auth.AuthService.User user) {return ApiResponse.ok(posts.review(id,body.approved(),body.reason(),user));}
}
