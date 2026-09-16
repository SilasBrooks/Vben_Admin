package com.vben.service.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.module.system.entity.SysDictData;
import com.vben.service.module.system.entity.SysDictType;
import com.vben.service.module.system.service.SysDictAdminService;
import com.vben.service.security.RequirePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据字典接口（类型管理 + 数据管理 + 下拉选项）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:Dict:List   类型/数据查询</li>
 *   <li>System:Dict:Add    新增</li>
 *   <li>System:Dict:Edit   编辑</li>
 *   <li>System:Dict:Delete 删除</li>
 * </ul>
 *
 * <p>options 接口仅要求登录（业务下拉人人可用，不含敏感信息）。
 */
@RestController
@Tag(name = "数据字典", description = "字典类型与字典项管理")
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class SystemDictController {

  private final SysDictAdminService dictService;

  // ------------------------------------------------------------------
  // 字典类型
  // ------------------------------------------------------------------

  /** 全部字典类型 */
  @RequirePermission("System:Dict:List")
  @GetMapping("/type/list")
  public R<List<SysDictType>> typeList() {
    return R.ok(dictService.typeList());
  }

  /** 新增类型（dictType 全局唯一） */
  @OperLog(module = "字典管理", description = "新增字典类型")
  @RequirePermission("System:Dict:Add")
  @PostMapping("/type/save")
  public R<Void> saveType(@RequestBody SysDictType type) {
    dictService.saveType(type);
    return R.ok();
  }

  /** 编辑类型（改 dictType 键会同步迁移数据项归属） */
  @OperLog(module = "字典管理", description = "编辑字典类型")
  @RequirePermission("System:Dict:Edit")
  @PutMapping("/type/update")
  public R<Void> updateType(@RequestBody SysDictType type) {
    dictService.updateType(type);
    return R.ok();
  }

  /** 删除类型（级联删除其全部数据项） */
  @OperLog(module = "字典管理", description = "删除字典类型")
  @RequirePermission("System:Dict:Delete")
  @DeleteMapping("/type/{id}")
  public R<Void> removeType(@PathVariable Long id) {
    dictService.removeType(id);
    return R.ok();
  }

  // ------------------------------------------------------------------
  // 字典数据
  // ------------------------------------------------------------------

  /** 按类型分页查询数据项 */
  @RequirePermission("System:Dict:List")
  @GetMapping("/data/list")
  public R<Map<String, Object>> dataList(
      @RequestParam String dictType,
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize) {

    IPage<SysDictData> page = dictService.dataPage(pageNo, pageSize, dictType);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 新增数据项（同类型下 dictValue 唯一） */
  @OperLog(module = "字典管理", description = "新增字典数据")
  @RequirePermission("System:Dict:Add")
  @PostMapping("/data/save")
  public R<Void> saveData(@RequestBody SysDictData data) {
    dictService.saveData(data);
    return R.ok();
  }

  /** 编辑数据项 */
  @OperLog(module = "字典管理", description = "编辑字典数据")
  @RequirePermission("System:Dict:Edit")
  @PutMapping("/data/update")
  public R<Void> updateData(@RequestBody SysDictData data) {
    dictService.updateData(data);
    return R.ok();
  }

  /** 删除数据项 */
  @OperLog(module = "字典管理", description = "删除字典数据")
  @RequirePermission("System:Dict:Delete")
  @DeleteMapping("/data/{id}")
  public R<Void> removeData(@PathVariable Long id) {
    dictService.removeData(id);
    return R.ok();
  }

  // ------------------------------------------------------------------
  // 下拉选项（仅要求登录）
  // ------------------------------------------------------------------

  /** 启用状态选项（label/value，sort_num 升序），供业务表单下拉取值 */
  @GetMapping("/data/options/{dictType}")
  public R<List<SysDictData>> options(@PathVariable String dictType) {
    return R.ok(dictService.options(dictType));
  }
}
