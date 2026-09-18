package com.vben.service.module.system.controller;

import com.vben.service.common.BizException;
import com.vben.service.common.R;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.common.storage.StorageService;
import com.vben.service.module.system.entity.SysFile;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.service.SysFileService;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件管理接口。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:File:List    列表</li>
 *   <li>System:File:Upload  上传</li>
 *   <li>System:File:Delete  删除</li>
 * </ul>
 * 下载/预览 {@code GET /file/{id}/content} 登录即可（不开放匿名静态映射，防绕过认证）；
 * 头像上传 {@code POST /file/avatar} 登录即可（仅本人）。
 */
@RestController
@Tag(name = "文件管理", description = "文件上传、列表、删除、下载预览与头像上传")
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

  private final SysFileService fileService;
  private final SysUserMapper userMapper;
  private final StorageService storageService;

  /** 通用文件上传：扩展名白名单 + 10MB，随机存储名，记录上传人 */
  @Operation(summary = "上传文件", description = "扩展名白名单（图片/文档/压缩包），单文件最大 10MB；返回文件 id 与访问路径")
  @RequirePermission("System:File:Upload")
  @RateLimit(name = "file:upload", limit = 20, windowSeconds = 60)
  @com.vben.service.common.idempotent.Idempotent(name = "file:upload", intervalSeconds = 5)
  @PostMapping("/upload")
  public R<SysFileService.FileItem> upload(@RequestParam("file") MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw BizException.badRequest("请选择要上传的文件");
    }
    try (InputStream in = file.getInputStream()) {
      SysFile saved = fileService.upload(in, file.getOriginalFilename(), file.getSize(),
          LoginUserHolder.require().getUserId());
      return R.ok(SysFileService.FileItem.from(saved));
    }
  }

  /** 文件分页列表：原始名模糊 + 分页，按上传时间倒序 */
  @Operation(summary = "文件列表", description = "原始名模糊过滤 + 分页，按上传时间倒序")
  @RequirePermission("System:File:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String originalName) {
    var page = fileService.page(pageNo, pageSize, originalName);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 删除：记录与物理文件同步移除 */
  @Operation(summary = "删除文件", description = "同时移除 sys_file 记录与物理文件；物理文件不存在时容错")
  @RequirePermission("System:File:Delete")
  @DeleteMapping("/{id}")
  public R<Void> delete(@PathVariable Long id) {
    fileService.delete(id);
    return R.ok();
  }

  /**
   * 下载/预览：登录即可。按记录的 content_type 输出；图片 inline，其余 attachment。
   * 记录不存在或物理文件缺失返回 404。
   */
  @Operation(summary = "下载/预览文件", description = "登录即可访问；图片内联呈现，其余以附件下载；文件不存在返回 404")
  @GetMapping("/{id}/content")
  public void content(@PathVariable Long id, HttpServletResponse response) throws IOException {
    SysFile file = fileService.getById(id);
    if (file == null) {
      response.sendError(404, "文件不存在");
      return;
    }
    InputStream in;
    try {
      in = storageService.open(file.getStorageKey());
    } catch (Exception e) {
      // 物理文件缺失/读取失败：与记录不一致，按 404 处理
      response.sendError(404, "文件不存在");
      return;
    }
    boolean inline = file.getContentType() != null && file.getContentType().startsWith("image/");
    response.setContentType(file.getContentType() != null
        ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
    String encodedName = URLEncoder.encode(
        file.getOriginalName() == null ? "file" : file.getOriginalName(), StandardCharsets.UTF_8);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encodedName);
    try (in) {
      in.transferTo(response.getOutputStream());
    }
  }

  /**
   * 头像上传：登录用户即可（无需权限码），仅图片白名单 + 5MB，只能改本人。
   * 成功后更新本人 sys_user.avatar = 文件 id。
   */
  @Operation(summary = "上传头像", description = "登录用户上传本人头像（仅图片，最大 5MB）；成功后更新本人 avatar")
  @RateLimit(name = "file:avatar", limit = 10, windowSeconds = 60)
  @PostMapping("/avatar")
  public R<SysFileService.FileItem> avatar(@RequestParam("file") MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw BizException.badRequest("请选择要上传的头像图片");
    }
    Long userId = LoginUserHolder.require().getUserId();
    SysFile saved;
    try (InputStream in = file.getInputStream()) {
      saved = fileService.uploadAvatar(in, file.getOriginalFilename(), file.getSize(), userId);
    }
    SysUser patch = new SysUser();
    patch.setId(userId);
    patch.setAvatar(String.valueOf(saved.getId()));
    userMapper.updateById(patch);
    return R.ok(SysFileService.FileItem.from(saved));
  }
}
