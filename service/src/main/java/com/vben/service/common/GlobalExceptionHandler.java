package com.vben.service.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一转换为 R 结构 + 对应 HTTP 状态码
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 业务异常 */
  @ExceptionHandler(BizException.class)
  public ResponseEntity<R<Void>> handleBiz(BizException e) {
    return ResponseEntity.status(e.getStatus()).body(R.fail(e.getMessage(), e.getMessage()));
  }

  /** @Valid 参数校验失败 */
  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  public ResponseEntity<R<Void>> handleValidation(Exception e) {
    String msg = I18nMessage.get("error.param.invalid");
    if (e instanceof MethodArgumentNotValidException ex) {
      FieldError fe = ex.getBindingResult().getFieldError();
      if (fe != null) {
        msg = fe.getDefaultMessage();
      }
    } else if (e instanceof ConstraintViolationException ex
        && ex.getConstraintViolations() != null
        && !ex.getConstraintViolations().isEmpty()) {
      msg = ex.getConstraintViolations().iterator().next().getMessage();
    }
    return ResponseEntity.badRequest().body(R.fail(msg, msg));
  }

  /** 上传大小超限（spring.servlet.multipart 约束）：统一转 400 错误结构，不落 500 */
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  public ResponseEntity<R<Void>> handleMaxUpload(Exception e) {
    String msg = I18nMessage.get("error.upload.tooLarge");
    return ResponseEntity.badRequest().body(R.fail(msg, msg));
  }

  /** 请求体不可读（如非法 JSON）：统一转 400，不落 500 */
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<R<Void>> handleUnreadable(Exception e) {
    String msg = I18nMessage.get("error.request.malformed");
    return ResponseEntity.badRequest().body(R.fail(msg, msg));
  }

  /** 静态资源/路径不存在（如 springdoc 关闭后的 swagger 端点）：按 404 返回，不落 500 */
  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<R<Void>> handleNoResource(Exception e) {
    return ResponseEntity.notFound().build();
  }

  /** 兜底 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<R<Void>> handleOther(Exception e) {
    log.error("未处理异常", e);
    return ResponseEntity.internalServerError()
        .body(R.fail("Internal Server Error", e.getMessage()));
  }
}
