package com.cxj.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostRequest(
  Long authorId, Long categoryId, @NotBlank String title, @NotBlank String slug,
  String summary, @NotBlank String contentMd, String coverUrl, @jakarta.validation.constraints.Pattern(regexp="DRAFT|PUBLISHED|OFFLINE") String status, Boolean isTop
) {}
