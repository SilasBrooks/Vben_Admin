package com.vben.service.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.menu.SysMenuService;
import com.vben.service.module.user.entity.SysUserConfig;
import com.vben.service.module.user.mapper.SysUserConfigMapper;
import com.vben.service.security.LoginUserHolder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserConfigService {
  private static final String MENU_KEY = "menu";

  private final SysUserConfigMapper mapper;
  private final ObjectMapper objectMapper;
  private final SysMenuService menuService;

  public JsonNode get(String key) {
    Long userId = LoginUserHolder.require().getUserId();
    validateKey(key);
    if (MENU_KEY.equals(key)) {
      // 菜单始终按当前授权生成，同名个人配置不能覆盖权限结果。
      var routes = menuService.buildRouteTree(userId);
      return routes == null ? objectMapper.createArrayNode() : objectMapper.valueToTree(routes);
    }
    SysUserConfig config = mapper.selectOne(scope(userId, key));
    if (config != null) {
      try {
        JsonNode value = objectMapper.readTree(config.getConfigValue());
        if (value != null && value.isArray()) return value;
      } catch (JsonProcessingException ignored) {
        // 存量损坏配置不影响列表展示，下一次保存即可修复。
      }
    }
    return objectMapper.createArrayNode();
  }

  public void save(String key, JsonNode value) {
    Long userId = LoginUserHolder.require().getUserId();
    validateKey(key);
    if (MENU_KEY.equals(key)) {
      throw BizException.badRequest("error.userConfig.key.readOnly");
    }
    if (value == null || !value.isArray() || value.size() > 200) {
      throw BizException.badRequest("error.userConfig.value.invalid");
    }
    String json = value.toString();
    if (json.getBytes(StandardCharsets.UTF_8).length > 65535) {
      throw BizException.badRequest("error.userConfig.value.tooLarge");
    }
    SysUserConfig patch = new SysUserConfig();
    patch.setConfigValue(json);
    if (mapper.update(patch, scope(userId, key)) == 0) {
      SysUserConfig config = new SysUserConfig();
      config.setUserId(userId);
      config.setConfigKey(key);
      config.setConfigValue(json);
      try {
        mapper.insert(config);
      } catch (DuplicateKeyException ignored) {
        // 并发首次保存由唯一键兜底；无外层事务，失败 INSERT 不污染后续 UPDATE。
        mapper.update(patch, scope(userId, key));
      }
    }
  }

  private static void validateKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
      throw BizException.badRequest("error.userConfig.key.invalid");
    }
  }

  private static LambdaQueryWrapper<SysUserConfig> scope(Long userId, String key) {
    return new LambdaQueryWrapper<SysUserConfig>()
        .eq(SysUserConfig::getUserId, userId).eq(SysUserConfig::getConfigKey, key);
  }
}
