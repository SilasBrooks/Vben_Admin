package com.vben.service.module.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.vben.service.common.BizException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.stream.Stream;
import javax.net.ssl.SSLHandshakeException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class RecoveryMailSenderTest {
  @SuppressWarnings("unchecked") private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
  @Test void missingSmtpConfigurationNeverPretendsToSend() {
    RecoveryMailSender mail = new RecoveryMailSender(provider, "", "");
    assertThat(mail.configured()).isFalse();
    assertThatThrownBy(() -> mail.send("test@example.invalid", "123456", "reset")).isInstanceOf(BizException.class);
    verifyNoInteractions(provider);
  }
  @Test void smtpFailureIsLocalizedWithoutExposingServerDetails() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(provider.getIfAvailable()).thenReturn(sender); when(provider.getObject()).thenReturn(sender);
    doThrow(new MailSendException("sensitive SMTP diagnostic")).when(sender).send(any(SimpleMailMessage.class));
    RecoveryMailSender mail = new RecoveryMailSender(provider, "smtp.example.invalid", "from@example.invalid");
    assertThatThrownBy(() -> mail.send("test@example.invalid", "123456", "reset"))
        .isInstanceOf(BizException.class).hasMessage("error.recovery.delivery").hasNoCause();
  }

  static Stream<Arguments> deliveryFailures() throws Exception {
    var rejectedAddress = new SMTPAddressFailedException(
        new InternetAddress("private@example.invalid"), "RCPT TO", 550, "secret provider diagnostic");
    var rejectedMessage = new SMTPSendFailedException("DATA", 554,
        "secret provider diagnostic", null, null, null, null);
    return Stream.of(
        Arguments.of(new MailAuthenticationException("secret authorization code"), "smtpAuthentication"),
        Arguments.of(new MailSendException("secret", new MessagingException("secret",
            new SocketTimeoutException("secret"))), "smtpTimeout"),
        Arguments.of(new MailSendException("secret", new ConnectException("secret host")), "smtpConnection"),
        Arguments.of(new MailSendException("secret", new SSLHandshakeException("secret certificate")), "smtpTls"),
        Arguments.of(new MailSendException(Map.of(new Object(), rejectedAddress)), "smtpRecipient"),
        Arguments.of(new MailSendException(Map.of(new Object(), new SMTPSendFailedException("DATA", 550,
            "550 The recipient may contain a non-existent account, please check the recipient address.",
            null, null, null, null))), "smtpRecipientNotFound"),
        Arguments.of(new MailSendException(Map.of(new Object(), rejectedMessage)), "smtpRejected"));
  }

  @ParameterizedTest
  @MethodSource("deliveryFailures")
  void classifiesNestedSmtpFailuresWithoutLeakingDetails(MailException failure, String reason) {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(provider.getIfAvailable()).thenReturn(sender);
    when(provider.getObject()).thenReturn(sender);
    doThrow(failure).when(sender).send(any(SimpleMailMessage.class));
    RecoveryMailSender mail = new RecoveryMailSender(provider, "smtp.example.invalid", "from@example.invalid");
    Logger logger = (Logger) LoggerFactory.getLogger(RecoveryMailSender.class);
    ListAppender<ILoggingEvent> entries = new ListAppender<>();
    entries.start();
    logger.addAppender(entries);
    try {
      assertThatThrownBy(() -> mail.send("recipient@example.invalid", "123456", "bind"))
          .isInstanceOf(BizException.class).hasMessage("error.recovery." + reason).hasNoCause();
      assertThat(entries.list).hasSize(1);
      ILoggingEvent event = entries.list.getFirst();
      assertThat(event.getThrowableProxy()).isNull();
      assertThat(event.getFormattedMessage()).contains("reason=error.recovery." + reason)
          .doesNotContain("secret", "@", "123456", "RCPT TO", "DATA");
      if (reason.startsWith("smtpRecipient")) assertThat(event.getFormattedMessage()).contains("smtpStatus=550");
      if (reason.equals("smtpRejected")) assertThat(event.getFormattedMessage()).contains("smtpStatus=554");
    } finally {
      logger.detachAppender(entries);
      entries.stop();
    }
  }
}
