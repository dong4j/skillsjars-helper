package dev.dong4j.idea.skillsjars.helper.toolwindow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;

/**
 * Tool Window 树形视图的模型构建器.
 *
 * <p>结构: 隐藏 root → 一组 {@link ArtifactNode} → 每个 ArtifactNode 下挂 {@link SkillNode}.
 * 这样 UI 上每个 jar 只展示一次坐标, 同一 jar 内的多个 skill 全部折叠在它下面,
 * 解决了原 flat 表格里 artifact / source 列大量重复的问题.</p>
 *
 * <p>设计要点:</p>
 * <ul>
 *   <li>ArtifactNode / SkillNode 都是 record, 不可变, 线程安全.</li>
 *   <li>{@code toString()} 返回的字符串会被 IDEA {@code TreeSpeedSearch} 当作匹配源,
 *       因此它必须包含用户最关心的字段 (artifact 显示坐标, skill 显示名称).</li>
 *   <li>本类不持有 IDE 状态, 测试时无需启动 IDE 容器.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillsTreeModel {

    private SkillsTreeModel() {
    }

    /**
     * 把当前快照构建成树模型.
     *
     * @param artifacts 当前 SkillRegistry 暴露的 jar 列表
     * @return 已就绪的 {@link DefaultTreeModel}, 根节点已隐藏
     */
    @NotNull
    public static DefaultTreeModel build(@NotNull List<SkillJarArtifact> artifacts) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root", true);
        for (SkillJarArtifact artifact : artifacts) {
            DefaultMutableTreeNode artifactNode = new DefaultMutableTreeNode(new ArtifactNode(artifact), true);
            for (SkillDescriptor skill : artifact.getSkills()) {
                DefaultMutableTreeNode skillNode = new DefaultMutableTreeNode(new SkillNode(artifact, skill), false);
                artifactNode.add(skillNode);
            }
            root.add(artifactNode);
        }
        return new DefaultTreeModel(root);
    }

    /**
     * 从 TreePath 中安全取出领域对象.
     *
     * <p>调用方拿到 {@link ArtifactNode} 或 {@link SkillNode} 后做 {@code instanceof} 判断.
     * 路径为 null / 根节点 / 非我们的节点时返回 null.</p>
     *
     * @param path 任意树路径, 可为 null
     * @return 用户对象, 没有时返回 null
     */
    @Nullable
    public static Object userObject(@Nullable TreePath path) {
        if (path == null) {
            return null;
        }
        Object last = path.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode node) {
            return node.getUserObject();
        }
        return null;
    }

    /**
     * Artifact 树节点的用户对象.
     *
     * <p>{@code toString()} 返回 {@code artifactId:version}, 是 {@code TreeSpeedSearch} 的匹配源.</p>
     */
    public record ArtifactNode(@NotNull SkillJarArtifact artifact) {
        @Override
        public @NotNull String toString() {
            SkillCoordinate c = this.artifact.getCoordinate();
            String aid = c.getArtifactId();
            String version = c.getVersion();
            if (aid != null) {
                return version != null ? aid + ":" + version : aid;
            }
            return c.toCoordinateString();
        }
    }

    /**
     * Skill 树节点的用户对象.
     *
     * <p>{@code toString()} 返回 skill 名, 是 {@code TreeSpeedSearch} 的匹配源.</p>
     */
    public record SkillNode(@NotNull SkillJarArtifact artifact, @NotNull SkillDescriptor skill) {
        @Override
        public @NotNull String toString() {
            return this.skill.getName();
        }
    }
}
