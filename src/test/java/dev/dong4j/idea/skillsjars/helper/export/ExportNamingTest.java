package dev.dong4j.idea.skillsjars.helper.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExportNaming} 单元测试.
 *
 * <p>覆盖默认命名 + DUPLICATE_NAME fallback 命名 + 非法字符清理.</p>
 */
class ExportNamingTest {

    @Test
    @DisplayName("默认命名直接采用 frontmatter name")
    void should_use_frontmatter_name() {
        SkillDescriptor skill = newSkill("code-review");
        assertThat(ExportNaming.defaultDirectoryName(skill)).isEqualTo("code-review");
    }

    @Test
    @DisplayName("DUPLICATE_NAME fallback 拼接 artifactId 后缀")
    void should_fallback_with_artifact_id_suffix() {
        SkillDescriptor skill = newSkill("code-review");
        SkillCoordinate coord = SkillCoordinate.of("dev.dong4j", "zeka-skills", "1.0.0");
        assertThat(ExportNaming.duplicateFallbackDirectoryName(skill, coord))
            .isEqualTo("code-review__zeka-skills");
    }

    @Test
    @DisplayName("artifactId 缺失时 fallback 用 'source'")
    void should_use_source_suffix_when_artifact_id_missing() {
        SkillDescriptor skill = newSkill("code-review");
        SkillCoordinate coord = SkillCoordinate.unknown();
        assertThat(ExportNaming.duplicateFallbackDirectoryName(skill, coord))
            .endsWith("__source");
    }

    @Test
    @DisplayName("非法文件系统字符被替换为下划线")
    void should_sanitize_invalid_chars() {
        assertThat(ExportNaming.sanitize("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c_d_e_f_g_h_i_j");
    }

    @Test
    @DisplayName("空白名退化为 'skill'")
    void should_use_skill_when_blank() {
        assertThat(ExportNaming.sanitize("   ")).isEqualTo("skill");
    }

    private static SkillDescriptor newSkill(String name) {
        return new SkillDescriptor(
            name, null, List.of(), null,
            "META-INF/skills/foo/", "META-INF/skills/foo/SKILL.md", "",
            List.of()
        );
    }
}
