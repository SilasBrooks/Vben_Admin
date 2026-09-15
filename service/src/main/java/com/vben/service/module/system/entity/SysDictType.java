package com.vben.service.module.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型表（dictType 全局唯一）
 */
@Data
@TableName("sys_dict_type")
public class SysDictType {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 字典名称，如「库存类型」 */
  private String dictName;

  /** 字典类型键，全局唯一，如 wsm_stock_type */
  private String dictType;

  /** 0 正常 1 停用 */
  private Integer status;

  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
