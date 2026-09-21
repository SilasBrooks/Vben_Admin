package com.vben.service.module.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vben.service.common.BizException;
import com.vben.service.module.im.entity.SysMessage;
import com.vben.service.module.im.mapper.SysMessageMapper;
import com.vben.service.module.im.websocket.ImWebSocketHandler;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * IM 单聊服务：落库为准，WebSocket 推送为实时通道。
 *
 * <p>发送消息先落库再推送，推送失败（对方离线）仅告警不影响落库；
 * 标记已读后向对方推 read 回执帧，对方在线时即时把气泡置为已读。
 * 全部操作以当前登录用户 id 限定「本人数据」，无越权面。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImChatService {

  /** 未读标记：0 */
  private static final int READ_FLAG_UNREAD = 0;

  /** 已读标记：1 */
  private static final int READ_FLAG_READ = 1;

  /** 用户状态：0 启用 */
  private static final int USER_STATUS_ENABLED = 0;

  private final SysMessageMapper messageMapper;
  private final SysUserMapper userMapper;
  private final ImWebSocketHandler webSocketHandler;

  /**
   * 可发起聊天的联系人：全部启用用户（除自己），仅暴露非敏感字段。
   *
   * @return [{id, username, nickname}]
   */
  public List<Map<String, Object>> peers(Long userId) {
    List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
        .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname)
        .eq(SysUser::getStatus, USER_STATUS_ENABLED)
        .ne(SysUser::getId, userId)
        .orderByAsc(SysUser::getUsername));
    return users.stream()
        .map(u -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("id", u.getId());
          row.put("username", u.getUsername());
          row.put("nickname", u.getNickname());
          return row;
        })
        .collect(Collectors.toList());
  }

  /**
   * 会话列表：按聊天对方分组，含对方信息、最后一条消息与未读数，最近活跃在前。
   *
   * @return [{peer:{id,username,nickname}, lastMessage:{id,senderId,content,createTime}, unreadCount}]
   */
  public List<Map<String, Object>> conversations(Long userId) {
    List<Map<String, Object>> summaries = messageMapper.selectConversationSummaries(userId);
    if (summaries.isEmpty()) {
      return Collections.emptyList();
    }
    // 回查每组的最后一条消息与对方用户信息（批量，避免逐会话 N+1）
    List<Long> lastIds = summaries.stream()
        .map(s -> ((Number) s.get("last_message_id")).longValue())
        .toList();
    Map<Long, SysMessage> lastById = messageMapper.selectBatchIds(lastIds).stream()
        .collect(Collectors.toMap(SysMessage::getId, Function.identity()));
    List<Long> peerIds = summaries.stream()
        .map(s -> ((Number) s.get("peer_id")).longValue())
        .toList();
    Map<Long, SysUser> peerById = userMapper.selectBatchIds(peerIds).stream()
        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

    List<Map<String, Object>> result = new ArrayList<>(summaries.size());
    for (Map<String, Object> summary : summaries) {
      Long peerId = ((Number) summary.get("peer_id")).longValue();
      SysUser peer = peerById.get(peerId);
      SysMessage last = lastById.get(((Number) summary.get("last_message_id")).longValue());
      if (peer == null || last == null) {
        // 对方已被物理删除等异常数据：跳过该会话，不阻塞其余展示
        continue;
      }
      Map<String, Object> peerMap = new LinkedHashMap<>();
      peerMap.put("id", peer.getId());
      peerMap.put("username", peer.getUsername());
      peerMap.put("nickname", peer.getNickname());

      Map<String, Object> lastMap = new LinkedHashMap<>();
      lastMap.put("id", last.getId());
      lastMap.put("senderId", last.getSenderId());
      lastMap.put("content", last.getContent());
      lastMap.put("createTime", last.getCreateTime());

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("peer", peerMap);
      row.put("lastMessage", lastMap);
      row.put("unreadCount", ((Number) summary.get("unread_count")).longValue());
      result.add(row);
    }
    return result;
  }

  /**
   * 历史消息：与指定对方的私聊记录，id 游标倒序取 pageSize 条后翻转为时间正序返回。
   *
   * @param me       当前用户 id
   * @param peerId   对方用户 id
   * @param beforeId 游标：取 id 小于该值的消息（首页传 null）
   * @param pageSize 每页条数（1~50）
   * @return 时间正序的消息列表
   */
  public List<SysMessage> messages(Long me, Long peerId, Long beforeId, int pageSize) {
    requirePeerExists(peerId);
    List<SysMessage> records = messageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
        .and(w -> w.and(x -> x.eq(SysMessage::getSenderId, me)
                .eq(SysMessage::getReceiverId, peerId)
                .eq(SysMessage::getSenderDeleted, 0))
            .or(x -> x.eq(SysMessage::getSenderId, peerId)
                .eq(SysMessage::getReceiverId, me)
                .eq(SysMessage::getReceiverDeleted, 0)))
        .lt(beforeId != null, SysMessage::getId, beforeId)
        .orderByDesc(SysMessage::getId)
        .last("LIMIT " + pageSize));
    Collections.reverse(records);
    return records;
  }

  /**
   * 发送消息：校验对方存在且启用，落库后实时推送 chat 帧（对方离线则静默）。
   *
   * @param me         当前用户 id
   * @param receiverId 接收人 id
   * @param content    消息内容（controller 已做非空与长度校验）
   * @return 落库后的消息实体
   */
  public SysMessage send(Long me, Long receiverId, String content) {
    return send(me, receiverId, content, null);
  }

  /**
   * 发送消息（可带引用）：引用消息必须属于本会话双方之间的记录，
   * 内容在发送时固化为快照（quoteContent），原消息此后被删除不影响引用展示。
   *
   * @param quoteId 被引用消息 id（null=非引用消息）
   */
  public SysMessage send(Long me, Long receiverId, String content, Long quoteId) {
    if (receiverId.equals(me)) {
      throw BizException.badRequest("error.im.self");
    }
    SysUser receiver = userMapper.selectById(receiverId);
    if (receiver == null || receiver.getStatus() == null
        || receiver.getStatus() != USER_STATUS_ENABLED) {
      throw BizException.badRequest("error.im.peer.invalid");
    }
    SysMessage message = new SysMessage();
    message.setSenderId(me);
    message.setReceiverId(receiverId);
    message.setContent(content);
    message.setReadFlag(READ_FLAG_UNREAD);
    if (quoteId != null) {
      SysMessage quote = messageMapper.selectById(quoteId);
      boolean inConversation = quote != null
          && ((quote.getSenderId().equals(me) && quote.getReceiverId().equals(receiverId))
          || (quote.getSenderId().equals(receiverId) && quote.getReceiverId().equals(me)));
      if (!inConversation) {
        throw BizException.badRequest("error.im.quote.invalid");
      }
      message.setQuoteId(quote.getId());
      message.setQuoteContent(quote.getContent());
    }
    messageMapper.insert(message);

    // 推送失败不影响落库（对方离线属常态），仅告警
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("type", "chat");
      payload.put("message", messagePayload(message));
      webSocketHandler.sendToUser(receiverId, payload);
    } catch (Exception e) {
      log.warn("IM 消息 WebSocket 推送失败 receiverId={} messageId={}", receiverId, message.getId(), e);
    }
    return message;
  }

  /**
   * 把对方发来的未读消息标记已读（限定 receiver=本人，防越权），
   * 并向对方推送 read 回执帧。
   *
   * @param me     当前用户 id（接收人）
   * @param peerId 对方用户 id（发送人）
   * @return 本次标记已读的条数
   */
  public long markPeerRead(Long me, Long peerId) {
    requirePeerExists(peerId);
    SysMessage patch = new SysMessage();
    patch.setReadFlag(READ_FLAG_READ);
    patch.setReadTime(LocalDateTime.now());
    long updated = messageMapper.update(patch, new LambdaUpdateWrapper<SysMessage>()
        .eq(SysMessage::getReceiverId, me)
        .eq(SysMessage::getSenderId, peerId)
        .eq(SysMessage::getReadFlag, READ_FLAG_UNREAD));
    if (updated > 0) {
      // 回执给对方：peerId=读者（我），对方据此把自己发出的消息置为已读
      try {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "read");
        payload.put("peerId", me);
        webSocketHandler.sendToUser(peerId, payload);
      } catch (Exception e) {
        log.warn("IM 已读回执推送失败 peerId={}", peerId, e);
      }
    }
    return updated;
  }

  /** WS 推送与 REST 返回共用的消息体（去除 update_time 等内部字段） */
  private Map<String, Object> messagePayload(SysMessage message) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", message.getId());
    payload.put("senderId", message.getSenderId());
    payload.put("receiverId", message.getReceiverId());
    payload.put("content", message.getContent());
    payload.put("readFlag", message.getReadFlag());
    payload.put("quoteId", message.getQuoteId());
    payload.put("quoteContent", message.getQuoteContent());
    payload.put("createTime", message.getCreateTime());
    return payload;
  }

  /**
   * 删除单条消息（单侧删除，类似微信）：本人是发送人则打 sender_deleted，
   * 是接收人则打 receiver_deleted；与本人无关的消息直接拒绝。
   * 之后本人视角的会话/历史/未读统计都不再可见，对方不受影响。
   *
   * @return 影响条数（消息存在且属于本人时为 1）
   */
  public long deleteMessage(Long me, Long messageId) {
    SysMessage message = messageMapper.selectById(messageId);
    if (message == null) {
      throw BizException.badRequest("error.im.message.notfound");
    }
    if (message.getSenderId().equals(me)) {
      SysMessage patch = new SysMessage();
      patch.setSenderDeleted(1);
      return messageMapper.update(patch, new LambdaUpdateWrapper<SysMessage>()
          .eq(SysMessage::getId, messageId)
          .eq(SysMessage::getSenderDeleted, 0));
    }
    if (message.getReceiverId().equals(me)) {
      SysMessage patch = new SysMessage();
      patch.setReceiverDeleted(1);
      return messageMapper.update(patch, new LambdaUpdateWrapper<SysMessage>()
          .eq(SysMessage::getId, messageId)
          .eq(SysMessage::getReceiverDeleted, 0));
    }
    throw BizException.badRequest("error.im.delete.forbidden");
  }

  /**
   * 删除会话（单侧删除）：把本人与该对方之间的全部消息按本人视角打删除标记，
   * 之后本人会话列表不再出现该会话（对方视图不受影响）。
   *
   * @return 标记的消息条数
   */
  public long deleteConversation(Long me, Long peerId) {
    requirePeerExists(peerId);
    SysMessage patch = new SysMessage();
    patch.setSenderDeleted(1);
    long sent = messageMapper.update(patch, new LambdaUpdateWrapper<SysMessage>()
        .eq(SysMessage::getSenderId, me)
        .eq(SysMessage::getReceiverId, peerId)
        .eq(SysMessage::getSenderDeleted, 0));
    SysMessage patch2 = new SysMessage();
    patch2.setReceiverDeleted(1);
    long received = messageMapper.update(patch2, new LambdaUpdateWrapper<SysMessage>()
        .eq(SysMessage::getSenderId, peerId)
        .eq(SysMessage::getReceiverId, me)
        .eq(SysMessage::getReceiverDeleted, 0));
    return sent + received;
  }

  /** 校验对方用户存在（不存在抛业务异常，避免发送/已读打在空 id 上） */
  private void requirePeerExists(Long peerId) {
    if (userMapper.selectById(peerId) == null) {
      throw BizException.badRequest("error.im.peer.invalid");
    }
  }
}
