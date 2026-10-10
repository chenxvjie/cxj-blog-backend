package com.cxj.blog.controller;

import com.cxj.blog.auth.AuthService;
import com.cxj.blog.auth.AuthService.User;
import com.cxj.blog.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class CommunityController {
  private final JdbcTemplate db;
  private final AuthService auth;
  public CommunityController(JdbcTemplate db,AuthService auth) {this.db=db;this.auth=auth;}
  private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
  private void published(long id) {
    if(db.queryForObject("SELECT count(*) FROM blog_post WHERE id=? AND status='PUBLISHED' AND deleted_at IS NULL",Integer.class,id)==0)throw error(HttpStatus.NOT_FOUND,"文章不存在");
  }
  public record Profile(@NotBlank @Size(max=80) String nickname,@Size(max=1024) @Pattern(regexp="^$|https://[^\\s]+") String avatarUrl,@Size(max=500) String bio) {}
  @GetMapping("/manage/profile") public ApiResponse<?> profile(@AuthenticationPrincipal User user) {
    return ApiResponse.ok(db.queryForMap("SELECT id,email,nickname,avatar_url AS \"avatarUrl\",bio FROM sys_user WHERE id=?",user.id()));
  }
  @PutMapping("/manage/profile") public ApiResponse<?> profile(@Valid @RequestBody Profile p,@AuthenticationPrincipal User user) {
    db.update("UPDATE sys_user SET nickname=?,avatar_url=?,bio=? WHERE id=?",p.nickname().strip(),p.avatarUrl(),p.bio(),user.id());return profile(user);
  }
  public record Password(@NotBlank String currentPassword,@NotBlank @Size(min=8,max=72) String newPassword) {}
  @PutMapping("/manage/password") public ApiResponse<Void> password(@Valid @RequestBody Password p,@AuthenticationPrincipal User user) {
    auth.changePassword(user.id(),p.currentPassword(),p.newPassword());return ApiResponse.ok(null);
  }
  @GetMapping("/authors/{id}") public ApiResponse<?> author(@PathVariable long id) {
    var rows=db.queryForList("SELECT id,nickname,avatar_url AS \"avatarUrl\",bio FROM sys_user WHERE id=? AND deleted_at IS NULL",id);
    if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"作者不存在");return ApiResponse.ok(rows.getFirst());
  }
  @GetMapping("/taxonomy") public ApiResponse<?> taxonomy() {
    return ApiResponse.ok(Map.of("categories",db.queryForList("SELECT c.id,c.name,c.slug,(SELECT count(*) FROM blog_post p WHERE p.category_id=c.id AND p.status='PUBLISHED' AND p.deleted_at IS NULL) AS count FROM blog_category c WHERE c.deleted_at IS NULL ORDER BY c.sort_order,c.id"),
      "tags",db.queryForList("SELECT t.id,t.name,t.slug,(SELECT count(*) FROM blog_post_tag pt JOIN blog_post p ON p.id=pt.post_id WHERE pt.tag_id=t.id AND p.status='PUBLISHED' AND p.deleted_at IS NULL) AS count FROM blog_tag t WHERE t.deleted_at IS NULL ORDER BY t.id")));
  }
  public record Term(@NotBlank @Size(max=80) String name,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,120}") String slug) {}
  private String table(String kind) {return switch(kind){case "categories"->"blog_category";case "tags"->"blog_tag";default->throw error(HttpStatus.NOT_FOUND,"类型不存在");};}
  @PostMapping("/admin/taxonomy/{kind}") public ApiResponse<?> term(@PathVariable String kind,@Valid @RequestBody Term t) {
    return ApiResponse.ok(db.queryForMap("INSERT INTO "+table(kind)+"(name,slug) VALUES (?,?) RETURNING id,name,slug",t.name().strip(),t.slug()));
  }
  @PutMapping("/admin/taxonomy/{kind}/{id}") public ApiResponse<Void> term(@PathVariable String kind,@PathVariable long id,@Valid @RequestBody Term t) {
    if(db.update("UPDATE "+table(kind)+" SET name=?,slug=? WHERE id=? AND deleted_at IS NULL",t.name().strip(),t.slug(),id)!=1)throw error(HttpStatus.NOT_FOUND,"分类或标签不存在");return ApiResponse.ok(null);
  }
  @Transactional @DeleteMapping("/admin/taxonomy/{kind}/{id}") public ApiResponse<Void> term(@PathVariable String kind,@PathVariable long id) {
    // Soft deletion preserves associations in existing and pending article versions.
    db.update("UPDATE "+table(kind)+" SET deleted_at=now() WHERE id=?",id);return ApiResponse.ok(null);
  }
  @GetMapping("/manage/images") public ApiResponse<?> images(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@AuthenticationPrincipal User user) {
    String where=" WHERE deleted_at IS NULL AND uploader_id=?";
    size=Math.max(1,Math.min(size,50));
    int offset=(Math.max(1,Math.min(page,100000))-1)*size;
    return ApiResponse.ok(Map.of("records",db.queryForList("SELECT id,public_url AS \"publicUrl\",original_name AS \"originalName\",size_bytes AS \"sizeBytes\",created_at AS \"createdAt\" FROM file_object"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",user.id(),size,offset),"total",db.queryForObject("SELECT count(*) FROM file_object"+where,Long.class,user.id())));
  }
  public record Comment(@NotBlank @Size(max=2000) String content,Long parentId) {}
  @GetMapping("/posts/{id}/comments") public ApiResponse<?> comments(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="false") boolean paginated) {
    published(id);
    size=Math.max(1,Math.min(size,50));
    var records=db.queryForList("SELECT c.id,c.parent_id AS \"parentId\",c.author_id AS \"authorId\",u.nickname,u.avatar_url AS \"avatarUrl\",c.content,c.created_at AS \"createdAt\" FROM blog_comment c JOIN sys_user u ON u.id=c.author_id WHERE c.post_id=? AND c.status='APPROVED' ORDER BY c.id LIMIT ? OFFSET ?",id,size,(Math.max(1,Math.min(page,100000))-1)*size);
    return ApiResponse.ok(paginated ? Map.of("records",records,"total",db.queryForObject("SELECT count(*) FROM blog_comment c JOIN sys_user u ON u.id=c.author_id WHERE c.post_id=? AND c.status='APPROVED'",Long.class,id)) : records);
  }
  @Transactional @PostMapping("/manage/posts/{id}/comments") public ApiResponse<Void> comment(@PathVariable long id,@Valid @RequestBody Comment c,@AuthenticationPrincipal User user) {
    published(id);
    db.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",user.id());
    if(db.queryForObject("SELECT count(*) FROM blog_comment WHERE author_id=? AND created_at>now()-interval '1 hour'",Integer.class,user.id())>=20)throw error(HttpStatus.TOO_MANY_REQUESTS,"评论过于频繁，请稍后再试");
    if(c.parentId()!=null && db.queryForObject("SELECT count(*) FROM blog_comment WHERE id=? AND post_id=? AND status='APPROVED'",Integer.class,c.parentId(),id)==0)throw error(HttpStatus.BAD_REQUEST,"回复目标不存在");
    db.update("INSERT INTO blog_comment(post_id,author_id,parent_id,content,status) VALUES (?,?,?,?,?)",id,user.id(),c.parentId(),c.content().strip(),"APPROVED");return ApiResponse.ok(null);
  }
  @DeleteMapping("/manage/comments/{id}") public ApiResponse<Void> removeComment(@PathVariable long id,@AuthenticationPrincipal User user) {
    if(db.update("UPDATE blog_comment SET status='DELETED' WHERE id=? AND (author_id=? OR ?='ADMIN')",id,user.id(),user.role())!=1)throw error(HttpStatus.FORBIDDEN,"无权删除此评论");return ApiResponse.ok(null);
  }
  public record Report(@NotBlank @Size(max=500) String reason) {}
  @PostMapping("/manage/comments/{id}/report") public ApiResponse<Void> report(@PathVariable long id,@Valid @RequestBody Report r,@AuthenticationPrincipal User user) {
    if(db.queryForObject("SELECT count(*) FROM blog_comment c JOIN blog_post p ON p.id=c.post_id WHERE c.id=? AND c.status='APPROVED' AND p.status='PUBLISHED' AND p.deleted_at IS NULL",Integer.class,id)==0)throw error(HttpStatus.NOT_FOUND,"评论不存在");
    db.update("INSERT INTO comment_report(comment_id,reporter_id,reason) VALUES (?,?,?) ON CONFLICT(comment_id,reporter_id) DO UPDATE SET reason=excluded.reason,resolved=false,created_at=now() WHERE comment_report.resolved",id,user.id(),r.reason().strip());return ApiResponse.ok(null);
  }
  @GetMapping("/admin/comments") public ApiResponse<?> moderation(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="false") boolean paginated,@RequestParam(defaultValue="") String q) {
    size=Math.max(1,Math.min(size,50));
    String keyword=q.strip();
    if(keyword.length()>100)throw error(HttpStatus.BAD_REQUEST,"搜索内容最多100字");
    String from=" FROM blog_comment c JOIN sys_user u ON u.id=c.author_id WHERE c.status<>'DELETED'";
    var args=new ArrayList<Object>();
    if(!keyword.isEmpty()) {
      from+=" AND (strpos(lower(c.content),lower(?))>0 OR strpos(lower(u.nickname),lower(?))>0)";
      args.add(keyword);args.add(keyword);
    }
    Object[] countArgs=args.toArray();
    args.add(size);args.add((Math.max(1,Math.min(page,100000))-1)*size);
    var records=db.queryForList("SELECT c.*,u.nickname,(SELECT count(*) FROM comment_report r WHERE r.comment_id=c.id AND NOT r.resolved) AS reports"+from+" ORDER BY EXISTS (SELECT 1 FROM comment_report r WHERE r.comment_id=c.id AND NOT r.resolved) DESC,(c.status='PENDING') DESC,c.id DESC LIMIT ? OFFSET ?",args.toArray());
    return ApiResponse.ok(paginated ? Map.of("records",records,"total",db.queryForObject("SELECT count(*)"+from,Long.class,countArgs)) : records);
  }
  public record Moderate(@NotNull Boolean approved) {}
  @Transactional @PostMapping("/admin/comments/{id}/review") public ApiResponse<Void> moderate(@PathVariable long id,@Valid @RequestBody Moderate r) {
    if(db.update("UPDATE blog_comment SET status=? WHERE id=? AND status<>'DELETED'",r.approved()?"APPROVED":"REJECTED",id)!=1)throw error(HttpStatus.NOT_FOUND,"评论不存在");
    return ApiResponse.ok(null);
  }
  @PostMapping("/admin/comments/{id}/reports/resolve") public ApiResponse<Void> resolveReports(@PathVariable long id) {
    db.update("UPDATE comment_report SET resolved=true WHERE comment_id=?",id);return ApiResponse.ok(null);
  }
  @GetMapping("/admin/comments/{id}/reports") public ApiResponse<?> reports(@PathVariable long id) {return ApiResponse.ok(db.queryForList("SELECT reason,created_at FROM comment_report WHERE comment_id=? AND NOT resolved ORDER BY created_at",id));}
  @GetMapping("/site") public ApiResponse<?> site() {return ApiResponse.ok(db.queryForMap("SELECT title,description,about,contact FROM site_setting WHERE id=1"));}
  public record Site(@NotBlank @Size(max=100) String title,@Size(max=500) String description,@Size(max=20000) String about,@Size(max=320) String contact) {}
  @PutMapping("/admin/site") public ApiResponse<Void> site(@Valid @RequestBody Site s) {
    db.update("UPDATE site_setting SET title=?,description=?,about=?,contact=? WHERE id=1",s.title(),s.description(),s.about(),s.contact());return ApiResponse.ok(null);
  }
  @GetMapping("/admin/users") public ApiResponse<?> users(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="false") boolean paginated) {
    size=Math.max(1,Math.min(size,50));
    var records=db.queryForList("SELECT id,email,nickname,role,status,last_login_at FROM sys_user WHERE deleted_at IS NULL ORDER BY id LIMIT ? OFFSET ?",size,(Math.max(1,Math.min(page,100000))-1)*size);
    return ApiResponse.ok(paginated ? Map.of("records",records,"total",db.queryForObject("SELECT count(*) FROM sys_user WHERE deleted_at IS NULL",Long.class)) : records);
  }
  public record UserStatus(@NotNull Boolean active) {}
  @Transactional @PutMapping("/admin/users/{id}/status") public ApiResponse<Void> userStatus(@PathVariable long id,@Valid @RequestBody UserStatus s) {
    if(db.update("UPDATE sys_user SET status=? WHERE id=? AND role<>'ADMIN'",s.active()?"ACTIVE":"DISABLED",id)!=1)throw error(HttpStatus.BAD_REQUEST,"不能修改管理员状态");
    if(!s.active())db.update("DELETE FROM auth_session WHERE user_id=?",id);return ApiResponse.ok(null);
  }
}
