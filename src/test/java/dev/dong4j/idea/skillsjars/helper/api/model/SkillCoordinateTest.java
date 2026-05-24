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

    @Test
    @DisplayName("parseQuery: 二段输入按 (g,a) 通配版本")
    void should_parse_two_segment_query_as_version_wildcard() {
        SkillCoordinate q = SkillCoordinate.parseQuery("g:a");
        assertThat(q.getGroupId()).isEqualTo("g");
        assertThat(q.getArtifactId()).isEqualTo("a");
        assertThat(q.getVersion()).isNull();
    }

    @Test
    @DisplayName("parseQuery: 三段及以上精确匹配, 多余段忽略")
    void should_parse_three_segment_query_as_exact() {
        SkillCoordinate q = SkillCoordinate.parseQuery("g:a:1.0");
        assertThat(q.getGroupId()).isEqualTo("g");
        assertThat(q.getArtifactId()).isEqualTo("a");
        assertThat(q.getVersion()).isEqualTo("1.0");

        SkillCoordinate q2 = SkillCoordinate.parseQuery("g:a:1.0:jar:sources");
        assertThat(q2.getVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("parseQuery: 空或单段输入返回 unknown")
    void should_return_unknown_for_invalid_query() {
        assertThat(SkillCoordinate.parseQuery("").isComplete()).isFalse();
        assertThat(SkillCoordinate.parseQuery("solo").isComplete()).isFalse();
        // unknown 与任何完整坐标都不匹配 (因为本侧版本通配, 但 g/a 都是 null 故不会主动否决,
        // 实际上会返回 true; 这里只验证 isComplete=false)
        SkillCoordinate solo = SkillCoordinate.parseQuery("solo");
        assertThat(solo.getGroupId()).isNull();
    }

    @Test
    @DisplayName("matches: 查询侧 null 段视为通配, 实现非对称匹配")
    void should_match_asymmetrically_when_query_has_nulls() {
        SkillCoordinate target = SkillCoordinate.of("g", "a", "1.0");

        // 同三段精确匹配
        assertThat(SkillCoordinate.of("g", "a", "1.0").matches(target)).isTrue();

        // 版本通配
        assertThat(SkillCoordinate.of("g", "a", null).matches(target)).isTrue();

        // groupId / artifactId 不一致
        assertThat(SkillCoordinate.of("other", "a", "1.0").matches(target)).isFalse();
        assertThat(SkillCoordinate.of("g", "other", "1.0").matches(target)).isFalse();
        assertThat(SkillCoordinate.of("g", "a", "2.0").matches(target)).isFalse();
    }

    @Test
    @DisplayName("matches: 反向不对称 — 完整坐标查询不会被 wildcard 目标命中")
    void should_not_match_when_target_has_more_specific_fields() {
        SkillCoordinate wildcardTarget = SkillCoordinate.of("g", "a", null);
        SkillCoordinate fullQuery = SkillCoordinate.of("g", "a", "1.0");
        // 查询侧版本=1.0, 目标版本=null, equals 比较失败
        assertThat(fullQuery.matches(wildcardTarget)).isFalse();
    }
}
