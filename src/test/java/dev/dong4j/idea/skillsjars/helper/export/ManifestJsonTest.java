package dev.dong4j.idea.skillsjars.helper.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ManifestJson} 序列化 / 反序列化测试.
 */
class ManifestJsonTest {

    @Test
    @DisplayName("写出再读回, 字段值完全一致")
    void should_round_trip() {
        ManifestSchema original = sample();
        String json = ManifestJson.toJson(original);
        ManifestSchema parsed = ManifestJson.fromJson(json);

        assertThat(parsed).isNotNull();
        assertThat(parsed.getSchemaVersion()).isEqualTo(original.getSchemaVersion());
        assertThat(parsed.getManagedBy()).isEqualTo(original.getManagedBy());
        assertThat(parsed.getArtifact()).isEqualTo(original.getArtifact());
        assertThat(parsed.getSourceType()).isEqualTo(original.getSourceType());
        assertThat(parsed.getSkill()).isEqualTo(original.getSkill());
        assertThat(parsed.getSourceJarSha256()).isEqualTo(original.getSourceJarSha256());
        assertThat(parsed.getSkillRoot()).isEqualTo(original.getSkillRoot());
        assertThat(parsed.getInstalledAt()).isEqualTo(original.getInstalledAt());
        assertThat(parsed.getTargetAgent()).isEqualTo(original.getTargetAgent());
        assertThat(parsed.getTargetPath()).isEqualTo(original.getTargetPath());
        assertThat(parsed.getFiles()).hasSize(2);
        assertThat(parsed.getFiles().get(0).getPath()).isEqualTo("SKILL.md");
        assertThat(parsed.getFiles().get(1).getPath()).isEqualTo("examples/x.md");
    }

    @Test
    @DisplayName("解析失败返回 null")
    void should_return_null_for_invalid_json() {
        assertThat(ManifestJson.fromJson("not a json")).isNull();
        assertThat(ManifestJson.fromJson("{ broken")).isNull();
    }

    @Test
    @DisplayName("未知字段被宽容跳过")
    void should_tolerate_unknown_fields() {
        String json = """
            {
              "schemaVersion": 1,
              "managedBy": "skillsjars-helper",
              "futureField": "ignored",
              "artifact": "g:a:1",
              "sourceType": "MAVEN_DEPENDENCY",
              "skill": "x",
              "sourceJarSha256": "abc",
              "skillRoot": "META-INF/skills/x/",
              "installedAt": "2026-01-01T00:00:00+08:00",
              "targetAgent": "claude",
              "targetPath": "/tmp/x",
              "files": []
            }
            """;
        ManifestSchema m = ManifestJson.fromJson(json);
        assertThat(m).isNotNull();
        assertThat(m.getSkill()).isEqualTo("x");
    }

    @Test
    @DisplayName("isManagedByThisPlugin 仅认 skillsjars-helper 串")
    void should_recognize_managed_by() {
        ManifestSchema mine = sample();
        assertThat(mine.isManagedByThisPlugin()).isTrue();

        ManifestSchema foreign = new ManifestSchema(
            1, "other-tool", "g:a:1", SkillSourceType.MAVEN_DEPENDENCY,
            "x", "sha", "META-INF/skills/x/", "now",
            "claude", "/tmp/x", List.of()
        );
        assertThat(foreign.isManagedByThisPlugin()).isFalse();
    }

    @Test
    @DisplayName("matchesSource 比较 (artifact, skillRoot)")
    void should_match_source() {
        ManifestSchema m = sample();
        assertThat(m.matchesSource("dev.dong4j:zeka-skills:1.0.0", "META-INF/skills/dev/dong4j/code-review/")).isTrue();
        assertThat(m.matchesSource("other:other:1", "META-INF/skills/dev/dong4j/code-review/")).isFalse();
        assertThat(m.matchesSource("dev.dong4j:zeka-skills:1.0.0", "META-INF/skills/foo/")).isFalse();
    }

    private static ManifestSchema sample() {
        return new ManifestSchema(
            1,
            "skillsjars-helper",
            "dev.dong4j:zeka-skills:1.0.0",
            SkillSourceType.MAVEN_DEPENDENCY,
            "code-review",
            "abc123",
            "META-INF/skills/dev/dong4j/code-review/",
            "2026-05-23T10:00:00+08:00",
            "claude",
            ".claude/skills/code-review",
            List.of(
                new ManifestSchema.FileEntry("SKILL.md", "shasha1", 100),
                new ManifestSchema.FileEntry("examples/x.md", "shasha2", 200)
            )
        );
    }
}
