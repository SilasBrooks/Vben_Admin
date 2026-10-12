package com.vben.service.module.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vben.service.common.BizException;
import com.vben.service.common.storage.StorageService;
import com.vben.service.common.storage.StoredFile;
import com.vben.service.module.ai.skill.mapper.AgentSkillMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Agent 技能服务单测：字段校验（name 正则/长度上限）、name 唯一、
 * 内置技能禁删、启用数量配额、编辑不改启用状态；
 * 技能包上传（全有或全无）、文件清单/读取、删除清理存储。
 */
class AgentSkillServiceTest {

  private final AgentSkillMapper mapper = Mockito.mock(AgentSkillMapper.class);
  private final StorageService storageService = Mockito.mock(StorageService.class);
  private final AgentSkillService service = new AgentSkillService(mapper, storageService);

  private AgentSkill skill(String name) {
    AgentSkill s = new AgentSkill();
    s.setName(name);
    s.setTitle("写作助手");
    s.setDescription("当用户需要撰写公告时使用");
    s.setContent("# 指引\n先明确受众，再动笔。");
    return s;
  }

  /** 内存 zip 构造：条目名 → 文本内容 */
  private byte[] zip(Map.Entry<String, String>... files) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos)) {
      for (Map.Entry<String, String> f : files) {
        zos.putNextEntry(new ZipEntry(f.getKey()));
        zos.write(f.getValue().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
    }
    return bos.toByteArray();
  }

  private static final String SKILL_MD = """
      ---
      name: code-reviewer
      description: 当用户需要评审代码时使用
      ---
      # 评审指引
      """;

  // ------------------------------------------------------------------
  // 新增校验
  // ------------------------------------------------------------------

  @Test
  void createNormalizesBuiltInAndEnabled() {
    when(mapper.selectCount(any())).thenReturn(0L);
    AgentSkill input = skill("demo-writer");
    input.setBuiltIn(1);
    when(mapper.selectById(any())).thenReturn(input);

    service.create(input);

    // 内置标记与启用状态不允许客户端指定
    assertThat(input.getBuiltIn()).isZero();
    assertThat(input.getEnabled()).isZero();
    verify(mapper).insert(input);
  }

  @Test
  void createRejectsBadNameFormat() {
    assertThatThrownBy(() -> service.create(skill("Bad Name"))).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.create(skill("-lead"))).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.create(skill("大写"))).isInstanceOf(BizException.class);
  }

  @Test
  void createRejectsMissingFields() {
    AgentSkill s = new AgentSkill();
    s.setName("demo");
    assertThatThrownBy(() -> service.create(s)).isInstanceOf(BizException.class);
  }

  @Test
  void createRejectsLengthOverflow() {
    AgentSkill desc = skill("demo");
    desc.setDescription("长".repeat(AgentSkillService.MAX_DESCRIPTION_CHARS + 1));
    assertThatThrownBy(() -> service.create(desc)).isInstanceOf(BizException.class);

    AgentSkill content = skill("demo");
    content.setContent("长".repeat(AgentSkillService.MAX_CONTENT_CHARS + 1));
    assertThatThrownBy(() -> service.create(content)).isInstanceOf(BizException.class);

    AgentSkill title = skill("demo");
    title.setTitle("标".repeat(65));
    assertThatThrownBy(() -> service.create(title)).isInstanceOf(BizException.class);
  }

  @Test
  void createRejectsDuplicateName() {
    when(mapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.create(skill("demo"))).isInstanceOf(BizException.class);
  }

  // ------------------------------------------------------------------
  // 删除：内置禁删
  // ------------------------------------------------------------------

  @Test
  void deleteRejectsBuiltInSkill() {
    AgentSkill builtIn = skill("core");
    builtIn.setId(1L);
    builtIn.setBuiltIn(1);
    when(mapper.selectById(1L)).thenReturn(builtIn);

    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BizException.class);
    verify(mapper, never()).deleteById(1L);
  }

  @Test
  void deleteAllowsCustomSkill() {
    AgentSkill custom = skill("mine");
    custom.setId(2L);
    custom.setBuiltIn(0);
    when(mapper.selectById(2L)).thenReturn(custom);

    service.delete(2L);
    verify(mapper).deleteById(2L);
  }

  // ------------------------------------------------------------------
  // 启停配额
  // ------------------------------------------------------------------

  @Test
  void enableOverQuotaRejected() {
    AgentSkill s = skill("extra");
    s.setId(3L);
    s.setEnabled(0);
    when(mapper.selectById(3L)).thenReturn(s);
    when(mapper.selectCount(any())).thenReturn((long) AgentSkillService.MAX_ENABLED_SKILLS);

    assertThatThrownBy(() -> service.setEnabled(3L, true)).isInstanceOf(BizException.class);
    verify(mapper, never()).updateById(any(AgentSkill.class));
  }

  @Test
  void enableAlreadyEnabledSkipsQuotaCheck() {
    AgentSkill s = skill("on");
    s.setId(4L);
    s.setEnabled(1);
    when(mapper.selectById(4L)).thenReturn(s);

    service.setEnabled(4L, true);

    assertThat(s.getEnabled()).isEqualTo(1);
    verify(mapper).updateById(s);
  }

  @Test
  void disableAlwaysAllowed() {
    AgentSkill s = skill("off");
    s.setId(5L);
    s.setEnabled(1);
    when(mapper.selectById(5L)).thenReturn(s);

    service.setEnabled(5L, false);

    assertThat(s.getEnabled()).isZero();
    verify(mapper).updateById(s);
  }

  // ------------------------------------------------------------------
  // 编辑：不改启用状态（启停走 setEnabled）
  // ------------------------------------------------------------------

  @Test
  void updateKeepsEnabledStateAndAllowsBuiltIn() {
    AgentSkill exists = skill("core");
    exists.setId(1L);
    exists.setBuiltIn(1);
    exists.setEnabled(1);
    when(mapper.selectById(1L)).thenReturn(exists);
    when(mapper.selectCount(any())).thenReturn(0L);

    AgentSkill patch = skill("core");
    patch.setTitle("新标题");
    patch.setEnabled(0);

    service.update(1L, patch);

    assertThat(exists.getTitle()).isEqualTo("新标题");
    assertThat(exists.getEnabled()).isEqualTo(1);
    assertThat(exists.getBuiltIn()).isEqualTo(1);
    verify(mapper).updateById(exists);
  }

  // ------------------------------------------------------------------
  // 技能包上传（全有或全无）
  // ------------------------------------------------------------------

  @Test
  void uploadPackageCreatesSkillAndStoresZip() throws IOException {
    byte[] zipBytes = zip(Map.entry("code-reviewer/SKILL.md", SKILL_MD),
        Map.entry("code-reviewer/references/spec.md", "# 规格"));
    when(mapper.selectCount(any())).thenReturn(0L);
    when(storageService.store(any(), any(), anyLong()))
        .thenReturn(new StoredFile("skill/pkg.zip", zipBytes.length));
    // 捕获 insert 的实体，selectById 回读同一实例（校验 service 落库前的真实字段）
    AgentSkill[] inserted = new AgentSkill[1];
    doAnswer(inv -> {
      AgentSkill s = inv.getArgument(0);
      s.setId(9L);
      inserted[0] = s;
      return 1;
    }).when(mapper).insert(any(AgentSkill.class));
    when(mapper.selectById(9L)).thenAnswer(inv -> inserted[0]);

    AgentSkill created = service.uploadPackage(
        new ByteArrayInputStream(zipBytes), "code-reviewer.zip", zipBytes.length);

    // frontmatter 驱动创建：默认停用、无内置标记、title 以 name 兜底、包三元组落库
    assertThat(created.getName()).isEqualTo("code-reviewer");
    assertThat(created.getTitle()).isEqualTo("code-reviewer");
    assertThat(created.getDescription()).isEqualTo("当用户需要评审代码时使用");
    assertThat(created.getContent()).startsWith("# 评审指引");
    assertThat(created.getEnabled()).isZero();
    assertThat(created.getBuiltIn()).isZero();
    assertThat(created.getPackageFileKey()).isEqualTo("skill/pkg.zip");
    assertThat(created.getPackageFileName()).isEqualTo("code-reviewer.zip");
    verify(storageService).store(any(), any(), anyLong());
  }

  @Test
  void uploadPackageRejectsDuplicateNameWithoutStoring() throws IOException {
    byte[] zipBytes = zip(Map.entry("code-reviewer/SKILL.md", SKILL_MD));
    when(mapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.uploadPackage(
        new ByteArrayInputStream(zipBytes), "pkg.zip", zipBytes.length))
        .isInstanceOf(BizException.class);
    // 全有或全无：重名拒绝时不落存储不建记录
    verify(storageService, never()).store(any(), any(), anyLong());
    verify(mapper, never()).insert(any(AgentSkill.class));
  }

  @Test
  void uploadPackageRejectsTraversalWithoutStoring() throws IOException {
    byte[] zipBytes = zip(Map.entry("code-reviewer/SKILL.md", SKILL_MD),
        Map.entry("../evil.sh", "x"));

    assertThatThrownBy(() -> service.uploadPackage(
        new ByteArrayInputStream(zipBytes), "evil.zip", zipBytes.length))
        .isInstanceOf(BizException.class);
    verify(storageService, never()).store(any(), any(), anyLong());
  }

  @Test
  void uploadPackageRejectsOversizeAndBadExt() throws IOException {
    assertThatThrownBy(() -> service.uploadPackage(
        new ByteArrayInputStream(new byte[1]), "pkg.zip",
        AgentSkillService.MAX_PACKAGE_BYTES + 1))
        .isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.uploadPackage(
        new ByteArrayInputStream(new byte[1]), "pkg.txt", 1))
        .isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.uploadPackage(
        new ByteArrayInputStream(new byte[1]), "pkg.zip", 0))
        .isInstanceOf(BizException.class);
  }

  @Test
  void uploadPackageCompensatesStorageWhenInsertFails() throws IOException {
    byte[] zipBytes = zip(Map.entry("code-reviewer/SKILL.md", SKILL_MD));
    when(mapper.selectCount(any())).thenReturn(0L);
    when(storageService.store(any(), any(), anyLong()))
        .thenReturn(new StoredFile("skill/pkg.zip", 10));
    when(mapper.insert(any(AgentSkill.class))).thenThrow(new RuntimeException("db down"));

    assertThatThrownBy(() -> service.uploadPackage(
        new ByteArrayInputStream(zipBytes), "pkg.zip", zipBytes.length))
        .hasMessage("db down");
    // 落库失败补偿删除存储，不留孤儿文件
    verify(storageService).delete("skill/pkg.zip");
  }

  // ------------------------------------------------------------------
  // 包文件清单 / 读取 / 下载 / 删除清理
  // ------------------------------------------------------------------

  private AgentSkill packagedSkill(long id) {
    AgentSkill s = skill("code-reviewer");
    s.setId(id);
    s.setPackageFileKey("skill/pkg.zip");
    s.setPackageFileName("pkg.zip");
    return s;
  }

  @Test
  void listPackageFilesReturnsEntriesAndRejectsNoPackage() throws IOException {
    byte[] zipBytes = zip(Map.entry("code-reviewer/SKILL.md", SKILL_MD),
        Map.entry("code-reviewer/assets/logo.png", "bin"));
    AgentSkill withPackage = packagedSkill(5L);
    when(mapper.selectById(5L)).thenReturn(withPackage);
    when(storageService.open("skill/pkg.zip"))
        .thenAnswer(inv -> new ByteArrayInputStream(zipBytes));

    var entries = service.listPackageFiles(5L);
    assertThat(entries).extracting(SkillPackageParser.PackageEntry::path)
        .containsExactly("code-reviewer/SKILL.md", "code-reviewer/assets/logo.png");

    // 无包技能（含内置种子）404
    AgentSkill noPackage = skill("builtin");
    noPackage.setId(6L);
    when(mapper.selectById(6L)).thenReturn(noPackage);
    assertThatThrownBy(() -> service.listPackageFiles(6L))
        .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
  }

  @Test
  void readPackageFileExtractsTextWithTruncation() throws IOException {
    String big = "长".repeat(AgentSkillService.MAX_AI_FILE_CHARS + 100);
    byte[] zipBytes = zip(Map.entry("code-reviewer/SKILL.md", SKILL_MD),
        Map.entry("code-reviewer/references/big.md", big),
        Map.entry("code-reviewer/assets/logo.png", "\u0000PNG"));
    AgentSkill withPackage = packagedSkill(5L);
    when(mapper.selectById(5L)).thenReturn(withPackage);
    // 每次读取给全新流（ZipInputStream 是一次性消费）
    when(storageService.open("skill/pkg.zip"))
        .thenAnswer(inv -> new ByteArrayInputStream(zipBytes));

    var text = service.readPackageFile(5L, "code-reviewer/references/big.md");
    assertThat(text.text()).isTrue();
    assertThat(text.textContent()).contains("已截断");
    assertThat(text.textContent().length()).isLessThan(big.length());

    var binary = service.readPackageFile(5L, "code-reviewer/assets/logo.png");
    assertThat(binary.text()).isFalse();
    assertThat(binary.textContent()).isNull();

    // 路径不存在：404 且不泄露包外文件
    assertThatThrownBy(() -> service.readPackageFile(5L, "../outside.md"))
        .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
    assertThatThrownBy(() -> service.readPackageFile(5L, "no/such.md"))
        .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
  }

  @Test
  void openPackageReturnsStreamAndFileName() throws IOException {
    byte[] zipBytes = zip(Map.entry("code-reviewer/SKILL.md", SKILL_MD));
    when(mapper.selectById(5L)).thenReturn(packagedSkill(5L));
    when(storageService.open("skill/pkg.zip"))
        .thenReturn(new ByteArrayInputStream(zipBytes));

    var download = service.openPackage(5L);
    assertThat(download.fileName()).isEqualTo("pkg.zip");
    assertThat(download.stream().readAllBytes()).isEqualTo(zipBytes);
  }

  @Test
  void deleteCleansPackageStorageBestEffort() {
    AgentSkill withPackage = packagedSkill(7L);
    when(mapper.selectById(7L)).thenReturn(withPackage);
    doThrow(new RuntimeException("minio down")).when(storageService).delete("skill/pkg.zip");

    // 清理失败不阻断删除
    service.delete(7L);
    verify(mapper).deleteById(7L);
  }
}
