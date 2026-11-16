package com.gt;

import com.gt.kb.Taxonomy;
import com.gt.kb.model.BlockSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 树形目录侧栏的交互行为：懒加载、刷新保留展开与选中、点击导航。
 * 用假宿主喂数据，不碰文件、数据库和真实笔记；也不 realize 任何窗口。
 */
@ExtendWith(KbTreePanelTest.EdtExtension.class)
public class KbTreePanelTest {

    /** refreshNow 会把自己调度到 EDT，测试不在 EDT 上跑就只会看到空树。 */
    static final class EdtExtension implements InvocationInterceptor {
        @Override
        public void interceptTestMethod(Invocation<Void> invocation,
                                        ReflectiveInvocationContext<Method> invocationContext,
                                        ExtensionContext extensionContext) throws Throwable {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    invocation.proceed();
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            if (failure.get() != null) {
                throw failure.get();
            }
        }
    }

    private static final String VOCAB = """
            version: 1
            domains:
              - name: 数据库
                topics: [MySQL, Redis]
              - name: 运维
                topics: [Linux, Docker]
            """;

    private static final class FakeHost implements KbTreePanel.Host {
        final List<NoteDto> notes = new ArrayList<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, List<BlockSummary>> blocks = new LinkedHashMap<>();
        final List<String> openedNotes = new ArrayList<>();
        final List<String> openedBlocks = new ArrayList<>();
        int metadataCalls;
        int summaryCalls;

        @Override
        public List<NoteDto> noteMetadata() {
            metadataCalls++;
            return notes;
        }

        @Override
        public Taxonomy taxonomy() {
            return Taxonomy.parse(VOCAB);
        }

        @Override
        public Map<String, Integer> blockCounts() {
            return counts;
        }

        @Override
        public List<BlockSummary> blockSummaries(String noteKey) {
            summaryCalls++;
            return blocks.getOrDefault(noteKey, List.of());
        }

        @Override
        public void openNote(String noteKey) {
            openedNotes.add(noteKey);
        }

        @Override
        public void openBlock(String noteKey, String blockId, int startLine) {
            openedBlocks.add(noteKey + "/" + blockId + "@" + startLine);
        }
    }

    private final FakeHost host = new FakeHost();

    private KbTreePanel panel() {
        host.notes.add(note("mysql1", "MySQL 索引", "数据库", "MySQL", false));
        host.notes.add(note("redis1", "Redis 持久化", "数据库", "Redis", false));
        host.counts.put("mysql1", 2);
        host.counts.put("redis1", 1);
        host.blocks.put("mysql1", List.of(
                new BlockSummary("ku_aaaaaaaa", "mysql1", "索引结构", 2, 3, 8),
                new BlockSummary("ku_bbbbbbbb", "mysql1", "", 2, 10, 15)));
        host.blocks.put("redis1", List.of(
                new BlockSummary("ku_cccccccc", "redis1", "RDB", 2, 1, 4)));
        return new KbTreePanel(host);
    }

    private static NoteDto note(String key, String title, String domain, String topic, boolean encrypted) {
        NoteDto n = new NoteDto();
        n.key = key;
        n.title = title;
        n.domain = domain;
        n.topic = topic;
        n.encrypted = encrypted;
        return n;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(KbTreePanel panel, String name) {
        try {
            Field f = KbTreePanel.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JTree tree(KbTreePanel panel) {
        return field(panel, "tree");
    }

    private static DefaultMutableTreeNode find(JTree tree, String noteKey) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        Enumeration<?> e = root.preorderEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.nextElement();
            if (n.getUserObject() instanceof KbTreePanel.Note note && note.key().equals(noteKey)) {
                return n;
            }
        }
        return null;
    }

    private static TreePath path(DefaultMutableTreeNode node) {
        return new TreePath(node.getPath());
    }

    private static DefaultMutableTreeNode child(DefaultMutableTreeNode node, int i) {
        return (DefaultMutableTreeNode) node.getChildAt(i);
    }

    private static DefaultMutableTreeNode parent(DefaultMutableTreeNode node) {
        return (DefaultMutableTreeNode) node.getParent();
    }

    @Test
    public void firstBuildExpandsToNotesButNotToBlocks() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JTree tree = tree(panel);

        DefaultMutableTreeNode mysql = find(tree, "mysql1");
        assertNotNull(mysql);
        assertTrue(tree.isExpanded(path(parent(mysql))), "主题应展开");
        assertTrue(tree.isExpanded(path(parent(parent(mysql)))), "分类应展开");
        assertFalse(tree.isExpanded(path(mysql)), "笔记本身应折叠");
        assertEquals(1, mysql.getChildCount(), "只挂占位子节点");
        assertEquals(0, host.summaryCalls, "没展开就不该查块目录");
    }

