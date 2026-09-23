package com.vben.service.module.auth;

import com.vben.service.common.BizException;
import com.vben.service.common.I18nMessage;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RecoveryMailSender {
  private final ObjectProvider<JavaMailSender> sender;
  private final String host;
  private final String from;

  public RecoveryMailSender(ObjectProvider<JavaMailSender> sender,
      @Value("${spring.mail.host:}") String host,
      @Value("${vben.mail.from:}") String from) {
    this.sender = sender;
    this.host = host;
    this.from = from;
  }

  public boolean configured() {
    return !host.isBlank() && !from.isBlank() && sender.getIfAvailable() != null;
  }

  public void requireConfigured() {
    if (!configured()) throw BizException.badRequest("error.recovery.unavailable");
  }

  public void send(String email, String code, String purpose) {
    requireConfigured();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject(I18nMessage.get("mail.recovery.subject"));
    message.setText(I18nMessage.get("mail.recovery." + purpose, code));
    try {
      sender.getObject().send(message);
    } catch (MailException e) {
      DeliveryFailure failure = classify(e);
      // 只记录白名单诊断字段。原始异常及其堆栈可能包含邮箱、正文或认证信息。
      log.warn("Recovery email delivery failed: reason={}, type={}, smtpStatus={}",
          failure.key(), failure.type(), failure.smtpStatus());
      throw BizException.badRequest(failure.key());
    }
  }

  private static DeliveryFailure classify(MailException failure) {
    var pending = new ArrayDeque<Throwable>();
    var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    pending.add(failure);
    DeliveryFailure fallback = new DeliveryFailure("delivery", failure, null);
    while (!pending.isEmpty()) {
      Throwable cause = pending.removeFirst();
      if (!visited.add(cause)) continue;
      if (cause instanceof MailAuthenticationException || cause instanceof AuthenticationFailedException) {
        return new DeliveryFailure("smtpAuthentication", cause, null);
      }
      if (cause instanceof SocketTimeoutException) {
        return new DeliveryFailure("smtpTimeout", cause, null);
      }
      if (cause instanceof SSLException) {
        return new DeliveryFailure("smtpTls", cause, null);
      }
      if (cause instanceof ConnectException || cause instanceof UnknownHostException
          || cause instanceof NoRouteToHostException) {
        return new DeliveryFailure("smtpConnection", cause, null);
      }
      if (cause instanceof SMTPAddressFailedException address) {
        return new DeliveryFailure("smtpRecipient", cause, address.getReturnCode());
      }
      if (cause instanceof SMTPSenderFailedException sender) {
        return new DeliveryFailure("smtpRejected", cause, sender.getReturnCode());
      }
      if (cause instanceof SMTPSendFailedException smtp) {
        // QQ 在 DATA 阶段也可能返回此错误，不能仅靠 SMTPAddressFailedException 判断。
        if (smtp.getReturnCode() == 550 && smtp.getMessage() != null
            && smtp.getMessage().toLowerCase(Locale.ROOT)
                .contains("the recipient may contain a non-existent account")) {
          return new DeliveryFailure("smtpRecipientNotFound", cause, smtp.getReturnCode());
        }
        fallback = new DeliveryFailure("smtpRejected", cause, smtp.getReturnCode());
      } else if (cause instanceof SendFailedException && fallback.smtpStatus() == null) {
        fallback = new DeliveryFailure("smtpRejected", cause, null);
      }
      if (cause.getCause() != null) pending.addLast(cause.getCause());
      if (cause instanceof MessagingException mail && mail.getNextException() != null) {
        pending.addLast(mail.getNextException());
      }
      if (cause instanceof MailSendException mail) {
        Collections.addAll(pending, mail.getMessageExceptions());
      }
    }
    return fallback;
  }

  private record DeliveryFailure(String key, String type, Integer smtpStatus) {
    private DeliveryFailure(String reason, Throwable failure, Integer smtpStatus) {
      this("error.recovery." + reason, failure.getClass().getSimpleName(), smtpStatus);
    }
  }
}
