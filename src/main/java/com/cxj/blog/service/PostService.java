package com.cxj.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cxj.blog.auth.AuthService.User;
import com.cxj.blog.dto.PostRequest;
import com.cxj.blog.entity.BlogPost;
import com.cxj.blog.mapper.BlogPostMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostService {
  private final BlogPostMapper mapper;
  private final JdbcTemplate db;
  private final ObjectMapper json;
  public PostService(BlogPostMapper mapper,JdbcTemplate db,ObjectMapper json) {this.mapper=mapper;this.db=db;this.json=json;}
  private boolean admin(User user) {return "ADMIN".equals(user.role());}
  private ResponseStatusException error(HttpStatus status,String message) {return new ResponseStatusException(status,message);}
  private void lock(long id) {db.queryForList("SELECT id FROM blog_post WHERE id=? FOR UPDATE",id);}
  private BlogPost owned(long id,User user) {
    BlogPost p=mapper.selectById(id);
    if(p==null || p.getDeletedAt()!=null) throw error(HttpStatus.NOT_FOUND,"文章不存在");
    if(!admin(user) && !Objects.equals(p.getAuthorId(),user.id())) throw error(HttpStatus.FORBIDDEN,"只能管理自己的投稿");
    return p;
  }
  private void apply(BlogPost p,PostRequest r) {
    p.setTitle(r.title());p.setSlug(r.slug());p.setSummary(r.summary());p.setContentMd(r.contentMd());
    p.setCoverUrl(r.coverUrl());p.setCategoryId(r.categoryId());p.setTagIds(r.tagIds());
  }
  private BlogPost decorate(BlogPost p) {
    if(p==null)return null;
    var authors=db.queryForList("SELECT nickname FROM sys_user WHERE id=?",p.getAuthorId());
    if(!authors.isEmpty())p.setAuthorName((String)authors.getFirst().get("nickname"));
    if(p.getCategoryId()!=null) {
      var cats=db.queryForList("SELECT name FROM blog_category WHERE id=? AND deleted_at IS NULL",p.getCategoryId());
      if(!cats.isEmpty())p.setCategoryName((String)cats.getFirst().get("name"));
    }
    var tags=db.queryForList("SELECT t.id,t.name,t.slug FROM blog_tag t JOIN blog_post_tag pt ON pt.tag_id=t.id WHERE pt.post_id=? AND t.deleted_at IS NULL ORDER BY t.id",p.getId());
    p.setTags(tags);p.setTagIds(tags.stream().map(t->((Number)t.get("id")).longValue()).toList());return p;
  }
  private void tags(long id,PostRequest r) {
    if(r.tagIds()==null)return;
    db.update("DELETE FROM blog_post_tag WHERE post_id=?",id);
    for(Long tag:r.tagIds().stream().distinct().toList()) {
      if(db.update("INSERT INTO blog_post_tag(post_id,tag_id) SELECT ?,id FROM blog_tag WHERE id=? AND deleted_at IS NULL",id,tag)!=1)throw error(HttpStatus.BAD_REQUEST,"标签已删除，请重新选择");
    }
  }
  private PostRequest decode(String value) {
    try {return json.readValue(value,PostRequest.class);} catch(Exception e) {throw new IllegalStateException("Invalid stored submission",e);}
  }
  private String encode(PostRequest value) {
    try {return json.writeValueAsString(value);} catch(Exception e) {throw new IllegalStateException(e);}
  }
  private BlogPost overlay(BlogPost p) {
    decorate(p);
    p.setPublicStatus(p.getStatus());
    var rows=db.queryForList("SELECT payload::text AS payload,status,review_reason FROM blog_post_submission WHERE post_id=?",p.getId());
    p.setHasSubmission(!rows.isEmpty());
    if(!rows.isEmpty()) {
      var row=rows.getFirst();apply(p,decode(row.get("payload").toString()));p.setStatus(row.get("status").toString());
      p.setReviewReason((String)row.get("review_reason"));
    }
    return p;
  }
  public Page<BlogPost> managed(long page,long size,User user) {
    return managed(page,size,user,null);
  }
  public Page<BlogPost> managed(long page,long size,User user,String status) {
    var filter=new LambdaQueryWrapper<BlogPost>().isNull(BlogPost::getDeletedAt).eq(!admin(user),BlogPost::getAuthorId,user.id());
    if(status!=null && !status.isBlank())filter.apply("COALESCE((SELECT s.status FROM blog_post_submission s WHERE s.post_id=blog_post.id),status)={0}",status);
    var result=mapper.selectPage(Page.of(Math.max(1,page),Math.max(1,Math.min(size,50))),filter.orderByDesc(BlogPost::getId));
    result.getRecords().forEach(this::overlay);return result;
  }
  public BlogPost editable(long id,User user) {return overlay(owned(id,user));}
  private void submission(BlogPost p,PostRequest r,User user) {
    if(p.getPublishedAt()!=null && !p.getSlug().equals(r.slug()))throw error(HttpStatus.BAD_REQUEST,"已发布文章的链接标识不能修改，以保留已有分享链接");
    if(!"USER".equals(user.role())) throw error(HttpStatus.FORBIDDEN,"当前账号不能投稿");
    String status=r.status()==null?"DRAFT":r.status();
    if(!"DRAFT".equals(status) && !"PENDING".equals(status)) throw error(HttpStatus.FORBIDDEN,"投稿需提交管理员审核，不能直接发布、下线或退回");
    if(Boolean.TRUE.equals(r.isTop()) && !Boolean.TRUE.equals(p.getIsTop())) throw error(HttpStatus.FORBIDDEN,"只有管理员可以置顶文章");
    var previous=db.queryForList("SELECT status FROM blog_post_submission WHERE post_id=?",p.getId());
    if(!previous.isEmpty() && "PENDING".equals(previous.getFirst().get("status"))) throw error(HttpStatus.CONFLICT,"投稿审核中，请先撤回再编辑");
    db.update("INSERT INTO blog_post_submission(post_id,payload,status) VALUES (?,?::jsonb,?) ON CONFLICT(post_id) DO UPDATE SET payload=excluded.payload,status=excluded.status,review_reason=NULL,updated_at=now()",p.getId(),encode(r),status);
  }
  private void publishFields(BlogPost p,PostRequest r,String status,boolean top) {
    if(p.getPublishedAt()!=null && !p.getSlug().equals(r.slug()))throw error(HttpStatus.BAD_REQUEST,"已发布文章的链接标识不能修改，以保留已有分享链接");
    var change=new LambdaUpdateWrapper<BlogPost>().eq(BlogPost::getId,p.getId()).isNull(BlogPost::getDeletedAt)
      .set(BlogPost::getTitle,r.title()).set(BlogPost::getSlug,r.slug()).set(BlogPost::getSummary,r.summary())
      .set(BlogPost::getContentMd,r.contentMd()).set(BlogPost::getContentHtml,null).set(BlogPost::getCoverUrl,r.coverUrl())
      .set(BlogPost::getCategoryId,r.categoryId()).set(BlogPost::getStatus,status).set(BlogPost::getIsTop,top);
    if("PUBLISHED".equals(status) && p.getPublishedAt()==null) change.set(BlogPost::getPublishedAt,OffsetDateTime.now());
    if(mapper.update(null,change)!=1) throw error(HttpStatus.CONFLICT,"文章已变更，请刷新");
    tags(p.getId(),r);
  }
  @Transactional public BlogPost update(long id,PostRequest r,User user) {
    lock(id);BlogPost p=owned(id,user);
    if(admin(user)) {
      String status=r.status()==null?"DRAFT":r.status();
      if(!java.util.List.of("DRAFT","PUBLISHED","OFFLINE").contains(status)) throw error(HttpStatus.BAD_REQUEST,"请使用审核操作处理投稿");
      if(!db.queryForList("SELECT post_id FROM blog_post_submission WHERE post_id=?",id).isEmpty()) throw error(HttpStatus.CONFLICT,"存在投稿版本，请先完成审核或撤回");
      publishFields(p,r,status,Boolean.TRUE.equals(r.isTop()));audit(id,user,"POST_UPDATE",null);
    } else submission(p,r,user);
    return editable(id,user);
  }
  @Transactional public BlogPost create(PostRequest r,User user) {
    if(r.slug()==null || !r.slug().matches("[A-Za-z0-9_-]{4,30}"))throw error(HttpStatus.BAD_REQUEST,"新文章链接标识须为4–30位英文字母、数字、短横线或下划线");
    if(!admin(user) && !java.util.List.of("DRAFT","PENDING").contains(r.status()==null?"DRAFT":r.status())) throw error(HttpStatus.FORBIDDEN,"投稿需提交管理员审核");
    if(!admin(user) && Boolean.TRUE.equals(r.isTop())) throw error(HttpStatus.FORBIDDEN,"只有管理员可以置顶文章");
    BlogPost p=new BlogPost();p.setAuthorId(user.id());apply(p,r);p.setIsTop(admin(user) && Boolean.TRUE.equals(r.isTop()));p.setViewCount(0L);
    String status=admin(user)?(r.status()==null?"DRAFT":r.status()):"DRAFT";
    if(!java.util.List.of("DRAFT","PUBLISHED","OFFLINE").contains(status)) throw error(HttpStatus.BAD_REQUEST,"无效的文章状态");
    p.setStatus(status);if("PUBLISHED".equals(status))p.setPublishedAt(OffsetDateTime.now());mapper.insert(p);
    if(!admin(user)) submission(p,r,user);else tags(p.getId(),r);
    return editable(p.getId(),user);
  }
  @Transactional public BlogPost withdraw(long id,User user) {
    lock(id);owned(id,user);
    if(db.update("UPDATE blog_post_submission SET status='DRAFT',review_reason=NULL,updated_at=now() WHERE post_id=? AND status='PENDING'",id)!=1) throw error(HttpStatus.CONFLICT,"当前没有待审核投稿");
    return editable(id,user);
  }
  @Transactional public BlogPost changeStatus(long id,String status,User user) {
    lock(id);BlogPost p=owned(id,user);
    if(!admin(user) && !java.util.List.of("DRAFT","PENDING").contains(status))throw error(HttpStatus.FORBIDDEN,"只能提交或撤回自己的投稿");
    var rows=db.queryForList("SELECT payload::text AS payload,status FROM blog_post_submission WHERE post_id=?",id);
    if(!rows.isEmpty() && "PENDING".equals(rows.getFirst().get("status"))) {
      if("DRAFT".equals(status))return withdraw(id,user);
      throw error(HttpStatus.CONFLICT,"待审核投稿请使用审核操作");
    }
    if(admin(user)) {
      if(!java.util.List.of("DRAFT","PUBLISHED","OFFLINE").contains(status))throw error(HttpStatus.BAD_REQUEST,"无效的文章状态");
      if(!rows.isEmpty())throw error(HttpStatus.CONFLICT,"作者仍有修改稿，请等待提交审核");
      mapper.update(null,new LambdaUpdateWrapper<BlogPost>().eq(BlogPost::getId,id).set(BlogPost::getStatus,status)
        .set("PUBLISHED".equals(status) && p.getPublishedAt()==null,BlogPost::getPublishedAt,OffsetDateTime.now()));
      audit(id,user,"POST_STATUS",status);
    } else {
      if(rows.isEmpty())throw error(HttpStatus.CONFLICT,"没有可提交的草稿，请先编辑保存");
      PostRequest r=decode(rows.getFirst().get("payload").toString());
      submission(p,new PostRequest(user.id(),r.categoryId(),r.title(),r.slug(),r.summary(),r.contentMd(),r.coverUrl(),status,r.isTop(),r.tagIds()),user);
    }
    return editable(id,user);
  }
  @Transactional public BlogPost review(long id,boolean approved,String reason,User user) {
    if(!admin(user)) throw error(HttpStatus.FORBIDDEN,"只有管理员可以审核");
    lock(id);BlogPost p=owned(id,user);
    var rows=db.queryForList("SELECT payload::text AS payload FROM blog_post_submission WHERE post_id=? AND status='PENDING'",id);
    if(rows.isEmpty())throw error(HttpStatus.CONFLICT,"投稿已撤回或已审核，请刷新");
    if(approved) {
      publishFields(p,decode(rows.getFirst().get("payload").toString()),"PUBLISHED",Boolean.TRUE.equals(p.getIsTop()));
      db.update("DELETE FROM blog_post_submission WHERE post_id=?",id);
    } else {
      if(reason==null || reason.isBlank())throw error(HttpStatus.BAD_REQUEST,"退回时请填写原因");
      db.update("UPDATE blog_post_submission SET status='REJECTED',review_reason=?,updated_at=now() WHERE post_id=?",reason.strip(),id);
    }
    audit(id,user,approved?"POST_APPROVE":"POST_REJECT",reason);return editable(id,user);
  }
  private void audit(long id,User user,String action,String reason) {
    db.update("INSERT INTO operation_log(operator_id,action,resource_type,resource_id,detail_json) VALUES (?,?,'POST',?,jsonb_build_object('reason',?::text))",user.id(),action,Long.toString(id),reason);
  }
  @Transactional public void delete(long id,User user) {
    lock(id);BlogPost p=owned(id,user);
    if(!admin(user) && "PUBLISHED".equals(p.getStatus()))throw error(HttpStatus.FORBIDDEN,"已发布文章请联系管理员撤稿");
    mapper.update(null,new LambdaUpdateWrapper<BlogPost>().eq(BlogPost::getId,id).set(BlogPost::getDeletedAt,OffsetDateTime.now()));
    db.update("DELETE FROM blog_post_submission WHERE post_id=?",id);audit(id,user,"POST_DELETE",null);
  }
  public Page<BlogPost> published(long page,long size) {
    var result=mapper.selectPage(Page.of(Math.max(1,page),Math.max(1,Math.min(size,50))),new LambdaQueryWrapper<BlogPost>()
      .eq(BlogPost::getStatus,"PUBLISHED").isNull(BlogPost::getDeletedAt).orderByDesc(BlogPost::getIsTop,BlogPost::getPublishedAt));
    result.getRecords().forEach(this::decorate);return result;
  }
  public Page<BlogPost> search(long page,long size,String q,Long category,Long tag,Long author) {
    var query=new LambdaQueryWrapper<BlogPost>().eq(BlogPost::getStatus,"PUBLISHED").isNull(BlogPost::getDeletedAt)
      .eq(category!=null,BlogPost::getCategoryId,category).eq(author!=null,BlogPost::getAuthorId,author);
    if(q!=null && !q.isBlank())query.and(w->w.like(BlogPost::getTitle,q.strip()).or().like(BlogPost::getSummary,q.strip()).or().like(BlogPost::getContentMd,q.strip()));
    if(tag!=null)query.inSql(BlogPost::getId,"SELECT post_id FROM blog_post_tag WHERE tag_id="+tag);
    var result=mapper.selectPage(Page.of(Math.max(1,page),Math.max(1,Math.min(size,50))),query.orderByDesc(BlogPost::getIsTop,BlogPost::getPublishedAt));
    result.getRecords().forEach(this::decorate);return result;
  }
  public BlogPost bySlug(String slug) {return decorate(mapper.selectOne(new LambdaQueryWrapper<BlogPost>().eq(BlogPost::getSlug,slug).eq(BlogPost::getStatus,"PUBLISHED").isNull(BlogPost::getDeletedAt)));}
}
