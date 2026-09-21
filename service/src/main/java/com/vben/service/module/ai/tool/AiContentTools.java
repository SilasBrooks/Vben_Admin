package com.vben.service.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
import com.vben.service.module.notice.entity.SysNotice;
import com.vben.service.module.notice.mapper.SysNoticeMapper;
import com.vben.service.module.notice.service.NoticeService;
import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.entity.SysDictData;
import com.vben.service.module.system.entity.SysFile;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysDeptMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.service.SysDictAdminService;
import com.vben.service.module.system.service.SysFileService;
import com.vben.service.security.LoginUserHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 内容类 AI 工具：字典查询、文件查询、发布公告、查看本人通知。
 * 发布公告为普通写操作（需确认卡片/计划确认），不属于高危。
 */
@Component
@RequiredArgsConstructor
public class AiContentTools {

  private static final int NOTICE_LIMIT = 10;

  private final SysDictAdminService dictService;
  private final SysFileService fileService;
  private final NoticeService noticeService;
  private final SysNoticeMapper noticeMapper;
  private final SysDeptMapper deptMapper;
  private final SysUserMapper userMapper;
  private final ObjectMapper objectMapper;

  @AiAgentTool(name = "query_dict_options", title = "查询字典选项", kind = AiToolKind.QUERY,
      permission = "System:Dict:List",
      description = "按字典类型键查询启用的字典选项列表（如用户性别、状态等下拉选项）。"
          + "dictType 为字典类型英文键，不确定时可先向用户确认。结果包含显示名 label、值 value、排序。")
  public AiToolResult queryDictOptions(
      @AiToolParam(value = "字典类型键，如 sys_user_sex", required = true) String dictType) {
    List<SysDictData> data = dictService.options(dictType);
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysDictData d : data) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("label", d.getDictLabel());
      m.put("value", d.getDictValue());
      m.put("sortNum", d.getSortNum());
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("dictType", dictType, "options", items)));
  }

  @AiAgentTool(name = "query_files", title = "查询文件", kind = AiToolKind.QUERY,
      permission = "System:File:List",
      description = "查询文件管理中的文件列表（最近 20 个），可按原始文件名模糊搜索。"
          + "结果包含 id、文件名、大小字节、类型、上传人、上传时间。")
  public AiToolResult queryFiles(
      @AiToolParam("文件名关键字，可省略") String keyword) {
    IPage<SysFile> page = fileService.page(1, 20, keyword);
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysFile f : page.getRecords()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", f.getId());
      m.put("fileName", f.getOriginalName());
      m.put("size", f.getSize());
      m.put("contentType", f.getContentType());
      m.put("uploaderName", f.getUploaderName());
      m.put("createTime", f.getCreateTime());
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("total", page.getTotal(), "files", items)));
  }

  @AiAgentTool(name = "publish_announcement", title = "发布公告", kind = AiToolKind.WRITE,
      permission = "Notice:Announce:Publish",
      description = "发布一条站内公告并实时推送给目标用户。targetType 为发送范围：all=全员，"
          + "dept=按部门（此时 deptNames 传部门中文名列表），user=按指定用户（此时 usernames 传登录用户名列表）。"
          + "只发送给启用状态账号。发布前必须与用户确认标题、内容与发送范围。")
  public AiToolResult publishAnnouncement(
      @AiToolParam(value = "公告标题", required = true) String title,
      @AiToolParam(value = "公告正文", required = true) String content,
      @AiToolParam(value = "发送范围：all 全员 / dept 按部门 / user 按指定用户", required = true)
      String targetType,
      @AiToolParam("targetType=dept 时的部门中文名列表，如 [\"仓储部\"]") List<String> deptNames,
      @AiToolParam("targetType=user 时的登录用户名列表，如 [\"zhangsan\"]") List<String> usernames) {

    List<Long> deptIds = resolveDeptIds(deptNames);
    List<Long> userIds = resolveUserIds(usernames);

    int count = noticeService.announce(title, content, targetType, deptIds, userIds);
    String scopeText = switch (targetType) {
      case "all" -> "全员";
      case "dept" -> "部门「" + String.join("、", deptNames) + "」";
      case "user" -> "用户「" + String.join("、", usernames) + "」";
      default -> targetType;
    };
    String summary = "已向" + scopeText + "发布公告「" + title + "」，实际送达 " + count + " 人";
    return AiToolResult.created(json(Map.of("sentCount", count, "title", title)), summary);
  }

  @AiAgentTool(name = "query_my_notices", title = "查询我的通知", kind = AiToolKind.QUERY,
      description = "查询当前登录用户自己最近的站内通知/公告（10 条）。msgType 可省略（全部），"
          + "或传 announcement=公告、security=安全通知。结果包含标题、正文、类型、是否已读、时间。")
  public AiToolResult queryMyNotices(
      @AiToolParam("消息类型过滤：announcement 公告 / security 安全通知，可省略") String msgType) {
    Long userId = LoginUserHolder.require().getUserId();
    List<SysNotice> notices = noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
        .eq(SysNotice::getUserId, userId)
        .eq(msgType != null && !msgType.isBlank(), SysNotice::getMsgType, msgType)
        .orderByDesc(SysNotice::getCreateTime)
        .last("limit " + NOTICE_LIMIT));
    List<Map<String, Object>> items = new ArrayList<>();
    for (SysNotice n : notices) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("title", n.getTitle());
      m.put("content", n.getContent());
      m.put("msgType", n.getMsgType());
      m.put("read", n.getReadFlag() != null && n.getReadFlag() == 1);
      m.put("createTime", n.getCreateTime());
      items.add(m);
    }
    return AiToolResult.success(json(Map.of("total", items.size(), "notices", items)));
  }

  // ------------------------------------------------------------------
  // 名称 → id 解析（0 个即报错，不猜测）
  // ------------------------------------------------------------------

  private List<Long> resolveDeptIds(List<String> deptNames) {
    if (deptNames == null || deptNames.isEmpty()) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>();
    for (String name : deptNames) {
      SysDept dept = deptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
          .eq(SysDept::getDeptName, name.trim()));
      if (dept == null) {
        throw BizException.badRequest("error.ai.dept.notFound", name);
      }
      ids.add(dept.getId());
    }
    return ids;
  }

  private List<Long> resolveUserIds(List<String> usernames) {
    if (usernames == null || usernames.isEmpty()) {
      return List.of();
    }
    List<Long> ids = new ArrayList<>();
    for (String name : usernames) {
      SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
          .eq(SysUser::getUsername, name.trim()));
      if (user == null) {
        throw BizException.badRequest("error.ai.user.notFound", name);
      }
      ids.add(user.getId());
    }
    return ids;
  }

  private String json(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException("工具结果序列化失败", e);
    }
  }
}
