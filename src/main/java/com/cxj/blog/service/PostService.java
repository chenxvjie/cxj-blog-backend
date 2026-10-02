package com.cxj.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cxj.blog.dto.PostRequest;
import com.cxj.blog.entity.BlogPost;
import com.cxj.blog.mapper.BlogPostMapper;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
  private final BlogPostMapper mapper;
  public PostService(BlogPostMapper mapper) { this.mapper = mapper; }
  public Page<BlogPost> managed(long page,long size,com.cxj.blog.auth.AuthService.User user) {
    return mapper.selectPage(Page.of(Math.max(1,page),Math.max(1,Math.min(size,50))),new LambdaQueryWrapper<BlogPost>()
      .isNull(BlogPost::getDeletedAt).eq(!"ADMIN".equals(user.role()),BlogPost::getAuthorId,user.id()).orderByDesc(BlogPost::getId));
  }
  public BlogPost editable(long id,com.cxj.blog.auth.AuthService.User user) {
    BlogPost p=mapper.selectById(id);
    if(p==null || p.getDeletedAt()!=null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"文章不存在");
    if(!"ADMIN".equals(user.role()) && !java.util.Objects.equals(p.getAuthorId(),user.id()))
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"只能修改或删除自己的文章");
    return p;
  }
  @Transactional public BlogPost update(long id,PostRequest r,com.cxj.blog.auth.AuthService.User user) {
    BlogPost p=editable(id,user);
    var change=new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BlogPost>()
      .eq(BlogPost::getId,id).isNull(BlogPost::getDeletedAt)
      .eq(!"ADMIN".equals(user.role()),BlogPost::getAuthorId,user.id())
      .set(BlogPost::getTitle,r.title()).set(BlogPost::getSlug,r.slug()).set(BlogPost::getSummary,r.summary())
      .set(BlogPost::getContentMd,r.contentMd()).set(BlogPost::getCoverUrl,r.coverUrl()).set(BlogPost::getCategoryId,r.categoryId())
      .set(BlogPost::getStatus,r.status()==null?"DRAFT":r.status()).set(BlogPost::getIsTop,Boolean.TRUE.equals(r.isTop()));
    if("PUBLISHED".equals(r.status()) && p.getPublishedAt()==null) change.set(BlogPost::getPublishedAt,OffsetDateTime.now());
    if(mapper.update(null,change)!=1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"文章已变更，请刷新");
    return mapper.selectById(id);
  }
  @Transactional public void delete(long id,com.cxj.blog.auth.AuthService.User user) {
    editable(id,user);
    mapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BlogPost>()
      .eq(BlogPost::getId,id).isNull(BlogPost::getDeletedAt).eq(!"ADMIN".equals(user.role()),BlogPost::getAuthorId,user.id())
      .set(BlogPost::getDeletedAt,OffsetDateTime.now()));
  }
  public Page<BlogPost> published(long page, long size) {
    return mapper.selectPage(Page.of(page, Math.min(size, 50)), new LambdaQueryWrapper<BlogPost>()
      .eq(BlogPost::getStatus, "PUBLISHED").isNull(BlogPost::getDeletedAt).orderByDesc(BlogPost::getIsTop, BlogPost::getPublishedAt));
  }
  public BlogPost bySlug(String slug) { return mapper.selectOne(new LambdaQueryWrapper<BlogPost>().eq(BlogPost::getSlug, slug).eq(BlogPost::getStatus, "PUBLISHED").isNull(BlogPost::getDeletedAt)); }
  @Transactional public BlogPost create(PostRequest r) {
    BlogPost p = new BlogPost(); p.setAuthorId(r.authorId()); p.setCategoryId(r.categoryId()); p.setTitle(r.title()); p.setSlug(r.slug()); p.setSummary(r.summary()); p.setContentMd(r.contentMd()); p.setCoverUrl(r.coverUrl());
    p.setStatus(r.status() == null ? "DRAFT" : r.status()); p.setIsTop(Boolean.TRUE.equals(r.isTop())); p.setViewCount(0L);
    if ("PUBLISHED".equals(p.getStatus())) p.setPublishedAt(OffsetDateTime.now()); mapper.insert(p); return p;
  }
}
