package com.vben.service.module.menu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单服务：sys_menu（若依风格平铺表）→ vben 动态路由树
 *
 * <p>映射规则：
 * <ul>
 *   <li>M(目录)：生成带 children 的父节点，无 component</li>
 *   <li>C(菜单)：叶子节点，component 指向前端 ../views/**.vue</li>
 *   <li>F(按钮)：不进路由树，仅其 perm 汇入权限码</li>
 *   <li>meta：title/icon/order/keepAlive/affixTab/hideInMenu/authority 由列映射，
 *       extraMeta(JSON) 原样合并，可携带 vben 任意 meta 扩展</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

  private static final String TYPE_CATALOG = "M";
  private static final String TYPE_MENU = "C";

  private final SysMenuMapper menuMapper;
  private final ObjectMapper objectMapper;

  /** 查询用户有权访问的动态路由树 */
  public List<VbenRoute> buildRouteTree(Long userId) {
    List<SysMenu> menus = menuMapper.selectMenusByUserId(userId);
    // 仅 M/C 参与路由树
    Map<Long, List<SysMenu>> byParent = menus.stream()
        .filter(m -> TYPE_CATALOG.equals(m.getMenuType()) || TYPE_MENU.equals(m.getMenuType()))
        .sorted(Comparator
            .comparing(SysMenu::getParentId)
            .thenComparing(m -> m.getOrderNum() == null ? 0 : m.getOrderNum()))
        .collect(Collectors.groupingBy(SysMenu::getParentId, LinkedHashMap::new,
            Collectors.toList()));

    return convertChildren(byParent, 0L);
  }

  private List<VbenRoute> convertChildren(Map<Long, List<SysMenu>> byParent, Long parentId) {
    List<SysMenu> children = byParent.get(parentId);
    if (children == null) {
      return null;
    }
    List<VbenRoute> routes = new ArrayList<>();
    for (SysMenu menu : children) {
      VbenRoute route = new VbenRoute();
      route.setName(menu.getMenuName());
      route.setPath(menu.getPath());
      route.setRedirect(menu.getRedirect());
      if (TYPE_MENU.equals(menu.getMenuType())) {
        route.setComponent(menu.getComponent());
      }
      route.setMeta(buildMeta(menu));
      route.setChildren(convertChildren(byParent, menu.getId()));
      // 目录没有子节点时省略 children 字段
      if (route.getChildren() == null || route.getChildren().isEmpty()) {
        route.setChildren(null);
      }
      routes.add(route);
    }
    return routes;
  }

  @SneakyThrows
  private LinkedHashMap<String, Object> buildMeta(SysMenu menu) {
    LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
    meta.put("title", menu.getTitle());
    if (StringUtils.hasText(menu.getIcon())) {
      meta.put("icon", menu.getIcon());
    }
    if (menu.getOrderNum() != null && menu.getOrderNum() != 0) {
      meta.put("order", menu.getOrderNum());
    }
    if (intValue(menu.getKeepAlive()) == 1) {
      meta.put("keepAlive", true);
    }
    if (intValue(menu.getAffixTab()) == 1) {
      meta.put("affixTab", true);
    }
    if (intValue(menu.getVisible()) == 1) {
      meta.put("hideInMenu", true);
    }
    if (StringUtils.hasText(menu.getAuthority())) {
      meta.put("authority", menu.getAuthority().split("\\s*,\\s*"));
    }
    // extraMeta JSON 原样合并（badge / menuVisibleWithForbidden / activePath 等）
    if (StringUtils.hasText(menu.getExtraMeta())) {
      Map<String, Object> extra = objectMapper.readValue(menu.getExtraMeta(),
          new TypeReference<LinkedHashMap<String, Object>>() {});
      meta.putAll(extra);
    }
    return meta;
  }

  private int intValue(Integer v) {
    return v == null ? 0 : v;
  }
}
