package com.vben.service.module.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.common.redis.RedisKeys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailCodeService {
  private static final Duration VALIDITY = Duration.ofMinutes(5);
  static final DefaultRedisScript<String> VERIFY = new DefaultRedisScript<>("""
      local value = redis.call('GET', KEYS[1])
      if not value then return nil end
      local data = cjson.decode(value)
      if data.digest == ARGV[1] then
        redis.call('DEL', KEYS[1])
        return value
      end
      data.attempts = data.attempts + 1
      if data.attempts >= 5 then
        redis.call('DEL', KEYS[1])
      else
        redis.call('SET', KEYS[1], cjson.encode(data), 'XX', 'KEEPTTL')
      end
      return nil
      """, String.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final RecoveryMailSender mail;
  private final SecureRandom random = new SecureRandom();

  /** 对真实和不存在账号一视同仁，避免通过冷却行为探测账号。 */
  public void reserve(String purpose, String subject) {
    Boolean claimed = redis.opsForValue().setIfAbsent(
        RedisKeys.emailCooldown(purpose, digest(subject)), "1", Duration.ofSeconds(60));
    if (!Boolean.TRUE.equals(claimed)) throw BizException.tooManyRequests("error.recovery.cooldown");
  }

  public String issue(String purpose, Long userId, String email, long version) {
    String id = newId();
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    String key = RedisKeys.emailCode(purpose, id);
    Challenge challenge = new Challenge(userId.toString(), email, version, digest(id + ":" + code), 0);
    try {
      redis.opsForValue().set(key, json.writeValueAsString(challenge), VALIDITY);
      mail.send(email, code, purpose);
      return id;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode recovery challenge");
    } catch (RuntimeException e) {
      redis.delete(key);
      throw e;
    }
  }

  public Challenge consume(String purpose, String id, String code) {
    String value = redis.execute(VERIFY, List.of(RedisKeys.emailCode(purpose, id)), digest(id + ":" + code));
    if (value == null) throw BizException.badRequest("error.recovery.code");
    try {
      return json.readValue(value, Challenge.class);
    } catch (JsonProcessingException e) {
      throw BizException.badRequest("error.recovery.code");
    }
  }

  public static String newId() { return UUID.randomUUID().toString().replace("-", ""); }

  private static String digest(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record Challenge(String userId, String email, long version, String digest, int attempts) {}
}
