package com.vben.service.module.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.redis.RedisKeys;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import com.vben.service.security.LoginUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 认证权限快照缓存：LoginUser（角色键 + 权限码集合）按 userId 缓存于 Redis，
 * 让 JwtAuthFilter 命中时跳过「用户 + 角色键 + 四表 JOIN 菜单」三条装配 SQL。
 *
 * <p>正确性靠三级主动失效（TTL 5 分钟仅兜底漏网路径）：
 * <ul>
 *   <li>用户级变更（分配角色/禁用/重置密码/删除）→ {@link #evict}</li>
 *   <li>角色级变更（改角色/重分配菜单）→ {@link #evictByRole}</li>
 *   <li>菜单级变更（增删改）→ {@link #evictAll}（SCAN 逐批删，避免 KEYS 阻塞）</li>
 * </ul>
 *
 * <p>降级语义：缓存是只读派生数据的加速层，Redis 异常一律降级为回源查库
 * （与无缓存时语义完全一致，不构成安全回退）；token 版本号校验的 fail-closed 不在本层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

  /** 快照兜底过期：即使失效漏接，权限变更最多延迟该时长生效 */
  private static final Duration TTL = Duration.ofMinutes(5);

  /** SCAN 单批清理的 key 数量上限（攒批删除，减少往返） */
  private static final int SCAN_BATCH = 500;

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final SysUserRoleMapper userRoleMapper;

  /** 读取权限快照；miss 或 Redis 异常（降级）返回 null，由调用方回源查库 */
  public LoginUser get(Long userId) {
    try {
      String json = redis.opsForValue().get(RedisKeys.perms(userId));
      return json == null ? null : objectMapper.readValue(json, LoginUser.class);
    } catch (Exception e) {
      log.warn("权限快照读取失败，降级直查库: userId={}, cause={}", userId, e.getMessage());
      return null;
    }
  }

  /** 回填快照；Redis 异常仅记日志（不影响请求结果） */
  public void put(LoginUser user) {
    try {
      redis.opsForValue().set(RedisKeys.perms(user.getUserId()),
          objectMapper.writeValueAsString(user), TTL);
    } catch (Exception e) {
      log.warn("权限快照写入失败（不影响请求）: userId={}, cause={}",
          user.getUserId(), e.getMessage());
    }
  }

  /** 用户级失效：分配角色 / 禁用 / 重置密码 / 删除用户后调用 */
  public void evict(Long userId) {
    try {
      redis.delete(RedisKeys.perms(userId));
    } catch (Exception e) {
      log.warn("权限快照失效失败（TTL 兜底）: userId={}, cause={}", userId, e.getMessage());
    }
  }

  /** 角色级失效：改角色（含 roleKey/状态）/ 重分配菜单授权后调用 */
  public void evictByRole(Long roleId) {
    try {
      for (Long userId : userRoleMapper.selectUserIdsByRoleId(roleId)) {
        redis.delete(RedisKeys.perms(userId));
      }
    } catch (Exception e) {
      log.warn("角色级权限快照失效失败（TTL 兜底）: roleId={}, cause={}", roleId, e.getMessage());
    }
  }

  /** 菜单级失效：菜单增删改影响任意角色，全量清理（低频操作，SCAN 攒批删除） */
  public void evictAll() {
    try {
      ScanOptions options = ScanOptions.scanOptions()
          .match(RedisKeys.permsPattern()).count(SCAN_BATCH).build();
      List<String> batch = new ArrayList<>();
      try (Cursor<String> cursor = redis.scan(options)) {
        while (cursor.hasNext()) {
          batch.add(cursor.next());
          if (batch.size() >= SCAN_BATCH) {
            redis.delete(batch);
            batch.clear();
          }
        }
      }
      if (!batch.isEmpty()) {
        redis.delete(batch);
      }
    } catch (Exception e) {
      log.warn("权限快照全量清理失败（TTL 兜底）: cause={}", e.getMessage());
    }
  }
}
