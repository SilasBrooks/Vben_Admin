package com.vben.service.module.im.controller;

import com.vben.service.common.R;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.module.im.entity.SysMessage;
import com.vben.service.module.im.service.ImChatService;
import com.vben.service.security.LoginUserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IM 单聊接口。
 *
 * <p>全部为「本人数据」操作（当前用户 id 取自登录态），登录即可访问，
 * 不挂权限码，写法对齐 module/notice/NoticeController。
 * 发送消息用 @RateLimit 按用户限流防刷；不加 @Idempotent——聊天允许
 * 短时间连发相同内容，幂等键会误伤正常连发场景。
 */
@RestController
@Tag(name = "IM单聊", description = "联系人/会话列表/历史消息/发送/已读（登录即可访问）")
@RequestMapping("/im")
@RequiredArgsConstructor
public class ImChatController {

  private final ImChatService imChatService;

  /** 历史消息最大单页条数 */
  private static final int MAX_PAGE_SIZE = 50;

  /** 可发起聊天的联系人（启用用户，除自己） */
  @Operation(summary = "联系人列表", description = "全部启用用户（除自己），仅暴露 id/用户名/昵称")
  @GetMapping("/peers")
  public R<List<Map<String, Object>>> peers() {
    return R.ok(imChatService.peers(LoginUserHolder.require().getUserId()));
  }

  /** 会话列表（含最后一条消息与未读数，最近活跃在前） */
  @Operation(summary = "会话列表", description = "按聊天对方分组，含对方信息、最后一条消息与未读数")
  @GetMapping("/conversations")
  public R<List<Map<String, Object>>> conversations() {
    return R.ok(imChatService.conversations(LoginUserHolder.require().getUserId()));
  }

  /** 历史消息（id 游标分页，时间正序返回） */
  @Operation(summary = "历史消息", description = "与指定对方的私聊记录；beforeId 为游标（首页不传），倒序取后正序返回")
  @GetMapping("/messages")
  public R<List<SysMessage>> messages(
      @RequestParam Long peerId,
      @RequestParam(required = false) Long beforeId,
      @RequestParam(defaultValue = "20") int pageSize) {
    int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    return R.ok(imChatService.messages(LoginUserHolder.require().getUserId(),
        peerId, beforeId, size));
  }

  /** 发送消息（落库 + 实时推送给对方，按用户限流） */
  @Operation(summary = "发送消息", description = "落库为准并实时推送；对方停用/不存在或给自己发送将报错")
  @PostMapping("/messages")
  @RateLimit(name = "im:send", limit = 60, windowSeconds = 60, scope = RateLimit.Scope.USER)
  public R<Map<String, Object>> send(@Valid @RequestBody ImSendRequest body) {
    SysMessage saved = imChatService.send(LoginUserHolder.require().getUserId(),
        body.receiverId(), body.content());
    return R.ok(Map.of("message", saved));
  }

  /** 把对方发来的未读消息标记已读，并推回执给对方 */
  @Operation(summary = "标记已读", description = "把指定对方发来的本人未读消息置为已读并向对方推回执")
  @PostMapping("/messages/read")
  public R<Map<String, Object>> markRead(@Valid @RequestBody ImReadRequest body) {
    long updated = imChatService.markPeerRead(LoginUserHolder.require().getUserId(), body.peerId());
    return R.ok(Map.of("updated", updated));
  }

  /** 发送消息请求体 */
  public record ImSendRequest(
      @NotNull(message = "{error.im.receiver.blank}") Long receiverId,
      @NotBlank(message = "{error.im.content.blank}")
          @Size(max = 2000, message = "{error.im.content.max}") String content) {
  }

  /** 标记已读请求体 */
  public record ImReadRequest(@NotNull(message = "{error.im.peer.blank}") Long peerId) {
  }
}
