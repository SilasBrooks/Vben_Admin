package com.vben.service.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sys_user_recovery_email")
public class RecoveryEmail {
  @TableId(type = IdType.INPUT)
  private Long userId;
  private String email;
  private LocalDateTime verifiedAt;
}
