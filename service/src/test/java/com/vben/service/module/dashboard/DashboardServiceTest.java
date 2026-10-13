package com.vben.service.module.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.vben.service.module.ai.usage.entity.SysLlmCallLog;
import com.vben.service.module.ai.usage.mapper.SysLlmCallLogMapper;
import com.vben.service.module.monitor.entity.SysLoginLog;
import com.vben.service.module.monitor.entity.SysOperLog;
import com.vben.service.module.monitor.mapper.SysLoginLogMapper;
import com.vben.service.module.monitor.mapper.SysOperLogMapper;
import com.vben.service.module.system.mapper.SysDeptMapper;
import com.vben.service.module.system.mapper.SysDictTypeMapper;
import com.vben.service.module.system.mapper.SysFileMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.OnlineSessionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仪表盘聚合单测：
 * AI 用量口径（tokens 为 NULL 计入次数不计入合计、趋势按天补零、模型分布降序 Top5）
 * 与监控数据块权限过滤（super 全可见；无监控权限受控块装配 null，且不触发日志表查询）。
 */
class DashboardServiceTest {

  @BeforeAll
  static void initTableInfo() {
    // 纯单测无 MyBatis-Plus 启动流程：手动注册 LambdaWrapper 所需实体的元数据缓存
    MapperBuilderAssistant assistant =
        new MapperBuilderAssistant(new MybatisConfiguration(), "");
    TableInfoHelper.initTableInfo(assistant, SysLoginLog.class);
    TableInfoHelper.initTableInfo(assistant, SysOperLog.class);
    TableInfoHelper.initTableInfo(assistant, SysLlmCallLog.class);
  }

  @BeforeEach
  void setUpLoginUser() {
    // 默认以 super 角色登录执行（既有 aiUsage 断言路径数据块全可见）
    LoginUserHolder.set(new LoginUser(1L, "admin", List.of("super"), Set.of(), 0L));
  }

  @AfterEach
  void clearLoginUser() {
    LoginUserHolder.clear();
  }

  private final SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
  private final SysRoleMapper roleMapper = Mockito.mock(SysRoleMapper.class);
  private final SysDeptMapper deptMapper = Mockito.mock(SysDeptMapper.class);
  private final SysFileMapper fileMapper = Mockito.mock(SysFileMapper.class);
  private final SysDictTypeMapper dictTypeMapper = Mockito.mock(SysDictTypeMapper.class);
  private final SysLoginLogMapper loginLogMapper = Mockito.mock(SysLoginLogMapper.class);
  private final SysOperLogMapper operLogMapper = Mockito.mock(SysOperLogMapper.class);
  private final SysLlmCallLogMapper llmCallLogMapper = Mockito.mock(SysLlmCallLogMapper.class);
  private final OnlineSessionService onlineSessionService =
      Mockito.mock(OnlineSessionService.class);

  private final DashboardService service = new DashboardService(userMapper, roleMapper,
      deptMapper, fileMapper, dictTypeMapper, loginLogMapper, operLogMapper, llmCallLogMapper,
      onlineSessionService);

  private DashboardServiceTest stubEmptyDefaults() {
    // 无关数据块全部返回空集，聚焦 aiUsage
    when(loginLogMapper.selectList(any())).thenReturn(List.of());
    when(operLogMapper.selectList(any())).thenReturn(List.of());
    when(userMapper.selectMaps(any())).thenReturn(List.of());
    when(operLogMapper.selectMaps(any())).thenReturn(List.of());
    return this;
  }

  /** 构造一条用量记录（totalTokens 可为 null 模拟上游未返回 usage） */
  private SysLlmCallLog callLog(LocalDateTime time, Long totalTokens) {
    SysLlmCallLog log = new SysLlmCallLog();
    log.setScene("chat");
    log.setModel("test-model");
    log.setTotalTokens(totalTokens);
    log.setElapsedMs(10L);
    log.setCreateTime(time);
    return log;
  }

