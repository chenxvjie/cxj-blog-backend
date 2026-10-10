package com.cxj.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostRequest(
  Long authorId, Long categoryId, @NotBlank @jakarta.validation.constraints.Size(max=200) String title, @NotBlank @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{1,240}") String slug,
  @jakarta.validation.constraints.Size(max=500) String summary, @NotBlank String contentMd, @jakarta.validation.constraints.Size(max=1024) String coverUrl, @jakarta.validation.constraints.Pattern(regexp="DRAFT|PUBLISHED|OFFLINE|PENDING|REJECTED") String status, Boolean isTop,
  @jakarta.validation.constraints.Size(max=20) java.util.List<@jakarta.validation.constraints.Positive Long> tagIds
) {
  public PostRequest(Long authorId,Long categoryId,String title,String slug,String summary,String contentMd,String coverUrl,String status,Boolean isTop) {
    this(authorId,categoryId,title,slug,summary,contentMd,coverUrl,status,isTop,null);
  }
}
