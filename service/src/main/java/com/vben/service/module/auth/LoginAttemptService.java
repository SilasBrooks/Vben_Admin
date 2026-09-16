package com.vben.service.module.auth;

import com.vben.service.common.BizException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定：按【用户名】与【客户端 IP】两个维度独立计数。
 *
 * <p>规则：15 分钟滑动统计窗口内失败达 {@link #MAX_FAILURES} 次 → 锁定至窗口结束；
 * 锁定期间无论凭据正确与否一律拒绝（429 + 剩余等待时间）；登录成功清零对应计数。
 * 验证码失败不计入（防手误误锁），由接口限流兜底。
 * 内存态属单实例语义，多实例部署需迁移集中式存储（如 Redis）。
 */
@Service
public class LoginAttemptService {

  /** 触发锁定的失败次数 */
  private static final int MAX_FAILURES = 5;
  /** 统计窗口 = 锁定时长（毫秒） */
  private static final long WINDOW_MS = 15 * 60_000L;

  private final ConcurrentHashMap<String, State> byUsername = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, State> byIp = new ConcurrentHashMap<>();

  /** 登录前检查：若任一维度已锁定则抛 429（含剩余等待分钟数） */
  public void checkLocked(String username, String ip) {
    long now = System.currentTimeMillis();
    checkOne(byUsername, username.toLowerCase(), now, "该账号");
    checkOne(byIp, ip, now, "当前 IP");
  }

  /** 记录一次登录失败（凭据错误） */
  public void registerFailure(String username, String ip) {
    long now = System.currentTimeMillis();
    bump(byUsername, username.toLowerCase(), now);
    bump(byIp, ip, now);
  }

  /** 登录成功：清零两个维度的计数 */
  public void onSuccess(String username, String ip) {
    byUsername.remove(username.toLowerCase());
    byIp.remove(ip);
  }

  private void checkOne(ConcurrentHashMap<String, State> map, String key, long now, String label) {
    State state = map.get(key);
    if (state == null) {
      return;
    }
    long elapsed = now - state.windowStart();
    if (state.count() >= MAX_FAILURES && elapsed < WINDOW_MS) {
      long remainMinutes = Math.max(1, (WINDOW_MS - elapsed + 59_999) / 60_000);
      throw BizException.tooManyRequests(
          label + "登录失败次数过多，已临时锁定，请约 " + remainMinutes + " 分钟后再试");
    }
  }

  private void bump(ConcurrentHashMap<String, State> map, String key, long now) {
    map.compute(key, (k, old) -> {
      // 窗口已过期则重新起窗
      if (old == null || now - old.windowStart() >= WINDOW_MS) {
        return new State(1, now);
      }
      return new State(old.count() + 1, old.windowStart());
    });
  }

  /** 某个维度的失败状态：计数 + 窗口起点（首个失败时间） */
  private record State(int count, long windowStart) {
  }
}
