package com.cxj.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("blog_post")
public class BlogPost {
  @TableId(type = IdType.AUTO) private Long id;
  private Long authorId; private Long categoryId; private String title; private String slug; private String summary;
  private String contentMd; private String contentHtml; private String coverUrl; private String status; private Boolean isTop;
  private Long viewCount; private OffsetDateTime publishedAt; private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private OffsetDateTime deletedAt;
  public Long getId(){return id;} public void setId(Long v){id=v;} public Long getAuthorId(){return authorId;} public void setAuthorId(Long v){authorId=v;}
  public Long getCategoryId(){return categoryId;} public void setCategoryId(Long v){categoryId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
  public String getSlug(){return slug;} public void setSlug(String v){slug=v;} public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
  public String getContentMd(){return contentMd;} public void setContentMd(String v){contentMd=v;} public String getCoverUrl(){return coverUrl;} public void setCoverUrl(String v){coverUrl=v;}
  public String getStatus(){return status;} public void setStatus(String v){status=v;} public Boolean getIsTop(){return isTop;} public void setIsTop(Boolean v){isTop=v;}
  public Long getViewCount(){return viewCount;} public void setViewCount(Long v){viewCount=v;} public OffsetDateTime getPublishedAt(){return publishedAt;} public void setPublishedAt(OffsetDateTime v){publishedAt=v;}
  public OffsetDateTime getCreatedAt(){return createdAt;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public OffsetDateTime getDeletedAt(){return deletedAt;}
}
