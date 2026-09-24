package com.vben.service.module.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.GlobalExceptionHandler;
import com.vben.service.module.menu.SysMenuService;
import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.mapper.SysMenuMapper;
import com.vben.service.module.user.entity.SysUserConfig;
import com.vben.service.module.user.mapper.SysUserConfigMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserConfigTest {
  private SysUserConfigMapper mapper;
  private SysMenuMapper menuMapper;
  private UserConfigService service;
  private MockMvc http;
  private final ObjectMapper json = new ObjectMapper();
  private final Map<String, SysUserConfig> database = new HashMap<>();

  @BeforeEach
  void setup() {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUserConfig.class);
    mapper = mock(SysUserConfigMapper.class);
    menuMapper = mock(SysMenuMapper.class);
    service = new UserConfigService(mapper, json, new SysMenuService(menuMapper, json));
    http = MockMvcBuilders.standaloneSetup(new UserConfigController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    login(1L);
    when(mapper.selectOne(any())).thenAnswer(call -> database.get(scopeKey(call.getArgument(0))));
    when(mapper.update(any(SysUserConfig.class), any())).thenAnswer(call -> {
      SysUserConfig existing = database.get(scopeKey(call.getArgument(1)));
      if (existing == null) return 0;
      existing.setConfigValue(((SysUserConfig) call.getArgument(0)).getConfigValue());
      return 1;
    });
    when(mapper.insert(any(SysUserConfig.class))).thenAnswer(call -> {
      SysUserConfig config = call.getArgument(0);
      database.put(config.getUserId() + ":" + config.getConfigKey(), config);
      return 1;
    });
  }

  private static String scopeKey(LambdaQueryWrapper<SysUserConfig> scope) {
    assertThat(scope.getSqlSegment()).contains("user_id =", "config_key =");
    return scope.getParamNameValuePairs().get("MPGENVAL1") + ":" + scope.getParamNameValuePairs().get("MPGENVAL2");
  }

  private static void login(long id) {
    LoginUserHolder.set(new LoginUser(id, "test", List.of(), null, 0));
  }

  @AfterEach
  void clear() { LoginUserHolder.clear(); }

  @Test
  void roundTripOverwriteResetAndUserIsolationThroughHttp() throws Exception {
    String layout = "[{\"field\":\"name\",\"visible\":false,\"width\":180,\"fixed\":\"left\"}]";
    http.perform(get("/user-config").param("key", "table.user"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    http.perform(post("/user-config/save").contentType(MediaType.APPLICATION_JSON)
        .content("{\"key\":\"table.user\",\"userId\":99,\"value\":" + layout + "}"))
        .andExpect(status().isOk());
    http.perform(get("/user-config").param("key", "table.user"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].visible").value(false))
        .andExpect(jsonPath("$.data[0].width").value(180));
    login(2L);
    assertThat(service.get("table.user").isEmpty()).isTrue();
    service.save("table.user", json.readTree("[{\"field\":\"id\"}]"));
    login(1L);
    assertThat(service.get("table.role").isEmpty()).isTrue();
    assertThat(service.get("table.user")).isEqualTo(json.readTree(layout));
    service.save("table.user", json.createArrayNode());
    assertThat(service.get("table.user").isEmpty()).isTrue();
    assertThat(database).hasSize(2).doesNotContainKey("99:table.user");
  }

  @Test
  void menuUsesCurrentPermissionsAndPreservesNestedRoutesInsteadOfStoredConfig() throws Exception {
    SysMenu catalog = menu(10L, 0L, "M", "Catalog");
    catalog.setRedirect("/Page");
    SysMenu page = menu(11L, 10L, "C", "Page");
    page.setComponent("/system/user/index");
    page.setKeepAlive(1);
    page.setExtraMeta("{\"badge\":\"new\"}");
    SysMenu button = menu(12L, 10L, "F", "Button");
    when(menuMapper.selectMenusByUserId(1L)).thenReturn(List.of(catalog, page, button));
    SysUserConfig forged = new SysUserConfig();
    forged.setConfigValue("[{\"name\":\"Forged\"}]");
    database.put("1:menu", forged);

    http.perform(get("/user-config").param("key", "menu").param("userId", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].name").value("Catalog"))
        .andExpect(jsonPath("$.data[0].redirect").value("/Page"))
        .andExpect(jsonPath("$.data[0].component").doesNotExist())
        .andExpect(jsonPath("$.data[0].children.length()").value(1))
        .andExpect(jsonPath("$.data[0].children[0].path").value("/Page"))
        .andExpect(jsonPath("$.data[0].children[0].component").value("/system/user/index"))
        .andExpect(jsonPath("$.data[0].children[0].meta.title").value("page.test.Page"))
        .andExpect(jsonPath("$.data[0].children[0].meta.keepAlive").value(true))
        .andExpect(jsonPath("$.data[0].children[0].meta.badge").value("new"));
    verify(menuMapper).selectMenusByUserId(1L);
    verifyNoInteractions(mapper);

    login(2L);
    http.perform(get("/user-config").param("key", "menu"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
    login(1L);
    when(menuMapper.selectMenusByUserId(1L)).thenReturn(List.of());
    assertThat(service.get("menu").isEmpty()).isTrue();
    verifyNoInteractions(mapper);
  }

  @Test
  void menuIsReadOnlyAndRequiresAuthentication() throws Exception {
    http.perform(post("/user-config/save").contentType(MediaType.APPLICATION_JSON)
        .content("{\"key\":\"menu\",\"value\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("error.userConfig.key.readOnly"));
    LoginUserHolder.clear();
    http.perform(get("/user-config").param("key", "menu"))
        .andExpect(status().isUnauthorized());
    http.perform(post("/user-config/save").contentType(MediaType.APPLICATION_JSON)
        .content("{\"key\":\"menu\",\"value\":[]}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(mapper, menuMapper);
  }

  private static SysMenu menu(long id, long parentId, String type, String name) {
    SysMenu menu = new SysMenu();
    menu.setId(id);
    menu.setParentId(parentId);
    menu.setMenuType(type);
    menu.setMenuName(name);
    menu.setPath("/" + name);
    menu.setTitle("page.test." + name);
    return menu;
  }

  @Test
  void invalidInputsAndUnauthenticatedRequestsDoNotWrite() throws Exception {
    for (String body : List.of(
        "{\"value\":[]}", "{\"key\":\" \u0020\",\"value\":[]}",
        "{\"key\":\"table.user\",\"value\":{}}", "{\"key\":\"table.user\",\"value\":null}",
        "{\"key\":\"table.user\",\"value\":\"[]\"}")) {
      http.perform(post("/user-config/save").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest());
    }
    http.perform(get("/user-config")).andExpect(status().isBadRequest());
    http.perform(post("/user-config/save").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("key", "table.user", "value", List.of("x".repeat(65536))))))
        .andExpect(status().isBadRequest());
    var tooMany = json.createArrayNode();
    for (int i = 0; i < 201; i++) tooMany.addObject();
    assertThatThrownBy(() -> service.save("table.user", tooMany)).hasMessage("error.userConfig.value.invalid");
    LoginUserHolder.clear();
    http.perform(get("/user-config").param("key", "table.user")).andExpect(status().isUnauthorized());
    http.perform(post("/user-config/save").contentType(MediaType.APPLICATION_JSON)
        .content("{\"key\":\"table.user\",\"value\":[]}"))
        .andExpect(status().isUnauthorized());
    verify(mapper, never()).insert(any(SysUserConfig.class));
  }

  @Test
  void concurrentFirstInsertRecoversUsingScopedUpdate() throws Exception {
    when(mapper.insert(any(SysUserConfig.class))).thenThrow(new DuplicateKeyException("duplicate"));
    service.save("table.user", json.readTree("[]"));
    verify(mapper, times(2)).update(any(SysUserConfig.class), any());
  }

  @Test
  void damagedStoredJsonFallsBackToDefault() {
    SysUserConfig config = new SysUserConfig();
    config.setConfigValue("not-json");
    database.put("1:table.user", config);
    assertThat(service.get("table.user").isEmpty()).isTrue();
    config.setConfigValue("{}");
    assertThat(service.get("table.user").isEmpty()).isTrue();
  }
}
