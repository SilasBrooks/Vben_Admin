package com.vben.service.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据表（按 dictType 归组，同组内 dictValue 唯一）
 */
@Data
@TableName("sys_dict_data")
public class SysDictData {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属字典类型键（逻辑外键） */
  private String dictType;

  /** 显示名 */
  private String dictLabel;

  /** 值 */
  private String dictValue;

  private Integer sortNum;

  /** 0 正常 1 停用 */
  private Integer status;

  private String remark;

  private LocalDateTime createTime;
}
