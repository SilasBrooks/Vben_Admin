package com.vben.service.common.redis;

/**
 * Redis key 命名统一管理：全部业务 key 以 vben: 为前缀，集中在此避免魔法字符串散落。
 */
public final class RedisKeys {

  /** 全局前缀 */
  public static final String PREFIX = "vben:";

  private RedisKeys() {
  }

  public static String emailCode(String purpose, String challengeId) {
    return PREFIX + "email:code:" + purpose + ":" + challengeId;
  }

  public static String emailCooldown(String purpose, String subjectHash) {
    return PREFIX + "email:cooldown:" + purpose + ":" + subjectHash;
  }

  /** 图形验证码：vben:captcha:{captchaId}，值为明文码，TTL = 验证码有效期 */
  public static String captcha(String captchaId) {
    return PREFIX + "captcha:" + captchaId;
  }

  /** 登录失败计数：vben:login:fail:{dimension}:{key}，dimension = user / ip */
  public static String loginFail(String dimension, String key) {
    return PREFIX + "login:fail:" + dimension + ":" + key;
  }

  /** 限流固定窗口计数：vben:ratelimit:{name}:{principal} */
  public static String rateLimit(String name, String principal) {
    return PREFIX + "ratelimit:" + name + ":" + principal;
  }

  /** 防重复提交占位：vben:idempotent:{name}:{principal}，TTL = 幂等窗口 */
  public static String idempotent(String name, String principal) {
    return PREFIX + "idempotent:" + name + ":" + principal;
  }

  /** 用户 token 版本号：vben:token:ver:{userId}，无键视为 0 */
  public static String tokenVer(Long userId) {
    return PREFIX + "token:ver:" + userId;
  }

  /** 在线会话：vben:online:{userId}，值为会话信息 JSON */
  public static String online(Long userId) {
    return PREFIX + "online:" + userId;
  }

  /** 在线会话扫描模式（列表用；量大时应换 SCAN 游标） */
  public static String onlinePattern() {
    return PREFIX + "online:*";
  }

  /** AI 多步计划暂停态（遇高危步骤二次确认）：vben:ai:plan:{planId}，TTL 10 分钟 */
  public static String aiPlan(String planId) {
    return PREFIX + "ai:plan:" + planId;
  }

  /** 用户权限快照：vben:perms:{userId}，值为权限快照 JSON，TTL 5 分钟兜底 */
  public static String perms(Long userId) {
    return PREFIX + "perms:" + userId;
  }

  /** 权限快照全量清理扫描模式（菜单级变更时 SCAN 逐批删除） */
  public static String permsPattern() {
    return PREFIX + "perms:*";
  }

  /** WebSocket 推送跨实例广播频道（pub/sub channel，非 key）：信封 {kind, userId, payload} */
  public static String wsPush() {
    return PREFIX + "ws:push";
  }
}
