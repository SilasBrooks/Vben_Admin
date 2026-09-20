package com.vben.service.module.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.im.entity.SysMessage;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {

  /**
   * 会话汇总：按聊天对方分组，取最后一条消息 id 与对方发来的未读数。
   *
   * <p>SQL 为 PostgreSQL / MySQL 双方言兼容写法：
   * GROUP BY 输出列别名（两库都支持），COUNT(CASE...) 计未读；
   * 别名保持 snake_case（PG 未加引号会折叠小写，MyBatis Map 键按查询别名原样返回）。
   *
   * @param userId 当前用户 id
   * @return [{peer_id, last_message_id, unread_count}]，按 last_message_id 倒序
   */
  @Select("""
      SELECT CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END AS peer_id,
             MAX(id) AS last_message_id,
             COUNT(CASE WHEN receiver_id = #{userId} AND read_flag = 0 THEN 1 END) AS unread_count
      FROM sys_message
      WHERE sender_id = #{userId} OR receiver_id = #{userId}
      GROUP BY peer_id
      ORDER BY last_message_id DESC
      """)
  List<Map<String, Object>> selectConversationSummaries(@Param("userId") Long userId);
}