  @Test
  void aiUsageCountsNullTokenRecordsButNotTokenSum() {
    stubEmptyDefaults();
    LocalDate today = LocalDate.now();
    // 趋势数据：今日 2 条（100 + null），13 天前 1 条（50）
    when(llmCallLogMapper.selectList(any())).thenReturn(List.of(
        callLog(today.atStartOfDay().plusMinutes(1), 100L),
        callLog(today.atStartOfDay().plusMinutes(2), null),
        callLog(today.minusDays(13).atStartOfDay(), 50L)));

    // selectMaps 依调用顺序返回：累计聚合、今日聚合、本月聚合、模型分布
    Map<String, Object> totalRow = new LinkedHashMap<>();
    totalRow.put("calls", 5L);
    totalRow.put("tokens", BigDecimal.valueOf(400));
    Map<String, Object> todayRow = new LinkedHashMap<>();
    todayRow.put("calls", 2L);
    todayRow.put("tokens", BigDecimal.valueOf(100));
    Map<String, Object> monthRow = new LinkedHashMap<>();
    monthRow.put("calls", 3L);
    monthRow.put("tokens", BigDecimal.valueOf(250));
    Map<String, Object> modelA = new LinkedHashMap<>();
    modelA.put("model", "model-a");
    modelA.put("calls", 2L);
    modelA.put("tokens", BigDecimal.valueOf(150));
    Map<String, Object> modelB = new LinkedHashMap<>();
    modelB.put("model", "model-b");
    modelB.put("calls", 1L);
    modelB.put("tokens", BigDecimal.valueOf(100));
    when(llmCallLogMapper.selectMaps(any()))
        .thenReturn(List.of(totalRow), List.of(todayRow), List.of(monthRow),
            List.of(modelA, modelB));

    @SuppressWarnings("unchecked")
    Map<String, Object> aiUsage = (Map<String, Object>) service.summary().get("aiUsage");

    // NULL tokens 计入次数（todayCalls=2）不计入合计（todayTokens=100）
    assertThat(aiUsage.get("totalTokens")).isEqualTo(400L);
    assertThat(aiUsage.get("totalCalls")).isEqualTo(5L);
    assertThat(aiUsage.get("todayTokens")).isEqualTo(100L);
    assertThat(aiUsage.get("todayCalls")).isEqualTo(2L);
    assertThat(aiUsage.get("monthTokens")).isEqualTo(250L);
    assertThat(aiUsage.get("monthCalls")).isEqualTo(3L);

    // 趋势 14 天升序：首日 13 天前 tokens=50 calls=1，今日 tokens=100 calls=2，其余补零
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> trend = (List<Map<String, Object>>) aiUsage.get("tokenTrend");
    assertThat(trend).hasSize(14);
    assertThat(trend.get(0).get("date")).isEqualTo(today.minusDays(13).toString());
    assertThat(trend.get(0).get("tokens")).isEqualTo(50L);
    assertThat(trend.get(0).get("calls")).isEqualTo(1L);
    assertThat(trend.get(13).get("date")).isEqualTo(today.toString());
    assertThat(trend.get(13).get("tokens")).isEqualTo(100L);
    assertThat(trend.get(13).get("calls")).isEqualTo(2L);
    assertThat(trend.get(5).get("tokens")).isEqualTo(0L);

    // 模型分布按 token 降序
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> models =
        (List<Map<String, Object>>) aiUsage.get("modelDistribution");
    assertThat(models).hasSize(2);
    assertThat(models.get(0).get("name")).isEqualTo("model-a");
    assertThat(models.get(0).get("tokens")).isEqualTo(150L);
    assertThat(models.get(1).get("name")).isEqualTo("model-b");
  }

  @Test
  void aiUsageEmptyTableYieldsZeroesAndEmptySeries() {
    stubEmptyDefaults();
    // 空表：selectList 空集；selectMaps 依次为累计聚合（count=0/sum=0）、今日聚合、本月聚合、模型分布（空集）
    when(llmCallLogMapper.selectList(any())).thenReturn(List.of());
    Map<String, Object> zeroRow = new LinkedHashMap<>();
    zeroRow.put("calls", 0L);
    zeroRow.put("tokens", BigDecimal.ZERO);
    when(llmCallLogMapper.selectMaps(any())).thenReturn(List.of(zeroRow), List.of(zeroRow),
        List.of(zeroRow), List.of());

    @SuppressWarnings("unchecked")
    Map<String, Object> aiUsage = (Map<String, Object>) service.summary().get("aiUsage");

    assertThat(aiUsage.get("totalTokens")).isEqualTo(0L);
    assertThat(aiUsage.get("totalCalls")).isEqualTo(0L);
    assertThat(aiUsage.get("todayTokens")).isEqualTo(0L);
    assertThat(aiUsage.get("todayCalls")).isEqualTo(0L);
    assertThat(aiUsage.get("monthTokens")).isEqualTo(0L);
    assertThat(aiUsage.get("monthCalls")).isEqualTo(0L);
    assertThat((List<?>) aiUsage.get("modelDistribution")).isEmpty();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> trend = (List<Map<String, Object>>) aiUsage.get("tokenTrend");
    assertThat(trend).hasSize(14);
    assertThat(trend.get(13).get("tokens")).isEqualTo(0L);
  }

