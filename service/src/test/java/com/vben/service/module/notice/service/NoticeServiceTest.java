package com.vben.service.module.notice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.vben.service.common.BizException;
import com.vben.service.module.notice.entity.SysNotice;
import com.vben.service.module.notice.mapper.SysNoticeMapper;
import com.vben.service.module.notice.websocket.NoticeWebSocketHandler;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * NoticeService 公告广播与通知单测（纯 Mockito，无容器/数据库依赖）。
 *
 * <p>覆盖：公告目标解析（全员/按部门/按用户/非法类型/缺参）、msg_type 落库区分、
 * WebSocket 推送失败不影响落库、未读数统计。
 */
class NoticeServiceTest {

  private SysNoticeMapper noticeMapper;
  private NoticeWebSocketHandler webSocketHandler;
  private SysUserMapper userMapper;
  private NoticeService service;

  @BeforeEach
  void setUp() {
    noticeMapper = mock(SysNoticeMapper.class);
    webSocketHandler = mock(NoticeWebSocketHandler.class);
    userMapper = mock(SysUserMapper.class);
    service = new NoticeService(noticeMapper, webSocketHandler, userMapper);
    // MyBatis-Plus 的 .select(SFunction) 会急切解析 lambda 列名，
    // 纯单测环境无 mapper 初始化，需手动注册实体 TableInfo（幂等）
    MapperBuilderAssistant assistant =
        new MapperBuilderAssistant(new MybatisConfiguration(), "");
    TableInfoHelper.initTableInfo(assistant, SysUser.class);
    TableInfoHelper.initTableInfo(assistant, SysNotice.class);
  }

  private static BizException biz(Runnable action) {
    return org.junit.jupiter.api.Assertions.assertThrows(BizException.class, action::run);
  }

  private static SysUser user(long id) {
    SysUser u = new SysUser();
    u.setId(id);
    return u;
  }

  // ---------------- announce 目标解析 ----------------

  @Test
  void announce_all_sendsToEveryResolvedUserWithAnnouncementType() {
    when(userMapper.selectList(any())).thenReturn(List.of(user(1L), user(3L)));

    int count = service.announce("升级公告", "内容", "all", null, null);

    assertEquals(2, count);
    ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
    verify(noticeMapper, times(2)).insert(captor.capture());
    List<SysNotice> inserted = captor.getAllValues();
    assertEquals(1L, inserted.get(0).getUserId());
    assertEquals(3L, inserted.get(1).getUserId());
    assertEquals("announcement", inserted.get(0).getMsgType());
    assertEquals(0, inserted.get(0).getReadFlag());
    verify(webSocketHandler, times(2)).sendToUser(anyLong(), any());
  }

  @Test
  void announce_byDept_queriesWithDeptFilter() {
    when(userMapper.selectList(any())).thenReturn(List.of(user(4L)));

    int count = service.announce("盘点通知", "内容", "dept", List.of(4L), null);

    assertEquals(1, count);
    verify(noticeMapper).insert(any(SysNotice.class));
    verify(webSocketHandler).sendToUser(eq(4L), any());
  }

  @Test
  void announce_byUser_queriesWithUserFilter() {
    when(userMapper.selectList(any())).thenReturn(List.of(user(3L)));

    int count = service.announce("定向通知", "内容", "user", null, List.of(3L));

    assertEquals(1, count);
    verify(noticeMapper).insert(any(SysNotice.class));
  }

  @Test
  void announce_deptWithoutIds_rejected() {
    BizException ex = biz(() -> service.announce("t", "c", "dept", null, null));
    assertEquals(400, ex.getStatus());
    assertEquals("error.notice.dept.required", ex.getMessage());
  }

  @Test
  void announce_userWithoutIds_rejected() {
    BizException ex = biz(() -> service.announce("t", "c", "user", null, List.of()));
    assertEquals(400, ex.getStatus());
    assertEquals("error.notice.user.required", ex.getMessage());
  }

  @Test
  void announce_invalidType_rejected() {
    BizException ex = biz(() -> service.announce("t", "c", "bogus", null, null));
    assertEquals(400, ex.getStatus());
    assertEquals("error.notice.target.invalid", ex.getMessage());
    verify(noticeMapper, never()).insert(any(SysNotice.class));
  }

  // ---------------- send 基础行为 ----------------

  @Test
  void send_keepsSecurityTypeForExistingCallers() {
    service.send(2L, "账号已被管理员停用", "如有疑问请联系管理员。");

    ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
    verify(noticeMapper).insert(captor.capture());
    assertEquals("security", captor.getValue().getMsgType());
    assertEquals(2L, captor.getValue().getUserId());
  }

  @Test
  void send_websocketFailure_stillPersists() {
    doThrow(new RuntimeException("offline")).when(webSocketHandler).sendToUser(anyLong(), any());

    service.send(2L, "t", "c");

    // 推送失败不影响落库（离线属常态）
    verify(noticeMapper).insert(any(SysNotice.class));
  }

  // ---------------- 未读数 ----------------

  @Test
  void unreadCount_returnsMapperCount() {
    when(noticeMapper.selectCount(any())).thenReturn(3L);
    assertEquals(3L, service.unreadCount(2L));
  }
}
