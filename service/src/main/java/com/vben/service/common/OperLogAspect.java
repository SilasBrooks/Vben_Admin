package com.vben.service.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.module.monitor.entity.SysOperLog;
import com.vben.service.module.monitor.service.MonitorOperLogService;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 操作日志切面：拦截标注 {@link OperLog} 的 controller 方法，
 * 采集操作人/入参/结果/耗时后异步落库，日志失败绝不影响业务。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

  /** 密码类字段脱敏（password/newPassword/confirmPassword 等） */
  private static final Pattern SENSITIVE_FIELD =
      Pattern.compile("(\"[^\"]*(?i:password)[^\"]*\"\\s*:\\s*\")[^\"]*(\")");

  private static final int MAX_PARAM_LENGTH = 2000;

  private final ObjectMapper objectMapper;
  private final MonitorOperLogService operLogService;

  @Around("@annotation(operLog)")
  public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
    long start = System.currentTimeMillis();
    Throwable error = null;
    try {
      return joinPoint.proceed();
    } catch (Throwable ex) {
      error = ex;
      throw ex;
    } finally {
      try {
        saveLog(joinPoint, operLog, System.currentTimeMillis() - start, error);
      } catch (Exception ex) {
        log.debug("操作日志采集失败(不影响业务): {}", ex.getMessage());
      }
    }
  }

  private void saveLog(ProceedingJoinPoint joinPoint, OperLog operLog, long costMs,
      Throwable error) {
    SysOperLog logEntity = new SysOperLog();
    logEntity.setModule(operLog.module());
    logEntity.setDescription(operLog.description());

    // 操作人在请求线程内取出（异步后 ThreadLocal 不可用）
    LoginUser user = LoginUserHolder.get();
    if (user != null) {
      logEntity.setOperUserId(user.getUserId());
      logEntity.setOperName(user.getUsername());
    }

    logEntity.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "#"
        + joinPoint.getSignature().getName());
    logEntity.setCostMs(costMs);
    logEntity.setStatus(error == null ? 0 : 1);
    if (error != null) {
      logEntity.setErrorMsg(MonitorOperLogService.truncate(error.getMessage(), MAX_PARAM_LENGTH));
    }
    logEntity.setParams(
        MonitorOperLogService.truncate(
            desensitize(serializeArgs(joinPoint.getArgs())), MAX_PARAM_LENGTH));
    logEntity.setOperTime(LocalDateTime.now());

    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
      HttpServletRequest request = attrs.getRequest();
      logEntity.setRequestMethod(request.getMethod());
      logEntity.setRequestUrl(MonitorOperLogService.truncate(request.getRequestURI(), 255));
      logEntity.setIp(IpUtil.getClientIp(request));
    }
    operLogService.recordAsync(logEntity);
  }

  /** 序列化入参，跳过 servlet/文件等不可序列化对象 */
  private String serializeArgs(Object[] args) {
    if (args == null || args.length == 0) {
      return null;
    }
    StringBuilder sb = new StringBuilder("{");
    for (Object arg : args) {
      if (arg == null) {
        continue;
      }
      if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
          || arg instanceof MultipartFile || arg instanceof byte[]) {
        continue;
      }
      if (sb.length() > 1) {
        sb.append(',');
      }
      try {
        sb.append(objectMapper.writeValueAsString(arg));
      } catch (Exception e) {
        sb.append('"').append(arg.getClass().getSimpleName()).append('"');
      }
    }
    return sb.append('}').toString();
  }

  /** 密码类字段值替换为 *** */
  private String desensitize(String json) {
    if (json == null || json.isBlank()) {
      return json;
    }
    return SENSITIVE_FIELD.matcher(json).replaceAll("$1***$2");
  }
}
