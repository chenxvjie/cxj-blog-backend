package com.cxj.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cxj.blog.entity.BlogPost;
import org.apache.ibatis.annotations.Mapper;

@Mapper public interface BlogPostMapper extends BaseMapper<BlogPost> {}
