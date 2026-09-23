package com.vben.service.module.system.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import com.vben.service.security.LoginUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * PermissionCacheService 单测（纯 Mockito，无容器/Redis 依赖）。
 *
 * <p>覆盖：序列化往返（put 写入的 JSON 能被 get 还原）、miss/Redis 异常降级为 null、
 * 写入带 TTL 兜底、三级失效（用户级删单键 / 角色级删角色下用户键 / 菜单级 SCAN 攒批删）。
 */
class PermissionCacheServiceTest {

  private StringRedisTemplate redis;
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
  private SysUserRoleMapper userRoleMapper;
  private PermissionCacheService service;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    userRoleMapper = mock(SysUserRoleMapper.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    service = new PermissionCacheService(redis, new ObjectMapper(), userRoleMapper);
  }

  private static LoginUser user(Long userId) {
    return new LoginUser(userId, "jack", List.of("admin", "user"),
        Set.of("System:User:List"), 0L);
  }

  // ---------------- get ----------------

  @Test
  void get_hit_returnsDeserializedUser() throws Exception {
    String json = new ObjectMapper().writeValueAsString(user(7L));
    when(valueOps.get("vben:perms:7")).thenReturn(json);

    LoginUser result = service.get(7L);

    assertEquals(7L, result.getUserId());
    assertEquals("jack", result.getUsername());
    assertEquals(List.of("admin", "user"), result.getRoles());
    assertEquals(Set.of("System:User:List"), result.getPermissions());
    // 命中缓存：完全不触碰装配查询（回源才查 sys_user_role）
    verifyNoInteractions(userRoleMapper);
  }

  @Test
  void get_miss_returnsNull() {
    when(valueOps.get(anyString())).thenReturn(null);

    assertNull(service.get(1L));
  }

  @Test
  void get_redisError_degradesToNullWithoutThrowing() {
    when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

    assertNull(service.get(1L));
  }

  // ---------------- put ----------------

  @Test
  void put_writesSnapshotJsonWithTtl() {
    service.put(user(7L));

    ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
    verify(valueOps).set(key.capture(), json.capture(), ttl.capture());
    assertEquals("vben:perms:7", key.getValue());
    assertEquals(Duration.ofMinutes(5), ttl.getValue());
    assertTrue(json.getValue().contains("\"username\":\"jack\""));
  }

  @Test
  void put_redisError_swallowed() {
    doThrow(new RuntimeException("redis down"))
        .when(valueOps).set(anyString(), anyString(), any(Duration.class));

    assertDoesNotThrow(() -> service.put(user(7L)));
  }

  // ---------------- evict（用户级） ----------------

  @Test
  void evict_deletesUserKey() {
    service.evict(5L);

    verify(redis).delete("vben:perms:5");
  }

  @Test
  void evict_redisError_swallowed() {
    when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));

    assertDoesNotThrow(() -> service.evict(5L));
  }

  // ---------------- evictByRole（角色级） ----------------

  @Test
  void evictByRole_deletesKeysOfAllUsersUnderRole() {
    when(userRoleMapper.selectUserIdsByRoleId(2L)).thenReturn(List.of(1L, 3L));

    service.evictByRole(2L);

    verify(redis).delete("vben:perms:1");
    verify(redis).delete("vben:perms:3");
  }

  // ---------------- evictAll（菜单级，SCAN 攒批删） ----------------

  @Test
  void evictAll_scansAllPermsKeysAndFlushesRemainder() {
    Cursor<String> cursor = mock(Cursor.class);
    when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
    int[] seq = {0};
    when(cursor.next()).thenAnswer(inv -> "vben:perms:" + (seq[0]++));
    // 501 个 key：首批攒满 500 刷一次，余 1 个在游标结束后补刷
    int[] hasCalls = {0};
    when(cursor.hasNext()).thenAnswer(inv -> hasCalls[0]++ < 501);
    // 服务复用同一 batch 列表（delete 后 clear），captor 只能拿到活引用，
    // 必须在调用时刻记录批次大小
    List<Integer> batchSizes = new ArrayList<>();
    when(redis.delete(anyCollection())).thenAnswer(inv -> {
      batchSizes.add(((Collection<String>) inv.getArgument(0)).size());
      return 0L;
    });

    service.evictAll();

    assertEquals(List.of(500, 1), batchSizes);
  }

  @Test
  void evictAll_scanError_swallowed() {
    when(redis.scan(any(ScanOptions.class))).thenThrow(new RuntimeException("redis down"));

    assertDoesNotThrow(() -> service.evictAll());
  }
}
