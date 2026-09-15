package com.vben.service.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 配置（分页插件 + 公共字段自动填充）
 */
@Configuration
public class MybatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
    // 单页上限保护
    pagination.setMaxLimit(500L);
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
  }

  /**
   * 公共字段自动填充：createTime 插入时、updateTime 插入/更新时自动写入当前时间。
   * 取代 MySQL 的 ON UPDATE CURRENT_TIMESTAMP / PG 触发器，跨数据库通用。
   */
  @Bean
  public MetaObjectHandler metaObjectHandler() {
    return new MetaObjectHandler() {
      @Override
      public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
      }

      @Override
      public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
      }
    };
  }
}
