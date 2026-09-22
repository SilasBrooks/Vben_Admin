package com.vben.service.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
import com.vben.service.module.im.entity.SysMessage;
import com.vben.service.module.im.service.ImChatService;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.LoginUserHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * IM 消息聊天 AI 工具：联系人、会话、历史查询与消息发送。
 * 与 IM REST 接口权限语义一致（登录即可，无权限码）；
 * 发送为普通写操作，走确认卡片，落库复用 ImChatService 既有校验。
 */
@Component
@RequiredArgsConstructor
public class AiImTools {

  /** 默认历史条数（与 REST 首页一致） */
  private static final int HISTORY_DEFAULT = 20;

  /** 历史条数上限（与 REST pageSize 上限一致） */
  private static final int HISTORY_MAX = 50;

  private final ImChatService imChatService;
  private final SysUserMapper userMapper;
  private final ObjectMapper objectMapper;

  @AiAgentTool(name = "query_im_contacts", title = "查询联系人", kind = AiToolKind.QUERY,
      description = "查询消息聊天中可发起会话的全部启用用户（当前用户除外）。"
          + "结果包含登录名 username、昵称 nickname，供确定消息接收人。")
  public AiToolResult queryImContacts() {
    Long me = LoginUserHolder.require().getUserId();
    List<Map<String, Object>> peers = imChatService.peers(me);
    return AiToolResult.success(json(Map.of("total", peers.size(), "contacts", peers)));
  }

  @AiAgentTool(name = "query_my_conversations", title = "查询我的会话", kind = AiToolKind.QUERY,
      description = "查询当前用户在消息聊天中的会话列表（最近活跃在前）。"
          + "结果包含对方登录名与昵称、最后一条消息内容与时间、未读数。")
  public AiToolResult queryMyConversations() {
    Long me = LoginUserHolder.require().getUserId();
    List<Map<String, Object>> conversations = imChatService.conversations(me);
    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, Object> row : conversations) {
      Map<String, Object> m = new LinkedHashMap<>();
      @SuppressWarnings("unchecked")
      Map<String, Object> peer = (Map<String, Object>) row.get("peer");
      m.put("username", peer.get("username"));
      m.put("nickname", peer.get("nickname"));
      m.put("lastMessage", ((Map<?, ?>) row.get("lastMessage")).get("content"));
      m.put("unreadCount", row.get("unreadCount"));
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("total", items.size(), "conversations", items)));
  }

  @AiAgentTool(name = "query_chat_history", title = "查询聊天记录", kind = AiToolKind.QUERY,
      description = "查询当前用户与指定用户的私聊记录（时间正序，最近最多 50 条）。"
          + "username 为对方登录名。结果包含方向（我发/对方发）、内容、是否已读、时间。")
  public AiToolResult queryChatHistory(
      @AiToolParam(value = "对方登录名", required = true) String username,
      @AiToolParam("条数，默认 20，最大 50") Integer limit) {
    Long peerId = resolveUserId(username);
    int size = limit == null || limit <= 0 ? HISTORY_DEFAULT : Math.min(limit, HISTORY_MAX);
    Long me = LoginUserHolder.require().getUserId();
    List<SysMessage> records = imChatService.messages(me, peerId, null, size);
    SysUser peer = userMapper.selectById(peerId);
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysMessage msg : records) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("direction", msg.getSenderId().equals(me) ? "me_to_peer" : "peer_to_me");
      m.put("content", msg.getContent());
      m.put("read", msg.getReadFlag() != null && msg.getReadFlag() == 1);
      m.put("createTime", msg.getCreateTime());
      items.add(m);
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("peerUsername", username);
    data.put("peerNickname", peer == null ? null : peer.getNickname());
    data.put("total", items.size());
    data.put("messages", items);
    return AiToolResult.success(json(data));
  }

  @AiAgentTool(name = "send_chat_message", title = "发送消息", kind = AiToolKind.WRITE,
      description = "给指定用户发送一条站内私聊消息（对方在线时实时送达）。发送前必须与用户确认接收人与消息内容。")
  public AiToolResult sendChatMessage(
      @AiToolParam(value = "接收人登录名", required = true) String username,
      @AiToolParam(value = "消息内容", required = true) String content) {
    Long receiverId = resolveUserId(username);
    Long me = LoginUserHolder.require().getUserId();
    SysMessage message = imChatService.send(me, receiverId, content);
    SysUser receiver = userMapper.selectById(receiverId);
    String peerText = receiver == null || receiver.getNickname() == null
        || receiver.getNickname().isBlank()
        ? username
        : receiver.getNickname() + "（" + username + "）";
    String summary = "已把消息发送给「" + peerText + "」：" + content;
    return AiToolResult.created(json(Map.of(
        "messageId", message.getId(),
        "receiver", username)), summary);
  }

  /** 登录名 → 用户 id：精确匹配唯一用户，0 个或多个即报错（不猜测） */
  private Long resolveUserId(String username) {
    if (username == null || username.isBlank()) {
      throw BizException.badRequest("error.ai.user.notFound", username == null ? "" : username);
    }
    List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getUsername, username.trim()));
    if (users.isEmpty()) {
      throw BizException.badRequest("error.ai.user.notFound", username.trim());
    }
    if (users.size() > 1) {
      throw BizException.badRequest("error.ai.user.ambiguous", username.trim());
    }
    return users.get(0).getId();
  }

  private String json(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException("工具结果序列化失败", e);
    }
  }
}