  /** aiUsage 聚合行通用构造（calls/tokens 归零行） */
  private Map<String, Object> zeroRow() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("calls", 0L);
    row.put("tokens", BigDecimal.ZERO);
    return row;
  }

  @Test
  void superUserSeesAllBlocks() {
    when(userMapper.selectCount(null)).thenReturn(10L);
    when(roleMapper.selectCount(null)).thenReturn(3L);
    when(deptMapper.selectCount(null)).thenReturn(4L);
    when(fileMapper.selectCount(null)).thenReturn(5L);
    when(dictTypeMapper.selectCount(null)).thenReturn(6L);
    when(onlineSessionService.listAll()).thenReturn(List.of(
        new OnlineSessionService.OnlineUserView(1L, "u1", "U1",
            LocalDateTime.now().toString(), "127.0.0.1", 0L),
        new OnlineSessionService.OnlineUserView(2L, "u2", "U2",
            LocalDateTime.now().toString(), "127.0.0.1", 0L)));
    when(userMapper.selectMaps(any())).thenReturn(List.of());
    // selectCount 依调用顺序：totals.loginCount → today.loginSuccess → today.loginFail
    when(loginLogMapper.selectCount(any())).thenReturn(7L, 1L, 2L);
    // selectCount 依调用顺序：totals.operCount → today.operCount
    when(operLogMapper.selectCount(any())).thenReturn(8L, 3L);
    // selectList 依调用顺序：loginTrend → recentLogins
    when(loginLogMapper.selectList(any())).thenReturn(List.of(), List.of());
    when(operLogMapper.selectList(any())).thenReturn(List.of());
    when(operLogMapper.selectMaps(any())).thenReturn(List.of());
    when(llmCallLogMapper.selectList(any())).thenReturn(List.of());
    when(llmCallLogMapper.selectMaps(any())).thenReturn(List.of(zeroRow()), List.of(zeroRow()),
        List.of(zeroRow()), List.of());

    Map<String, Object> data = service.summary();

    @SuppressWarnings("unchecked")
    Map<String, Object> totals = (Map<String, Object>) data.get("totals");
    assertThat(totals.get("userCount")).isEqualTo(10L);
    assertThat(totals.get("onlineCount")).isEqualTo(2);
    assertThat(totals.get("loginCount")).isEqualTo(7L);
    assertThat(totals.get("operCount")).isEqualTo(8L);

    @SuppressWarnings("unchecked")
    Map<String, Object> today = (Map<String, Object>) data.get("today");
    assertThat(today.get("loginSuccess")).isEqualTo(1L);
    assertThat(today.get("loginFail")).isEqualTo(2L);
    assertThat(today.get("operCount")).isEqualTo(3L);

    assertThat(data.get("aiUsage")).isNotNull();
    assertThat((List<?>) data.get("loginTrend")).hasSize(14);
    assertThat((List<?>) data.get("deptDistribution")).isEmpty();
    assertThat((List<?>) data.get("moduleDistribution")).isEmpty();
    assertThat((List<?>) data.get("recentLogins")).isEmpty();
    assertThat((List<?>) data.get("recentOpers")).isEmpty();
  }

  @Test
  void monitorBlocksNullWithoutPermission() {
    // 无 super 角色、无任何权限码的普通用户
    LoginUserHolder.set(new LoginUser(2L, "guest", List.of("viewer"), Set.of(), 0L));

    when(userMapper.selectCount(null)).thenReturn(10L);
    when(roleMapper.selectCount(null)).thenReturn(3L);
    when(deptMapper.selectCount(null)).thenReturn(4L);
    when(fileMapper.selectCount(null)).thenReturn(5L);
    when(dictTypeMapper.selectCount(null)).thenReturn(6L);
    when(onlineSessionService.listAll()).thenReturn(List.of());
    when(userMapper.selectMaps(any())).thenReturn(List.of());
    when(llmCallLogMapper.selectList(any())).thenReturn(List.of());
    when(llmCallLogMapper.selectMaps(any())).thenReturn(List.of(zeroRow()), List.of(zeroRow()),
        List.of(zeroRow()), List.of());

    Map<String, Object> data = service.summary();

    // 受控数据块装配 null（语义为「不可见」而非 0），数据不出网
    @SuppressWarnings("unchecked")
    Map<String, Object> totals = (Map<String, Object>) data.get("totals");
    assertThat(totals.get("loginCount")).isNull();
    assertThat(totals.get("operCount")).isNull();
    assertThat(totals.get("userCount")).isEqualTo(10L);

    @SuppressWarnings("unchecked")
    Map<String, Object> today = (Map<String, Object>) data.get("today");
    assertThat(today.get("loginSuccess")).isNull();
    assertThat(today.get("loginFail")).isNull();
    assertThat(today.get("operCount")).isNull();

    assertThat(data.get("loginTrend")).isNull();
    assertThat(data.get("moduleDistribution")).isNull();
    assertThat(data.get("recentLogins")).isNull();
    assertThat(data.get("recentOpers")).isNull();

    // 非受控数据块不受影响
    assertThat((List<?>) data.get("deptDistribution")).isEmpty();
    assertThat(data.get("aiUsage")).isNotNull();

    // 无监控权限不触发日志表任何查询
    verify(loginLogMapper, never()).selectCount(any());
    verify(loginLogMapper, never()).selectList(any());
    verify(operLogMapper, never()).selectCount(any());
    verify(operLogMapper, never()).selectList(any());
    verify(operLogMapper, never()).selectMaps(any());
  }
}
