package com.vben.service.module.ai.kb;

import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.common.idempotent.Idempotent;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.module.ai.kb.entity.KbDoc;
import com.vben.service.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库接口（系统管理-知识库）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>Kb:Doc:List    列表 / 检索测试</li>
 *   <li>Kb:Doc:Upload  上传文档</li>
 *   <li>Kb:Doc:Delete  删除文档</li>
 * </ul>
 */
@RestController
@Tag(name = "知识库", description = "知识库文档上传/切块向量化/检索测试（AI 助手 RAG 数据底座）")
@RequestMapping("/system/kb")
@RequiredArgsConstructor
public class KbController {

  private final KbDocService docService;
  private final KbSearchService searchService;
  private final KbUploadService uploadService;

  /** 分页列表：文件名模糊过滤，状态跟踪（处理中/完成/失败 + 阶段进度 + 原因） */
  @Operation(summary = "文档列表", description = "文件名模糊过滤 + 分页；含处理状态、阶段/进度与失败原因")
  @RequirePermission("Kb:Doc:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String fileName) {
    var page = docService.page(pageNo, pageSize, fileName);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 上传文档（md/txt/pdf/docx ≤10MB）：受理后异步切块向量化，接口立即返回 */
  @Operation(summary = "上传文档", description = "md/txt/pdf/docx，10MB 上限（更大文件走分片接口）；受理后异步向量化")
  @RequirePermission("Kb:Doc:Upload")
  @RateLimit(name = "kb:upload", limit = 10, windowSeconds = 60)
  @Idempotent(name = "kb:upload", intervalSeconds = 3)
  @PostMapping("/upload")
  public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
    Long id = docService.upload(file);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", id);
    return R.ok(data);
  }

  /** 分片上传-领取会话：幂等；SHA-256 命中已入库文件直接秒传建文档 */
  @Operation(summary = "分片上传-领取会话", description = ">10MB 大文件专用；幂等复用进行中会话（断点续传），秒传命中返回 instant=true")
  @RequirePermission("Kb:Doc:Upload")
  @RateLimit(name = "kb:chunk", limit = 30, windowSeconds = 60)
  @PostMapping("/doc/upload/init")
  public R<Map<String, Object>> initUpload(@RequestBody KbUploadService.InitReq req) {
    return R.ok(uploadService.init(req));
  }

  /** 分片上传-上传单片：流式落盘，jsonb 原子追加索引（可并发） */
  @Operation(summary = "分片上传-上传单片", description = "index 从 0 起；单片大小须与 init 声明一致（末片除外）")
  @RequirePermission("Kb:Doc:Upload")
  @RateLimit(name = "kb:chunk", limit = 60, windowSeconds = 60)
  @PostMapping("/doc/upload/{uploadId}/chunk")
  public R<Void> uploadChunk(@PathVariable String uploadId,
                             @RequestParam("index") int index,
                             @RequestParam("chunk") MultipartFile chunk) {
    uploadService.chunk(uploadId, index, chunk);
    return R.ok();
  }

  /** 分片上传-完成：顺序合并 + 整文件 SHA-256 校验 + 入库建文档入管线 */
  @Operation(summary = "分片上传-完成", description = "合并校验失败或分片不全会清理会话，需重新上传")
  @RequirePermission("Kb:Doc:Upload")
  @RateLimit(name = "kb:chunk", limit = 30, windowSeconds = 60)
  @PostMapping("/doc/upload/{uploadId}/complete")
  public R<Map<String, Object>> completeUpload(@PathVariable String uploadId) {
    Long id = uploadService.complete(uploadId);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", id);
    return R.ok(data);
  }

  /** 分片上传-放弃：清理会话与临时分片（幂等） */
  @Operation(summary = "分片上传-放弃", description = "删除上传会话与已传分片；会话不存在时静默成功")
  @RequirePermission("Kb:Doc:Upload")
  @RateLimit(name = "kb:chunk", limit = 30, windowSeconds = 60)
  @DeleteMapping("/doc/upload/{uploadId}")
  public R<Void> abortUpload(@PathVariable String uploadId) {
    uploadService.abort(uploadId);
    return R.ok();
  }

  /** 失败文档重试：从 processed_chunks 断点续跑，不从头开始 */
  @Operation(summary = "重试失败文档", description = "仅失败状态可重试；从已入库块之后续跑向量化")
  @RequirePermission("Kb:Doc:Upload")
  @RateLimit(name = "kb:retry", limit = 10, windowSeconds = 60)
  @PostMapping("/doc/{id}/retry")
  public R<Void> retry(@PathVariable Long id) {
    docService.retry(id);
    return R.ok();
  }

  /** 删除文档：级联清理向量块与存储引用（处理中拒删） */
  @Operation(summary = "删除文档", description = "处理中的文档不可删除；删除级联清理全部向量块")
  @RequirePermission("Kb:Doc:Delete")
  @OperLog(module = "知识库", description = "删除知识库文档")
  @DeleteMapping("/{id}")
  public R<Void> delete(@PathVariable Long id) {
    docService.delete(id);
    return R.ok();
  }

  /** 检索测试：向量检索 Top-K 相似片段（管理页调试入口，与 AI 工具同链路） */
  @Operation(summary = "检索测试", description = "输入问题返回 Top-K 相似片段（文档名/块序号/内容/得分）")
  @RequirePermission("Kb:Doc:List")
  @RateLimit(name = "kb:search", limit = 20, windowSeconds = 60)
  @PostMapping("/search")
  public R<List<KbSearchHit>> search(@RequestBody SearchReq req) {
    return R.ok(searchService.search(req.query(), req.topK()));
  }

  /** 检索请求体 */
  public record SearchReq(String query, Integer topK) {
  }
}