    @Test
    public void ensureBuiltOnlyBuildsOnce() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        panel.ensureBuilt();
        assertEquals(1, host.metadataCalls);
    }

    @Test
    public void expandingNoteLoadsBlocksLazilyAndOnlyOnce() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JTree tree = tree(panel);
        DefaultMutableTreeNode mysql = find(tree, "mysql1");

        tree.expandPath(path(mysql));

        assertEquals(1, host.summaryCalls);
        assertEquals(2, mysql.getChildCount());
        assertTrue(child(mysql, 0).getUserObject() instanceof KbTreePanel.Block);
        assertEquals("索引结构", ((KbTreePanel.Block) child(mysql, 0).getUserObject()).title());
        assertEquals("(无标题)", ((KbTreePanel.Block) child(mysql, 1).getUserObject()).title());

        tree.collapsePath(path(mysql));
        tree.expandPath(path(mysql));
        assertEquals(1, host.summaryCalls, "已加载过的笔记不该重复查库");
    }

    @Test
    public void encryptedAndEmptyNotesHaveNoExpandArrow() {
        host.notes.add(note("secret", "私密笔记", "数据库", "MySQL", true));
        host.notes.add(note("empty", "空笔记", "运维", "Linux", false));
        host.counts.put("secret", 5);
        KbTreePanel panel = new KbTreePanel(host);
        panel.ensureBuilt();
        JTree tree = tree(panel);

        assertEquals(0, find(tree, "secret").getChildCount(), "私密笔记不出块");
        assertEquals(0, find(tree, "empty").getChildCount(), "没有块的笔记不挂占位");
        assertTrue(((KbTreePanel.Note) find(tree, "secret").getUserObject()).encrypted());
    }

    @Test
    public void selectingNoteAndBlockNotifiesHost() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JTree tree = tree(panel);
        DefaultMutableTreeNode mysql = find(tree, "mysql1");

        tree.setSelectionPath(path(mysql));
        assertEquals(List.of("mysql1"), host.openedNotes);

        tree.expandPath(path(mysql));
        tree.setSelectionPath(path(child(mysql, 0)));
        assertEquals(List.of("mysql1/ku_aaaaaaaa@3"), host.openedBlocks);
    }

    @Test
    public void selectingCategoryDoesNotOpenAnything() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JTree tree = tree(panel);
        DefaultMutableTreeNode domain = parent(parent(find(tree, "mysql1")));

        tree.setSelectionPath(path(domain));

        assertTrue(host.openedNotes.isEmpty());
        assertTrue(host.openedBlocks.isEmpty());
    }

    @Test
    public void refreshKeepsExpandedBlocksAndSelectionWithoutReopening() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JTree tree = tree(panel);
        DefaultMutableTreeNode mysql = find(tree, "mysql1");
        tree.expandPath(path(mysql));
        tree.setSelectionPath(path(child(mysql, 1)));
        host.openedNotes.clear();
        host.openedBlocks.clear();

        panel.refreshNow();

        DefaultMutableTreeNode after = find(tree, "mysql1");
        assertTrue(tree.isExpanded(path(after)), "刷新后笔记仍展开");
        assertEquals(2, after.getChildCount(), "块节点被还原而不是退回占位");
        assertNotNull(tree.getSelectionPath());
        assertEquals("ku_bbbbbbbb",
                ((KbTreePanel.Block) ((DefaultMutableTreeNode) tree.getSelectionPath().getLastPathComponent())
                        .getUserObject()).blockId());
        assertTrue(host.openedNotes.isEmpty(), "还原选中不能反过来再打开一次笔记");
        assertTrue(host.openedBlocks.isEmpty());
    }

    @Test
    public void refreshMovesNoteAfterReclassification() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JTree tree = tree(panel);

        host.notes.get(0).domain = "运维";
        host.notes.get(0).topic = "Linux";
        panel.refreshNow();

        DefaultMutableTreeNode moved = find(tree, "mysql1");
        assertEquals("Linux", ((KbTreePanel.Topic) parent(moved).getUserObject()).name());
        assertEquals("运维", ((KbTreePanel.Domain) parent(parent(moved)).getUserObject()).name());
    }

    @Test
    public void refreshDropsDeletedNote() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JTree tree = tree(panel);

        host.notes.removeIf(n -> n.key.equals("redis1"));
        panel.refreshNow();

        assertNull(find(tree, "redis1"));
        assertNotNull(find(tree, "mysql1"));
    }

    @Test
    public void summaryReportsTotals() {
        KbTreePanel panel = panel();
        panel.ensureBuilt();
        JLabel summary = field(panel, "summary");
        assertEquals("1 分类 / 2 主题 / 2 篇 / 3 块", summary.getText());
    }

    @Test
    public void scheduleRefreshIsDeferred() {
        KbTreePanel panel = panel();
        panel.scheduleRefresh();
        assertEquals(0, host.metadataCalls, "防抖期内不该立刻查库");
    }
}