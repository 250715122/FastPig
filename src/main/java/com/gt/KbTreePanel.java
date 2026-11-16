package com.gt;

import com.gt.NoteDto;
import com.gt.kb.KbDirectory;
import com.gt.kb.Taxonomy;
import com.gt.kb.model.BlockSummary;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库树形目录侧栏：分类 → 主题 → 笔记 → 知识块。
 *
 * 派生视图，不落盘、不建笔记、不进索引、不参与云同步。数据来自 {@link KbDirectory}，
 * 保存/删除后由宿主调 {@link #scheduleRefresh()} 自更新，刷新时保留展开状态与选中项。
 * 块节点懒加载：展开笔记时才查块目录，避免一次把 2400 多个块全塞进树。
 */
public class KbTreePanel extends JPanel {

    /** 宿主提供数据与导航动作，便于单测替换，也让本类不依赖 NoteService/UnifiedNoteAppFrame。 */
    public interface Host {
        List<NoteDto> noteMetadata();

        Taxonomy taxonomy();

        Map<String, Integer> blockCounts();

        List<BlockSummary> blockSummaries(String noteKey);

        void openNote(String noteKey);

        void openBlock(String noteKey, String blockId, int startLine);
    }

    sealed interface Entry permits Domain, Topic, Note, Block {
    }

    record Domain(String name, int noteCount) implements Entry {
    }

    record Topic(String domain, String name, int noteCount) implements Entry {
    }

    record Note(String key, String title, boolean encrypted, int blockCount) implements Entry {
    }

    record Block(String noteKey, String blockId, String title, int startLine) implements Entry {
    }

    /** 占位子节点：只为让笔记节点显示展开箭头，真正展开时才换成块列表。 */
    private static final String PLACEHOLDER = "__loading__";

    private static final int REFRESH_DELAY_MS = 300;

    private final Host host;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("知识库");
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model) {
        @Override
        public String getToolTipText(java.awt.event.MouseEvent e) {
            TreePath p = getPathForLocation(e.getX(), e.getY());
            if (p == null) {
                return null;
            }
            Object u = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            // 侧栏只有 260 宽，长标题会被裁掉，悬停给全文
            return switch (u) {
                case Note n -> n.title();
                case Block b -> b.title();
                case Topic t -> t.domain() + " / " + t.name();
                case Domain d -> d.name();
                default -> null;
            };
        }
    };
    private final JLabel summary = new JLabel(" ", SwingConstants.LEFT);
    private final Timer refreshTimer;

    /** 身份路径 → 节点，刷新后据此还原展开与选中。 */
    private final Map<List<String>, DefaultMutableTreeNode> byPath = new LinkedHashMap<>();

    private boolean loadingBlocks;
    private boolean firstBuild = true;
    /** 刷新时还原选中项会触发选择事件，不能让它反过来又打开一次笔记。 */
    private boolean restoring;

    public KbTreePanel(Host host) {
        this.host = host;
        setLayout(new BorderLayout());
        setBackground(UIColors.BG_PRIMARY);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRowHeight(0);
        tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        tree.setBackground(UIColors.BG_PRIMARY);
        tree.setCellRenderer(new Renderer());
        tree.addTreeWillExpandListener(new LazyBlocks());
        tree.addTreeSelectionListener(e -> {
            if (restoring || !e.isAddedPath() || tree.getSelectionCount() == 0) {
                return;
            }
            navigate(lastSelected());
        });

        JScrollPane scroll = new JScrollPane(tree);
        // 窄侧栏不出横向滚动条：长标题裁掉，靠悬停提示看全文
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIColors.BORDER_BASE));
        add(scroll, BorderLayout.CENTER);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);

        summary.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        summary.setForeground(UIColors.TEXT_SECONDARY);
        summary.setBackground(UIColors.BG_SECONDARY);
        summary.setOpaque(true);
        summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(summary, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(240, 0));
        setMinimumSize(new Dimension(180, 0));

        refreshTimer = new Timer(REFRESH_DELAY_MS, e -> refreshNow());
        refreshTimer.setRepeats(false);
    }

    /** 保存/删除后调用。与本文目录同样的 300ms 防抖，连续敲键不会反复查库。 */
    public void scheduleRefresh() {
        refreshTimer.restart();
    }

    public void refreshNow() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshNow);
            return;
        }
        List<List<String>> expanded = expandedPaths();
        List<String> selected = selectedPath();

        restoring = true;
        try {
            rebuild(expanded, selected);
        } finally {
            restoring = false;
        }
    }

    private void rebuild(List<List<String>> expanded, List<String> selected) {
        KbDirectory.Snapshot snapshot = KbDirectory.build(
                host.noteMetadata(), host.taxonomy(), host.blockCounts());

        root.removeAllChildren();
        byPath.clear();
        for (KbDirectory.DomainEntry d : snapshot.domains()) {
            DefaultMutableTreeNode domainNode = node(new Domain(d.name(), d.noteCount()), List.of(d.name()));
            for (KbDirectory.TopicEntry t : d.topics()) {
                DefaultMutableTreeNode topicNode = node(new Topic(d.name(), t.topic(), t.notes().size()),
                        List.of(d.name(), t.topic()));
                for (KbDirectory.NoteEntry n : t.notes()) {
                    DefaultMutableTreeNode noteNode = node(
                            new Note(n.key(), n.title(), n.encrypted(), n.blockCount()),
                            List.of(d.name(), t.topic(), n.key()));
                    // 有块才挂占位子节点：私密笔记和空笔记不该出现一个展开后什么都没有的箭头
                    if (n.blockCount() > 0 && !n.encrypted()) {
                        noteNode.add(new DefaultMutableTreeNode(PLACEHOLDER));
                    }
                    topicNode.add(noteNode);
                }
                domainNode.add(topicNode);
            }
            root.add(domainNode);
        }
        model.reload(root);

        if (firstBuild) {
            expandDomainsAndTopics();
            firstBuild = false;
        } else {
            restore(expanded, selected);
        }
        summary.setText(snapshot.domains().size() + " 分类 / " + snapshot.topicCount() + " 主题 / "
                + snapshot.noteCount() + " 篇 / " + snapshot.blockCount() + " 块");
    }

    /** 切到本面板时确保有内容：侧栏可能从未被展开过，模型还是空的。 */
    public void ensureBuilt() {
        if (root.getChildCount() == 0) {
            refreshNow();
        }
    }

    public void applyTheme() {
        setBackground(UIColors.BG_PRIMARY);
        tree.setBackground(UIColors.BG_PRIMARY);
        summary.setForeground(UIColors.TEXT_SECONDARY);
        summary.setBackground(UIColors.BG_SECONDARY);
        tree.repaint();
    }

    private DefaultMutableTreeNode node(Entry entry, List<String> path) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(entry);
        byPath.put(List.copyOf(path), n);
        return n;
    }

    /** 首次构建展开到「分类 → 主题 → 笔记」，笔记本身折叠，块按需展开。 */
    private void expandDomainsAndTopics() {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode domain = (DefaultMutableTreeNode) root.getChildAt(i);
            tree.expandPath(new TreePath(domain.getPath()));
            for (int j = 0; j < domain.getChildCount(); j++) {
                tree.expandPath(new TreePath(((DefaultMutableTreeNode) domain.getChildAt(j)).getPath()));
            }
        }
    }

    private void restore(List<List<String>> expanded, List<String> selected) {
        for (List<String> path : expanded) {
            DefaultMutableTreeNode n = byPath.get(path);
            if (n != null) {
                tree.expandPath(new TreePath(n.getPath()));
            }
        }
        if (selected != null) {
            DefaultMutableTreeNode n = byPath.get(selected);
            if (n != null) {
                TreePath tp = new TreePath(n.getPath());
                tree.setSelectionPath(tp);
                tree.scrollPathToVisible(tp);
            }
        }
    }

    private List<List<String>> expandedPaths() {
        List<List<String>> out = new ArrayList<>();
        Enumeration<TreePath> e = tree.getExpandedDescendants(new TreePath(root.getPath()));
        if (e == null) {
            return out;
        }
        while (e.hasMoreElements()) {
            List<String> path = identityPath(e.nextElement());
            // 块节点的身份路径含 blockId，重建后仍可对上；占位节点没有身份，跳过
            if (path != null) {
                out.add(path);
            }
        }
        return out;
    }

    private List<String> selectedPath() {
        TreePath tp = tree.getSelectionPath();
        return tp == null ? null : identityPath(tp);
    }

    private static List<String> identityPath(TreePath tp) {
        List<String> out = new ArrayList<>();
        for (Object o : tp.getPath()) {
            if (!(o instanceof DefaultMutableTreeNode n)) {
                return null;
            }
            // 根节点不可见也不承载 Entry，身份路径从分类起算
            if (n.isRoot()) {
                continue;
            }
            if (!(n.getUserObject() instanceof Entry entry)) {
                return null;
            }
            switch (entry) {
                case Domain d -> out.add(d.name());
                case Topic t -> out.add(t.name());
                case Note note -> out.add(note.key());
                case Block b -> out.add(b.blockId());
            }
        }
        return out;
    }

    private Entry lastSelected() {
        TreePath tp = tree.getSelectionPath();
        if (tp == null) {
            return null;
        }
        Object o = ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject();
        return o instanceof Entry entry ? entry : null;
    }

    private void navigate(Entry entry) {
        if (entry == null) {
            return;
        }
        switch (entry) {
            case Note n -> host.openNote(n.key());
            case Block b -> host.openBlock(b.noteKey(), b.blockId(), b.startLine());
            default -> {
            }
        }
    }

    /** 展开笔记时才查块目录。 */
    private final class LazyBlocks implements TreeWillExpandListener {
        @Override
        public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
            if (loadingBlocks) {
                return;
            }
            if (!(event.getPath().getLastPathComponent() instanceof DefaultMutableTreeNode node)
                    || !(node.getUserObject() instanceof Note note)) {
                return;
            }
            if (node.getChildCount() != 1
                    || !PLACEHOLDER.equals(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject())) {
                return;
            }
            loadingBlocks = true;
            try {
                node.removeAllChildren();
                List<String> basePath = identityPath(new TreePath(node.getPath()));
                for (BlockSummary b : host.blockSummaries(note.key())) {
                    String title = b.title() == null || b.title().isBlank() ? "(无标题)" : b.title();
                    List<String> path = new ArrayList<>(basePath);
                    path.add(b.blockId());
                    node.add(node(new Block(b.noteKey(), b.blockId(), title, b.startLine()), path));
                }
                model.reload(node);
            } finally {
                loadingBlocks = false;
            }
        }

        @Override
        public void treeWillCollapse(TreeExpansionEvent event) {
        }
    }

    private static final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setClosedIcon(null);
            setOpenIcon(null);
            setLeafIcon(null);
            setBackgroundNonSelectionColor(UIColors.BG_PRIMARY);
            setTextNonSelectionColor(UIColors.TEXT_PRIMARY);
            setBackgroundSelectionColor(UIColors.BG_ACTIVE);
            setTextSelectionColor(UIColors.TEXT_PRIMARY);
            setBorderSelectionColor(null);

            Object user = value instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
            if (user instanceof Domain d) {
                setText(d.name() + "  (" + d.noteCount() + ")");
                setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
                setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
            } else if (user instanceof Topic t) {
                setText(t.name() + "  (" + t.noteCount() + ")");
                setFont(new Font("Microsoft YaHei UI", Font.BOLD, 12));
                setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
            } else if (user instanceof Note n) {
                setText(n.title() + (n.encrypted() ? "  [私密]" : "") + "  · " + n.blockCount());
                setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
                setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            } else if (user instanceof Block b) {
                setText(b.title());
                setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
                if (!selected) {
                    setForeground(UIColors.TEXT_SECONDARY);
                }
                setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
            } else {
                setText(String.valueOf(user));
                setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
            }
            if (selected) {
                setOpaque(true);
                setBackground(UIColors.BG_ACTIVE);
            } else {
                setOpaque(false);
            }
            return this;
        }
    }
}