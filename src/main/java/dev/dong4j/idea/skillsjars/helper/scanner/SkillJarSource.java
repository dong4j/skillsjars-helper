package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

/**
 * 扫描器输出的候选 Jar.
 *
 * <p>扫描器只负责 "找到候选 Jar 并标注它的来源", 不负责打开 Jar 解析 SKILL.md.
 * 这种解耦让我们后续可以:</p>
 * <ul>
 *   <li>在 Maven plugin scanner 把候选 Jar 标注成 {@link SkillSourceType#MAVEN_PLUGIN_DEPENDENCY},
 *       而 Gradle scanner 标注成 {@link SkillSourceType#GRADLE_DEPENDENCY}, 解析阶段对它们一视同仁.</li>
 *   <li>把同一个 Jar 在多个扫描器中各产出一个 {@code SkillJarSource}, 由协调层做去重 (按 jar 路径).</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillJarSource {

    /** Jar 文件 (不是 jar 内目录). */
    @NotNull
    private final VirtualFile jarFile;

    /** 来源类型, 由扫描器标注. */
    @NotNull
    private final SkillSourceType sourceType;

    /** 解析得到的坐标; 没有坐标信息时使用 {@link SkillCoordinate#unknown()}. */
    @NotNull
    private final SkillCoordinate coordinate;

    /** 库 / 制品的展示名, 用于调试和 UI 兜底. */
    @Nullable
    private final String displayName;

    /**
     * 构造候选 Jar.
     *
     * @param jarFile     Jar 文件
     * @param sourceType  来源类型
     * @param coordinate  坐标
     * @param displayName 展示名
     */
    public SkillJarSource(@NotNull VirtualFile jarFile,
                          @NotNull SkillSourceType sourceType,
                          @NotNull SkillCoordinate coordinate,
                          @Nullable String displayName) {
        this.jarFile = jarFile;
        this.sourceType = sourceType;
        this.coordinate = coordinate;
        this.displayName = displayName;
    }

    @NotNull
    public VirtualFile getJarFile() {
        return this.jarFile;
    }

    @NotNull
    public SkillSourceType getSourceType() {
        return this.sourceType;
    }

    @NotNull
    public SkillCoordinate getCoordinate() {
        return this.coordinate;
    }

    @Nullable
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillJarSource that)) {
            return false;
        }
        return Objects.equals(this.jarFile.getPath(), that.jarFile.getPath())
            && this.sourceType == that.sourceType
            && Objects.equals(this.coordinate, that.coordinate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.jarFile.getPath(), this.sourceType, this.coordinate);
    }

    @Override
    public String toString() {
        return "SkillJarSource{" + this.sourceType + ":" + this.jarFile.getPath() + '}';
    }
}
