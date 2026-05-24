package dev.dong4j.idea.skillsjars.helper.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SkillCoordinate} 单元测试.
 *
 * @author dong4j
 */
class SkillCoordinateTest {

    @Test
    @DisplayName("可以从 IDEA Maven 库名解析坐标")
    void should_parse_maven_library_name() {
        SkillCoordinate c = SkillCoordinate.fromLibraryName("Maven: dev.dong4j:zeka-skills:1.0.0");
        assertThat(c.getGroupId()).isEqualTo("dev.dong4j");
        assertThat(c.getArtifactId()).isEqualTo("zeka-skills");
        assertThat(c.getVersion()).isEqualTo("1.0.0");
        assertThat(c.isComplete()).isTrue();
    }

    @Test
    @DisplayName("可以从 IDEA Gradle 库名解析坐标")
    void should_parse_gradle_library_name() {
        SkillCoordinate c = SkillCoordinate.fromLibraryName("Gradle: foo:bar:2.1");
        assertThat(c.getGroupId()).isEqualTo("foo");
        assertThat(c.getArtifactId()).isEqualTo("bar");
        assertThat(c.getVersion()).isEqualTo("2.1");
    }

    @Test
    @DisplayName("空或不完整的库名返回 unknown")
    void should_return_unknown_when_invalid() {
        assertThat(SkillCoordinate.fromLibraryName(null).isComplete()).isFalse();
        assertThat(SkillCoordinate.fromLibraryName("").isComplete()).isFalse();
        assertThat(SkillCoordinate.fromLibraryName("foo").isComplete()).isFalse();
        assertThat(SkillCoordinate.fromLibraryName("foo:bar").isComplete()).isFalse();
    }

    @Test
    @DisplayName("toCoordinateString 用 ? 占位缺失字段")
    void should_format_with_question_mark() {
        assertThat(SkillCoordinate.unknown().toCoordinateString()).isEqualTo("?:?:?");
        assertThat(SkillCoordinate.of("g", null, "v").toCoordinateString()).isEqualTo("g:?:v");
    }

    @Test
    @DisplayName("equals 与 hashCode 基于三段坐标")
    void should_equal_by_three_parts() {
        SkillCoordinate a = SkillCoordinate.of("g", "a", "1");
        SkillCoordinate b = SkillCoordinate.of("g", "a", "1");
        SkillCoordinate c = SkillCoordinate.of("g", "a", "2");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("拥有额外段时只取前 3 段")
    void should_truncate_extra_segments() {
        SkillCoordinate c = SkillCoordinate.fromLibraryName("Maven: g:a:1.0:test");
        assertThat(c.getGroupId()).isEqualTo("g");
        assertThat(c.getArtifactId()).isEqualTo("a");
        assertThat(c.getVersion()).isEqualTo("1.0");
    }
}
