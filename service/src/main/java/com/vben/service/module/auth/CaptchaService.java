package com.vben.service.module.auth;

import com.vben.service.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端图形验证码：生成（AWT 绘制）→ 发放 → 一次性校验。
 *
 * <p>有效期默认 120 秒（vben.captcha.ttl-seconds）；校验即销毁，防重放。
 * 验证码池有规模上限并惰性清理过期项，防止内存无限增长。
 * 内存态属单实例语义，多实例部署需迁移集中式存储（如 Redis）。
 */
@Slf4j
@Service
public class CaptchaService {

  /** 字符集：剔除 0/o、1/l/i 等易混字符 */
  private static final String CHARS = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
  private static final int CODE_LENGTH = 4;
  private static final int MAX_POOL = 10_000;

  private final SecureRandom random = new SecureRandom();
  private final ConcurrentHashMap<String, Entry> pool = new ConcurrentHashMap<>();

  /** 有效期（秒） */
  @Value("${vben.captcha.ttl-seconds:120}")
  private int ttlSeconds;

  /** 开发联调开关：响应中回显验证码明文（仅 dev profile 开启，生产必须关闭） */
  @Value("${vben.captcha.echo-enabled:false}")
  private boolean echoEnabled;

  /**
   * 生成一张验证码：返回 captchaId、base64 PNG 图片；echo 开启时附带 devCode 明文。
   */
  public synchronized CaptchaImage generate() {
    if (pool.size() >= MAX_POOL) {
      long now = System.currentTimeMillis();
      pool.entrySet().removeIf(e -> now >= e.getValue().expiresAt());
      if (pool.size() >= MAX_POOL) {
        throw BizException.tooManyRequests("验证码获取过于频繁，请稍后再试");
      }
    }
    String code = randomCode();
    String id = UUID.randomUUID().toString().replace("-", "");
    pool.put(id, new Entry(code, System.currentTimeMillis() + ttlSeconds * 1000L));
    return new CaptchaImage(id, draw(code), echoEnabled ? code : null);
  }

  /** 校验并销毁：id 不存在 / 已过期 / 不匹配均返回 false（忽略大小写） */
  public boolean verify(String captchaId, String input) {
    if (captchaId == null || captchaId.isBlank() || input == null || input.isBlank()) {
      return false;
    }
    Entry entry = pool.remove(captchaId);
    if (entry == null || System.currentTimeMillis() > entry.expiresAt()) {
      return false;
    }
    return entry.code().equalsIgnoreCase(input.trim());
  }

  private String randomCode() {
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
    }
    return sb.toString();
  }

  /** AWT 绘制 4 位字符 + 干扰线，输出 base64 PNG（headless 安全） */
  private String draw(String code) {
    int width = 132;
    int height = 44;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      // 背景
      g.setColor(new Color(0xF2, 0xF3, 0xF5));
      g.fillRect(0, 0, width, height);
      // 干扰线
      g.setStroke(new BasicStroke(1.2f));
      for (int i = 0; i < 4; i++) {
        g.setColor(randomColor(120, 200));
        g.drawLine(random.nextInt(width), random.nextInt(height),
            random.nextInt(width), random.nextInt(height));
      }
      // 逐字符绘制：随机颜色、轻微旋转
      Font font = new Font(Font.SANS_SERIF, Font.BOLD, 30);
      g.setFont(font);
      int charWidth = (width - 20) / CODE_LENGTH;
      for (int i = 0; i < CODE_LENGTH; i++) {
        g.setColor(randomColor(20, 110));
        double theta = (random.nextInt(40) - 20) * Math.PI / 180;
        int x = 10 + i * charWidth;
        int y = 32 + random.nextInt(4);
        g.rotate(theta, x + charWidth / 2.0, y - 10);
        g.drawString(String.valueOf(code.charAt(i)), x, y);
        g.rotate(-theta, x + charWidth / 2.0, y - 10);
      }
    } finally {
      g.dispose();
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", out);
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    } catch (Exception e) {
      log.error("验证码图片生成失败", e);
      throw new IllegalStateException("验证码图片生成失败", e);
    }
  }

  private Color randomColor(int min, int max) {
    int range = max - min;
    return new Color(min + random.nextInt(range), min + random.nextInt(range),
        min + random.nextInt(range));
  }

  /** 验证码条目：明文 + 过期时间 */
  private record Entry(String code, long expiresAt) {
  }

  /** 对外发放的验证码载荷 */
  public record CaptchaImage(String captchaId, String image, String devCode) {
  }
}
