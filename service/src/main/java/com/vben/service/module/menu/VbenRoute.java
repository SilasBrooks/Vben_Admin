package com.vben.service.module.menu;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 前端 vben 动态路由节点（backend 模式），结构对齐
 * apps/backend-mock/utils/mock-data.ts 的 MOCK_MENUS：
 *
 * <pre>
 * {
 *   "name": "Analytics",
 *   "path": "/analytics",
 *   "component": "/dashboard/analytics/index",
 *   "redirect": "/analytics",
 *   "meta": { "title": "page.dashboard.analytics", "icon": "...", "order": -1, ... },
 *   "children": [ ... ]
 * }
 * </pre>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VbenRoute {

  private String name;

  private String path;

  /** 前端页面组件路径（../views 下的相对路径去掉 .vue） */
  private String component;

  private String redirect;

  private LinkedHashMap<String, Object> meta;

  private List<VbenRoute> children;
}
