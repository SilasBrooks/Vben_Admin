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
    String msg = "参数校验失败";
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

  /** 兜底 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<R<Void>> handleOther(Exception e) {
    log.error("未处理异常", e);
    return ResponseEntity.internalServerError()
        .body(R.fail("Internal Server Error", e.getMessage()));
  }
}
