package dev.dong4j.idea.skillsjars.helper.api.model;

import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一个 SkillsJar 在 IDE 侧的解析结果.
 *
 * <p>表示一个 Jar 文件, 该文件至少包含一个 {@code META-INF/skills/**\/SKILL.md} 入口.
 * 同一 Jar 内可能有多个 Skill, 因此 {@link #getSkills()} 返回列表.</p>
 *
 * <p>该类型属于公共 API, 第三方插件需要直接访问其字段. 设计成不可变对象, 字段尽量克制:</p>
 * <ul>
 *   <li>不持有 UI 状态.</li>
 *   <li>不持有 manifest / 安装状态等导出阶段才需要的信息.</li>
 *   <li>{@link VirtualFile} 是 IDEA 的标准抽象, 调用方可以直接用它读 Jar 入口或定位源文件.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillJarArtifact {

    /** Jar 对应的虚拟文件, 一定是 jar 文件本身, 而不是 jar 内部某个根目录. */
    @NotNull
    private final VirtualFile jarFile;

    /** 来源类型. */
    @NotNull
    private final SkillSourceType sourceType;

    /** 解析得到的坐标; 解析失败时也会返回 {@link SkillCoordinate#unknown()}, 而不是 null. */
    @NotNull
    private final SkillCoordinate coordinate;

    /** 该 Jar 内识别到的 Skill 列表, 至少 1 个. */
    @NotNull
    private final List<SkillDescriptor> skills;

    /** IDEA 库名 (例如 {@code "Maven: g:a:v"}), 用于辅助调试和 UI 展示, 可为 null. */
    @Nullable
    private final String libraryName;

    /**
     * 构造 Jar 制品.
     *
     * @param jarFile     Jar 虚拟文件
     * @param sourceType  来源类型
     * @param coordinate  坐标
     * @param skills      Skill 列表 (会被复制为不可变列表)
     * @param libraryName IDEA 库名, 可为 null
     */
    public SkillJarArtifact(@NotNull VirtualFile jarFile,
                            @NotNull SkillSourceType sourceType,
                            @NotNull SkillCoordinate coordinate,
                            @NotNull List<SkillDescriptor> skills,
                            @Nullable String libraryName) {
        this.jarFile = jarFile;
        this.sourceType = sourceType;
        this.coordinate = coordinate;
        this.skills = List.copyOf(skills);
        this.libraryName = libraryName;
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

    /**
     * 获取该 Jar 内的所有 Skill, 已为不可变列表.
     *
     * @return Skill 列表
     */
    @NotNull
    public List<SkillDescriptor> getSkills() {
        return Collections.unmodifiableList(this.skills);
    }

    @Nullable
    public String getLibraryName() {
        return this.libraryName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillJarArtifact that)) {
            return false;
        }
        // 文件路径 + 坐标作为身份判定, 避免 List 比较拖慢热路径
        return Objects.equals(this.jarFile.getPath(), that.jarFile.getPath())
            && Objects.equals(this.coordinate, that.coordinate)
            && this.sourceType == that.sourceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.jarFile.getPath(), this.coordinate, this.sourceType);
    }

    @Override
    public String toString() {
        return "SkillJarArtifact{" +
            "jar=" + this.jarFile.getPath() +
            ", source=" + this.sourceType +
            ", coordinate=" + this.coordinate +
            ", skillCount=" + this.skills.size() +
            '}';
    }
}
