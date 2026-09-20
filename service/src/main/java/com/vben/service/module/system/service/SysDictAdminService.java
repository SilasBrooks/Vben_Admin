package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.BizException;
import com.vben.service.module.system.entity.SysDictData;
import com.vben.service.module.system.entity.SysDictType;
import com.vben.service.module.system.mapper.SysDictDataMapper;
import com.vben.service.module.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据字典：类型管理（dictType 唯一、删除级联删数据）+ 数据管理（同类型 value 唯一）+ 下拉选项
 */
@Service
@RequiredArgsConstructor
public class SysDictAdminService {

  private final SysDictTypeMapper typeMapper;
  private final SysDictDataMapper dataMapper;

  // ------------------------------------------------------------------
  // 字典类型
  // ------------------------------------------------------------------

  /** 全部类型（量小不分页） */
  public List<SysDictType> typeList() {
    QueryWrapper<SysDictType> q = new QueryWrapper<>();
    q.orderByAsc("id");
    return typeMapper.selectList(q);
  }

  @Transactional
  public void saveType(SysDictType type) {
    requireTypeName(type.getDictName());
    requireTypeKey(type.getDictType());
    if (typeMapper.selectCount(new QueryWrapper<SysDictType>()
        .eq("dict_type", type.getDictType())) > 0) {
      throw BizException.badRequest("error.dict.type.exists", type.getDictType());
    }
    type.setId(null);
    type.setCreateTime(LocalDateTime.now());
    type.setUpdateTime(LocalDateTime.now());
    typeMapper.insert(type);
  }

  @Transactional
  public void updateType(SysDictType type) {
    requireTypeName(type.getDictName());
    requireTypeKey(type.getDictType());
    SysDictType exist = typeMapper.selectById(type.getId());
    if (exist == null) {
      throw BizException.badRequest("error.dict.type.notFound");
    }
    // dictType 唯一（排除自身）
    Long dup = typeMapper.selectCount(new QueryWrapper<SysDictType>()
        .eq("dict_type", type.getDictType())
        .ne("id", type.getId()));
    if (dup > 0) {
      throw BizException.badRequest("error.dict.type.exists", type.getDictType());
    }
    // 若修改了 dictType 键，同步更新数据项归属
    if (!exist.getDictType().equals(type.getDictType())) {
      SysDictData upd = new SysDictData();
      upd.setDictType(type.getDictType());
      dataMapper.update(upd, new QueryWrapper<SysDictData>()
          .eq("dict_type", exist.getDictType()));
    }
    exist.setDictName(type.getDictName());
    exist.setDictType(type.getDictType());
    exist.setStatus(type.getStatus());
    exist.setRemark(type.getRemark());
    exist.setUpdateTime(LocalDateTime.now());
    typeMapper.updateById(exist);
  }

  /** 删除类型（同事务级联删除其数据项） */
  @Transactional
  public void removeType(Long id) {
    SysDictType exist = typeMapper.selectById(id);
    if (exist == null) {
      throw BizException.badRequest("error.dict.type.notFound");
    }
    dataMapper.delete(new QueryWrapper<SysDictData>().eq("dict_type", exist.getDictType()));
    typeMapper.deleteById(id);
  }

  // ------------------------------------------------------------------
  // 字典数据
  // ------------------------------------------------------------------

  /** 按类型分页 */
  public IPage<SysDictData> dataPage(long pageNo, long pageSize, String dictType) {
    QueryWrapper<SysDictData> q = new QueryWrapper<>();
    q.eq("dict_type", dictType).orderByAsc("sort_num").orderByAsc("id");
    return dataMapper.selectPage(new Page<>(pageNo, pageSize), q);
  }

  @Transactional
  public void saveData(SysDictData data) {
    requireDataLabel(data.getDictLabel());
    requireDataValue(data.getDictValue());
    requireDataBelong(data.getDictType());
    if (dataMapper.selectCount(new QueryWrapper<SysDictData>()
        .eq("dict_type", data.getDictType())
        .eq("dict_value", data.getDictValue())) > 0) {
      throw BizException.badRequest("error.dict.value.exists", data.getDictValue());
    }
    if (data.getSortNum() == null) {
      data.setSortNum(0);
    }
    if (data.getStatus() == null) {
      data.setStatus(0);
    }
    data.setId(null);
    data.setCreateTime(LocalDateTime.now());
    dataMapper.insert(data);
  }

  @Transactional
  public void updateData(SysDictData data) {
    requireDataLabel(data.getDictLabel());
    requireDataValue(data.getDictValue());
    SysDictData exist = dataMapper.selectById(data.getId());
    if (exist == null) {
      throw BizException.badRequest("error.dict.data.notFound");
    }
    // dictValue 同类型下唯一（排除自身）
    Long dup = dataMapper.selectCount(new QueryWrapper<SysDictData>()
        .eq("dict_type", exist.getDictType())
        .eq("dict_value", data.getDictValue())
        .ne("id", data.getId()));
    if (dup > 0) {
      throw BizException.badRequest("error.dict.value.exists", data.getDictValue());
    }
    exist.setDictLabel(data.getDictLabel());
    exist.setDictValue(data.getDictValue());
    exist.setSortNum(data.getSortNum());
    exist.setStatus(data.getStatus());
    exist.setRemark(data.getRemark());
    dataMapper.updateById(exist);
  }

  public void removeData(Long id) {
    dataMapper.deleteById(id);
  }

  // ------------------------------------------------------------------
  // 下拉选项（仅要求登录，供业务表单取值）
  // ------------------------------------------------------------------

  /** 启用状态选项，sort_num 升序 */
  public List<SysDictData> options(String dictType) {
    return dataMapper.selectList(new QueryWrapper<SysDictData>()
        .eq("dict_type", dictType)
        .eq("status", 0)
        .orderByAsc("sort_num")
        .orderByAsc("id"));
  }

  // ------------------------------------------------------------------
  // 入参基础校验（fail-fast）
  // ------------------------------------------------------------------

  private void requireTypeKey(String dictType) {
    if (dictType == null || dictType.isBlank()) {
      throw BizException.badRequest("error.dict.typeKey.blank");
    }
  }

  private void requireTypeName(String dictName) {
    if (dictName == null || dictName.isBlank()) {
      throw BizException.badRequest("error.dict.name.blank");
    }
  }

  private void requireDataLabel(String label) {
    if (label == null || label.isBlank()) {
      throw BizException.badRequest("error.dict.label.blank");
    }
  }

  private void requireDataValue(String value) {
    if (value == null || value.isBlank()) {
      throw BizException.badRequest("error.dict.value.blank");
    }
  }

  /** 数据必须归属已存在的类型 */
  private void requireDataBelong(String dictType) {
    if (dictType == null || dictType.isBlank()
        || typeMapper.selectCount(new QueryWrapper<SysDictType>()
            .eq("dict_type", dictType)) == 0) {
      throw BizException.badRequest("error.dict.type.notFoundWith", dictType);
    }
  }
}
