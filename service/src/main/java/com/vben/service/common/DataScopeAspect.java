package com.vben.service.common;

import com.vben.service.module.system.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 数据范围切面：进入标注 @DataScope 的方法前解析当前用户可见边界并放入
 * {@link DataScopeHolder}，方法返回后（含异常）finally 清理，杜绝 ThreadLocal 泄漏。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

  private final DataScopeService dataScopeService;

  @Around("@annotation(dataScope)")
  public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
    try {
      dataScopeService.applyForCurrentUser();
      return joinPoint.proceed();
    } finally {
      DataScopeHolder.clear();
    }
  }
}
