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
