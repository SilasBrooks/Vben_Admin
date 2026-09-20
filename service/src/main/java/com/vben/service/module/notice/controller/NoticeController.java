package com.vben.service.module.notice.controller;

import com.vben.service.common.R;
import com.vben.service.common.idempotent.Idempotent;
import com.vben.service.module.notice.service.NoticeService;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
 * 站内通知接口。
 *
 * <p>列表/未读数/已读为「本人数据」操作（当前用户 id 取自登录态），登录即可访问；
 * 公告发布为管理动作，挂 Notice:Announce:Publish 权限码 + @Idempotent 防重复提交。
 */
@RestController
@Tag(name = "站内通知", description = "通知列表/未读数/标记已读/公告广播")
@RequestMapping("/notice")
@RequiredArgsConstructor
public class NoticeController {

  private final NoticeService noticeService;

  /** 通知分页列表（当前用户，create_time 倒序）：{total, items} */
  @Operation(summary = "通知分页列表", description = "当前登录用户的通知，按创建时间倒序")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNum,
      @RequestParam(defaultValue = "10") long pageSize) {
    return R.ok(noticeService.page(pageNum, pageSize, LoginUserHolder.require().getUserId()));
  }

  /** 未读通知条数：{count} */
  @Operation(summary = "未读数量", description = "当前登录用户的未读通知条数")
  @GetMapping("/unread-count")
  public R<Map<String, Object>> unreadCount() {
    return R.ok(Map.of("count", noticeService.unreadCount(LoginUserHolder.require().getUserId())));
  }

  /** 批量标记已读（仅限本人通知，service 侧再按 user_id 限定防越权） */
  @Operation(summary = "标记已读", description = "批量把本人通知置为已读并记录阅读时间")
  @PostMapping("/read")
  public R<Void> read(@Valid @RequestBody NoticeReadRequest body) {
    noticeService.markRead(LoginUserHolder.require().getUserId(), body.ids());
    return R.ok();
  }

  /** 全部标记已读 */
  @Operation(summary = "全部标记已读", description = "把本人所有未读通知置为已读")
  @PostMapping("/read-all")
  public R<Void> readAll() {
    noticeService.markAllRead(LoginUserHolder.require().getUserId());
    return R.ok();
  }

  /** 公告广播：按全员/部门/指定用户发布，返回实际发送人数 */
  @Operation(summary = "公告广播", description = "管理员按全员/部门/指定用户发布公告，复用站内通知通道（落库 + 实时推送）")
  @RequirePermission("Notice:Announce:Publish")
  @Idempotent(name = "notice:announce")
  @PostMapping("/announce")
  public R<Map<String, Object>> announce(@Valid @RequestBody AnnounceRequest body) {
    int count = noticeService.announce(body.title(), body.content(),
        body.targetType(), body.deptIds(), body.userIds());
    return R.ok(Map.of("count", count));
  }

  /** 标记已读请求体 */
  public record NoticeReadRequest(@NotEmpty(message = "ids 不能为空") List<Long> ids) {
  }

  /**
   * 公告发布请求体：targetType = all 全员 / dept 按部门 / user 按用户，
   * deptIds 与 userIds 按 targetType 择一必填（service 侧校验）。
   */
  public record AnnounceRequest(
      @NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题不能超过 100 字") String title,
      @Size(max = 500, message = "内容不能超过 500 字") String content,
      @NotBlank(message = "目标类型不能为空") String targetType,
      List<Long> deptIds,
      List<Long> userIds) {
  }
}
