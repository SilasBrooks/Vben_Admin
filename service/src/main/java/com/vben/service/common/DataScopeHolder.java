package com.vben.service.common;

import java.util.Set;

/**
 * 数据范围过滤上下文（ThreadLocal）。
 * 由 {@link DataScopeAspect} 在进入标注 @DataScope 的方法前计算并放入，
 * 方法返回后由切面 finally 清理。业务查询代码读取后追加 WHERE 条件。
 */
public final class DataScopeHolder {

  /** 过滤类型 */
  public enum Type {
    /** 全部数据：不附加任何条件 */
    ALL,
    /** 部门范围：deptIds 内（含/不含未归属），另附本人兜底 */
    DEPT,
    /** 仅本人：只看本人关联数据 */
    SELF
  }

  /**
   * @param type              过滤类型
   * @param deptIds           可见部门 id 集合（含子孙部门展开，DEPT 时使用）
   * @param includeUnassigned 是否包含未归属部门的数据（dept_id IS NULL），DEPT 时恒为 true（防数据"消失"）
   * @param userId            当前用户 id（本人兜底 / SELF 时使用）
   */
  public record DataScopeInfo(Type type, Set<Long> deptIds, boolean includeUnassigned, Long userId) {
  }

  private static final ThreadLocal<DataScopeInfo> HOLDER = new ThreadLocal<>();

  private DataScopeHolder() {
  }

  /** 供 DataScopeService（跨包）写入解析结果；切面 finally 时调用 clear 清理 */
  public static void set(DataScopeInfo info) {
    HOLDER.set(info);
  }

  /** 当前请求的数据范围上下文；未经过切面进入（如内部调用）时返回 null = 不过滤 */
  public static DataScopeInfo get() {
    return HOLDER.get();
  }

  static void clear() {
    HOLDER.remove();
  }
}
