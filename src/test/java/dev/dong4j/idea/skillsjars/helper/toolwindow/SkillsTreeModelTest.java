package dev.dong4j.idea.skillsjars.helper.toolwindow;

import com.intellij.openapi.vfs.VirtualFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.util.Collections;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SkillsTreeModel} 单元测试.
 *
 * @author dong4j
 */
class SkillsTreeModelTest {

    @Test
    @DisplayName("空输入产出无子节点的根")
    void should_build_empty_root() {
        DefaultTreeModel model = SkillsTreeModel.build(Collections.emptyList());
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        assertThat(root.getChildCount()).isZero();
    }

    @Test
    @DisplayName("两层结构: 每个 artifact 一个一级节点, 每个 skill 一个叶子")
    void should_build_two_layer_tree() {
        SkillJarArtifact a1 = artifact("g", "a", "1", "n1", "n2");
        SkillJarArtifact a2 = artifact("g", "b", "2", "n3");

        DefaultTreeModel model = SkillsTreeModel.build(List.of(a1, a2));
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();

        assertThat(root.getChildCount()).isEqualTo(2);

        DefaultMutableTreeNode artifactNode1 = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(artifactNode1.getUserObject()).isInstanceOf(SkillsTreeModel.ArtifactNode.class);
        assertThat(artifactNode1.getChildCount()).isEqualTo(2);

        DefaultMutableTreeNode skillNode = (DefaultMutableTreeNode) artifactNode1.getChildAt(0);
        assertThat(skillNode.getUserObject()).isInstanceOf(SkillsTreeModel.SkillNode.class);
        assertThat(skillNode.isLeaf()).isTrue();
    }

    @Test
    @DisplayName("ArtifactNode#toString 用于 SpeedSearch, 含 artifactId:version")
    void artifact_node_toString_should_contain_coordinate() {
        SkillJarArtifact artifact = artifact("g", "spring-foo", "1.0", "x");
        SkillsTreeModel.ArtifactNode node = new SkillsTreeModel.ArtifactNode(artifact);
        assertThat(node.toString()).isEqualTo("spring-foo:1.0");
    }

    @Test
    @DisplayName("SkillNode#toString 用于 SpeedSearch, 等于 skill 名")
    void skill_node_toString_should_be_skill_name() {
        SkillJarArtifact artifact = artifact("g", "a", "1", "code-review");
        SkillDescriptor skill = artifact.getSkills().get(0);
        SkillsTreeModel.SkillNode node = new SkillsTreeModel.SkillNode(artifact, skill);
        assertThat(node.toString()).isEqualTo("code-review");
    }

    @Test
    @DisplayName("userObject 能从 TreePath 中取出领域对象")
    void should_extract_user_object_from_tree_path() {
        SkillJarArtifact artifact = artifact("g", "a", "1", "x");
        DefaultTreeModel model = SkillsTreeModel.build(List.of(artifact));
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        DefaultMutableTreeNode artifactNode = (DefaultMutableTreeNode) root.getChildAt(0);
        DefaultMutableTreeNode skillNode = (DefaultMutableTreeNode) artifactNode.getChildAt(0);

        assertThat(SkillsTreeModel.userObject(new TreePath(artifactNode.getPath())))
            .isInstanceOf(SkillsTreeModel.ArtifactNode.class);
        assertThat(SkillsTreeModel.userObject(new TreePath(skillNode.getPath())))
            .isInstanceOf(SkillsTreeModel.SkillNode.class);
        assertThat(SkillsTreeModel.userObject(null)).isNull();
    }

    /** 测试用辅助: 构造一个含若干 skill 的 jar artifact (复用 mock VirtualFile). */
    private static SkillJarArtifact artifact(String g, String a, String v, String... skillNames) {
        VirtualFile vf = mock(VirtualFile.class);
        when(vf.getPath()).thenReturn("/tmp/" + a + "-" + v + ".jar");
        SkillDescriptor[] skills = new SkillDescriptor[skillNames.length];
        for (int i = 0; i < skillNames.length; i++) {
            skills[i] = new SkillDescriptor(
                skillNames[i],
                "desc-" + skillNames[i],
                List.of("Read"),
                null,
                "META-INF/skills/" + skillNames[i] + "/",
                "META-INF/skills/" + skillNames[i] + "/SKILL.md",
                "body");
        }
        return new SkillJarArtifact(
            vf,
            SkillSourceType.MAVEN_DEPENDENCY,
            SkillCoordinate.of(g, a, v),
            List.of(skills),
            "Maven: " + g + ":" + a + ":" + v
        );
    }
}
