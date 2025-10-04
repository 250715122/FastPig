package com.gt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.swing.text.Highlighter;
import javax.swing.text.DefaultHighlighter;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoManager;
import java.io.File;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.util.misc.Extension;
import java.util.Arrays;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class UnifiedNoteAppFrame extends JFrame {
    private final NoteRepository repository;
    private TrayIcon trayIcon; // 系统托盘图标

    // 首行承载“快捷命令 空格 描述”，不再使用独立的输入框
    private final JPopupMenu suggestPopup = new JPopupMenu();
    private final DefaultListModel<String> suggestModel = new DefaultListModel<>();
    private final JList<String> suggestList = new JList<>(suggestModel);
    private int suggestSelectedIndex = -1;
    // 去除左侧结果列表，以输入联想替代

    // 去除标签与独立标题编辑，仅保留正文编辑区
    private final JTextArea bodyArea = new JTextArea(){
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            try{
                if (getDocument().getLength()==0){
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(0,0,0,110));
                    g2.setFont(getFont().deriveFont(Font.ITALIC));
                    String[] hints = {
                        "在此输入：快捷命令 空格 描述；",
                        "这里写正文（Ctrl+S保存，Alt+P预览，Alt+D删除）"
                    };
                    FontMetrics fm = g2.getFontMetrics();
                    int x = getInsets().left + 6;
                    int y = getInsets().top + fm.getAscent() + 2;
                    int lineHeight = fm.getHeight();
                    for (int i = 0; i < hints.length; i++) {
                        g2.drawString(hints[i], x, y + i * lineHeight);
                    }
                    g2.dispose();
                }
            }catch(Exception ignored){}
        }
    };
    private final Highlighter.HighlightPainter firstLinePainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255,255,0,40));
    private Object firstLineHighlightTag;

    // 页内搜索组件
    private JPanel searchPanel;
    private JTextField searchField;
    private JLabel searchResultLabel;
    private final Highlighter.HighlightPainter searchHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255,200,0,150));
    private final List<Object> searchHighlightTags = new ArrayList<>();
    private int currentSearchIndex = -1;
    private final List<Integer> searchMatchPositions = new ArrayList<>();

    private NoteDto current;

    public UnifiedNoteAppFrame(NoteRepository repository) {
        super("迅猪");
        this.repository = repository;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);
        
        // 设置窗口图标
        setWindowIcon();
        
        // 初始化系统托盘
        initSystemTray();
        
        // 添加窗口关闭监听器：点击 X 时最小化到托盘，而不是退出
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 隐藏窗口到托盘
                hideToTray();
            }
        });

        // 顶部按钮已移除（搜索、预览不再显示，预览保留 Alt+P 快捷键）

        // 建议弹层
        JScrollPane sp = new JScrollPane(suggestList);
        sp.setPreferredSize(new Dimension(420, 160));
        suggestPopup.add(sp);
        suggestList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 不让弹层或列表抢焦点，方向键由输入框驱动
        suggestList.setFocusable(false);
        sp.setFocusable(false);
        suggestPopup.setFocusable(false);
        suggestList.addMouseListener(new java.awt.event.MouseAdapter(){
            public void mouseClicked(java.awt.event.MouseEvent e){
                if (e.getClickCount()==2){
                    applySuggestion();
                }
            }
        });
        // 将方向键与回车交互绑定到正文首行（使用 bodyArea 捕获按键）
        bodyArea.addKeyListener(new java.awt.event.KeyAdapter(){
            @Override public void keyTyped(java.awt.event.KeyEvent e) {
                // 监听 `/` 键，弹出快捷命令菜单
                if (e.getKeyChar() == '/') {
                    int pos = bodyArea.getCaretPosition();
                    String text = bodyArea.getText();
                    // 检查是否在行首或空格后（避免在正常文本中间弹出）
                    if (pos == 0 || (pos > 0 && (text.charAt(pos - 1) == '\n' || text.charAt(pos - 1) == ' '))) {
                        SwingUtilities.invokeLater(() -> showSlashCommandMenu());
                        e.consume(); // 阻止 `/` 输入
                    }
                }
            }
            
            @Override public void keyReleased(java.awt.event.KeyEvent e){
                int code = e.getKeyCode();
                if (code==java.awt.event.KeyEvent.VK_DOWN){
                    if (suggestModel.size()>0){
                        suggestSelectedIndex = (suggestSelectedIndex + 1 + suggestModel.size()) % suggestModel.size();
                        suggestList.setSelectedIndex(suggestSelectedIndex);
                        suggestList.ensureIndexIsVisible(suggestSelectedIndex);
                    }
                    return;
                }
                if (code==java.awt.event.KeyEvent.VK_UP){
                    if (suggestModel.size()>0){
                        suggestSelectedIndex = (suggestSelectedIndex - 1 + suggestModel.size()) % suggestModel.size();
                        suggestList.setSelectedIndex(suggestSelectedIndex);
                        suggestList.ensureIndexIsVisible(suggestSelectedIndex);
                    }
                    return;
                }
                if (code==java.awt.event.KeyEvent.VK_ENTER){
                    if (suggestPopup.isVisible() && suggestModel.size()>0){
                        applySuggestion();
                        return;
                    }
                }
                if (code==java.awt.event.KeyEvent.VK_ESCAPE){ suggestPopup.setVisible(false); return; }
                updateSuggestions();
            }
        });

        // 左侧：结果列表
        // （已移除结果列表UI）

        // 顶部栏移除

        bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        // 撤销/重做支持（Ctrl+Z / Ctrl+Y）
        final UndoManager undoManager = new UndoManager();
        bodyArea.getDocument().addUndoableEditListener(new UndoableEditListener() {
            @Override public void undoableEditHappened(UndoableEditEvent e) {
                undoManager.addEdit(e.getEdit());
            }
        });
        // 绑定快捷键
        KeyStroke ksUndo = KeyStroke.getKeyStroke("control Z");
        KeyStroke ksRedo = KeyStroke.getKeyStroke("control Y");
        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(ksUndo, "editorUndo");
        bodyArea.getActionMap().put("editorUndo", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                try { if (undoManager.canUndo()) undoManager.undo(); } catch (Exception ignored) {}
            }
        });
        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(ksRedo, "editorRedo");
        bodyArea.getActionMap().put("editorRedo", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                try { if (undoManager.canRedo()) undoManager.redo(); } catch (Exception ignored) {}
            }
        });
        bodyArea.setLineWrap(true);
        bodyScrollPane = new JScrollPane(bodyArea);
        // 首行高亮：随内容变化动态更新
        bodyArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){ updateFirstLineHighlight(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e){ updateFirstLineHighlight(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e){ updateFirstLineHighlight(); }
        });

        // 底部状态栏
        statusLeft = new JLabel("就绪");
        statusRight = new JLabel("");
        statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        statusBar.add(statusLeft, BorderLayout.WEST);
        statusBar.add(statusRight, BorderLayout.EAST);

        // 初始化页内搜索面板
        initSearchPanel();

        editorPanel = new JPanel(new BorderLayout(8, 8));
        editorPanel.add(bodyScrollPane, BorderLayout.CENTER);
        editorPanel.add(statusBar, BorderLayout.SOUTH);

        setLayout(new BorderLayout(8, 8));
        add(editorPanel, BorderLayout.CENTER);
        centerComponent = editorPanel;
        ACTIVE = this;

        // 绑定全局快捷键：Ctrl+S -> 保存
        JRootPane root = getRootPane();
        KeyStroke saveKs = KeyStroke.getKeyStroke("control S");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(saveKs, "saveUnified");
        root.getActionMap().put("saveUnified", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveUnified(true); }
        });
        // 自动保存：3秒去抖
        javax.swing.Timer autosaveTimer = new javax.swing.Timer(3000, e -> {
            if (!isFirstLineStructured()) {
                // 首行未形成“key 空格 desc”，不执行自动保存
                return;
            }
            saveUnified(false);
        });
        autosaveTimer.setRepeats(false);
        bodyArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){ autosaveTimer.restart(); updateEditorStatus(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e){ autosaveTimer.restart(); updateEditorStatus(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e){ autosaveTimer.restart(); updateEditorStatus(); }
        });
        // 程序内快捷键：Alt+P / 右Alt(AltGr)+P / Ctrl+Alt+P -> 预览/收起预览
        KeyStroke ksAltP = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.ALT_DOWN_MASK);
        KeyStroke ksAltGrP = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke ksCtrlAltP = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltP, "togglePreview");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltGrP, "togglePreview");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlAltP, "togglePreview");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltP, "togglePreview");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltGrP, "togglePreview");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksCtrlAltP, "togglePreview");
        root.getActionMap().put("togglePreview", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleInAppPreview(); }
        });

        // 程序内快捷键：Alt+D / 右Alt(AltGr)+D / Ctrl+Alt+D -> 删除(软)
        KeyStroke ksAltD = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.ALT_DOWN_MASK);
        KeyStroke ksAltGrD = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke ksCtrlAltD = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltD, "softDelete");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltGrD, "softDelete");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlAltD, "softDelete");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltD, "softDelete");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltGrD, "softDelete");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksCtrlAltD, "softDelete");
        root.getActionMap().put("softDelete", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { deleteCurrent(); }
        });
        // 撤销删除 Alt+Z（左右 Alt/AltGr/Ctrl+Alt 兜底）
        KeyStroke ksAltZ = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.ALT_DOWN_MASK);
        KeyStroke ksAltGrZ = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke ksCtrlAltZ = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltZ, "undoSoftDelete");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltGrZ, "undoSoftDelete");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlAltZ, "undoSoftDelete");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltZ, "undoSoftDelete");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltGrZ, "undoSoftDelete");
        
        // 页内搜索 Ctrl+F
        KeyStroke ksCtrlF = KeyStroke.getKeyStroke("control F");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlF, "toggleSearch");
        root.getActionMap().put("toggleSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleSearchPanel(); }
        });
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksCtrlAltZ, "undoSoftDelete");
        root.getActionMap().put("undoSoftDelete", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { undoSoftDelete(); }
        });
        // Ctrl+` 插入代码块
        KeyStroke ksCtrlBacktick = KeyStroke.getKeyStroke('`', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlBacktick, "insertCodeBlock");
        root.getActionMap().put("insertCodeBlock", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                String tpl = "```\n\n```\n";
                bodyArea.replaceSelection(tpl);
            }
        });
        // Ctrl+Shift+M 插入行内 LaTeX 模板 \(…\)
        KeyStroke ksInlineMath = KeyStroke.getKeyStroke('M', java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksInlineMath, "insertInlineMath");
        root.getActionMap().put("insertInlineMath", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                bodyArea.replaceSelection("\\(  \\)");
            }
        });
        // Ctrl+Shift+L 插入块级 LaTeX 模板 $$…$$
        KeyStroke ksBlockMath = KeyStroke.getKeyStroke('L', java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksBlockMath, "insertBlockMath");
        root.getActionMap().put("insertBlockMath", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){
                bodyArea.replaceSelection("$$\n\n$$\n");
            }
        });
        // Alt+S 同步数据库到云端已改为全局热键，在 HotKeyManager 中处理
        // Esc 关闭补全
        KeyStroke ksEsc = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksEsc, "hideSuggest");
        root.getActionMap().put("hideSuggest", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ suggestPopup.setVisible(false); }
        });

        // 添加 KeyEventDispatcher 作为兜底方案，捕获所有 Alt+P 组合
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
                // 只处理当前窗口的按键事件
                if (!SwingUtilities.isDescendingFrom(e.getComponent(), UnifiedNoteAppFrame.this)) {
                    return false;
                }
                
                // 检查是否为 Alt+P 组合（按键按下事件）
                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED && 
                    e.getKeyCode() == java.awt.event.KeyEvent.VK_P &&
                    (e.isAltDown() || e.isAltGraphDown())) {
                    
                    System.out.println("KeyEventDispatcher 捕获到 Alt+P，触发预览切换");
                    SwingUtilities.invokeLater(() -> toggleInAppPreview());
                    return true; // 消费此事件
                }
                // 检查是否为 Alt+D 组合（按键按下事件）
                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED &&
                    e.getKeyCode() == java.awt.event.KeyEvent.VK_D &&
                    (e.isAltDown() || e.isAltGraphDown())) {
                    System.out.println("KeyEventDispatcher 捕获到 Alt+D，触发删除(软)");
                    SwingUtilities.invokeLater(() -> deleteCurrent());
                    return true;
                }
                // Alt+Z 撤销删除
                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED &&
                    e.getKeyCode() == java.awt.event.KeyEvent.VK_Z &&
                    (e.isAltDown() || e.isAltGraphDown())) {
                    System.out.println("KeyEventDispatcher 捕获到 Alt+Z，撤销删除");
                    SwingUtilities.invokeLater(() -> undoSoftDelete());
                    return true;
                }
                return false;
            }
        });
        // 选区悬浮工具条（Ctrl+E 手动触发；有选区时自动出现）
        initSelectionToolbar();
        bodyArea.addCaretListener(e -> {
            if (!selectionToolbarInitialized) return;
            if (bodyArea.getSelectionStart() != bodyArea.getSelectionEnd()) {
                showSelectionToolbarAtSelection();
            } else {
                if (selectionToolbar != null) selectionToolbar.setVisible(false);
            }
        });
        KeyStroke ksToggleSel = KeyStroke.getKeyStroke('E', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksToggleSel, "toggleSelectionToolbar");
        root.getActionMap().put("toggleSelectionToolbar", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ toggleSelectionToolbar(); }
        });
    }

    private boolean previewVisible = false;
    private JSplitPane previewSplit;
    private JEditorPane htmlPane;
    private javax.swing.Timer previewTimer;
    private JScrollPane bodyScrollPane;
    private JPanel statusBar;
    private JLabel statusLeft;
    private JLabel statusRight;
    private long lastSavedAt = 0L;
    private boolean darkTheme = false;
    private Component centerComponent;
    private JPanel editorPanel; // 持有编辑器的面板，用于动态添加搜索栏
    // 预览按钮已移除，保留占位避免大范围改动
    // private JButton previewBtnRef;
    // 保持最近激活实例，便于全局热键调用
    private static volatile UnifiedNoteAppFrame ACTIVE;
    public static UnifiedNoteAppFrame getActiveInstance() { return ACTIVE; }
    // 选区悬浮工具条
    private JPopupMenu selectionToolbar;
    private boolean selectionToolbarInitialized = false;
    
    /**
     * 让主编辑区获得焦点
     */
    public void focusEditor() {
        try {
            SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
        } catch (Exception ignored) {}
    }

    private void toggleInAppPreview() {
        if (!previewVisible) {
            // 创建右侧 HTML 预览（离线，无JS），公式以内联图片呈现
            htmlPane = new JEditorPane();
            htmlPane.setEditable(false);
            htmlPane.setContentType("text/html;charset=UTF-8");

            previewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(bodyArea), new JScrollPane(htmlPane));
            previewSplit.setResizeWeight(0.5);
            getContentPane().remove(centerComponent);
            centerComponent = previewSplit;
            getContentPane().add(centerComponent, BorderLayout.CENTER);
            revalidate();
            repaint();
            SwingUtilities.invokeLater(() -> previewSplit.setDividerLocation(0.5));

            refreshInAppPreview();
            previewVisible = true;
            // 安装实时预览（去抖200ms）
            if (previewTimer == null) {
                previewTimer = new javax.swing.Timer(200, e -> refreshInAppPreview());
                previewTimer.setRepeats(false);
            }
            bodyArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { previewTimer.restart(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { previewTimer.restart(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { previewTimer.restart(); }
            });
        } else {
            // 关闭预览，恢复原布局
            getContentPane().remove(centerComponent);
            bodyScrollPane = new JScrollPane(bodyArea);
            JPanel editor2 = new JPanel(new BorderLayout(8, 8));
            editor2.add(bodyScrollPane, BorderLayout.CENTER);
            editor2.add(statusBar, BorderLayout.SOUTH);
            centerComponent = editor2;
            getContentPane().add(centerComponent, BorderLayout.CENTER);
            revalidate();
            repaint();
            previewVisible = false;
        }
    }

    private void refreshInAppPreview() {
        String md = bodyArea.getText();
        // 预处理：为 Markdown 表格自动补充必要的空行，避免被当作普通段落渲染
        md = normalizeMarkdownTables(md);
        // 大文档降频：超过 50KB 时预览去抖提升到 600ms
        if (md != null && md.length() > 50 * 1024 && previewTimer != null) {
            int delay = 600;
            if (previewTimer.getDelay() != delay) previewTimer.setDelay(delay);
        }
        // 将所有 LaTeX 片段替换为内联图片占位
        String mdWithImgs = replaceAllLatexWithImages(md);
        // 将所有 Mermaid 代码块替换为图片
        mdWithImgs = replaceAllMermaidWithImages(mdWithImgs);
        String html = renderMarkdown(mdWithImgs);
        try {
            String snippet = mdWithImgs.length() > 200 ? mdWithImgs.substring(0, 200) + "..." : mdWithImgs;
            System.out.println("[预览] Markdown 片段: \n" + snippet);
            System.out.println("[预览] 是否包含<table>: " + (html.contains("<table") ? "是" : "否"));
        } catch (Exception ignore) {}
        
        // 构建完整的 HTML
        String fullHtml = "<html><head><meta charset='utf-8'><style>" +
                         "body { font-family: 'Segoe UI', sans-serif; line-height: 1.6; padding: 10px; }" +
                         "p { white-space: pre-wrap; }" +
                         "pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto; white-space: pre-wrap; }" +
                         "code { background-color: #f0f0f0; padding: 2px 4px; border-radius: 3px; }" +
                         "img { max-width: 100%; height: auto; display: block; margin: 10px auto; }" +
                         "table { border-collapse: collapse; width: 100%; margin: 10px 0; white-space: normal; }" +
                         "th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }" +
                         "th { background-color: #f5f5f5; font-weight: bold; }" +
                         "tr:nth-child(even) { background-color: #fafafa; }" +
                         "</style></head><body>" + html + "</body></html>";
        htmlPane.setText(fullHtml);
        htmlPane.setCaretPosition(0);
        
        // 预览指示浮标
        try {
            JLayeredPane layered = getLayeredPane();
            JLabel badge = new JLabel("预览中（Alt+P退出）");
            badge.setOpaque(true);
            badge.setBackground(new Color(0,0,0,150));
            badge.setForeground(Color.WHITE);
            badge.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
            Dimension sz = badge.getPreferredSize();
            int x = getWidth() - sz.width - 24;
            int y = 12;
            badge.setBounds(x, y, sz.width, sz.height);
            layered.add(badge, JLayeredPane.POPUP_LAYER);
            javax.swing.Timer t = new javax.swing.Timer(1200, e -> {
                layered.remove(badge);
                layered.revalidate();
                layered.repaint();
            });
            t.setRepeats(false);
            t.start();
        } catch (Exception ignored) {}
    }
    
    /**
     * 将所有 Mermaid 代码块替换为图片
     */
    private String replaceAllMermaidWithImages(String text) {
        if (text == null) return "";
        
        // 匹配 ```mermaid ... ``` 代码块
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "```mermaid\\s*\\n([\\s\\S]*?)\\n```",
            java.util.regex.Pattern.MULTILINE
        );
        
        StringBuffer result = new StringBuffer();
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            String mermaidCode = matcher.group(1).trim();
            String imageTag = convertMermaidToImageTag(mermaidCode);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(imageTag));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 将 Mermaid 代码转换为图片标签
     * 使用 mermaid.ink 在线API
     */
    private String convertMermaidToImageTag(String mermaidCode) {
        try {
            // 对 Mermaid 代码进行 Base64 编码
            String encoded = java.util.Base64.getEncoder().encodeToString(mermaidCode.getBytes("UTF-8"));
            
            // 使用 mermaid.ink API
            // 格式: https://mermaid.ink/img/<base64_encoded_code>
            String imageUrl = "https://mermaid.ink/img/" + encoded;
            
            // 返回 Markdown 图片语法
            return "\n![Mermaid Diagram](" + imageUrl + ")\n";
            
        } catch (Exception e) {
            // 如果转换失败，返回原始代码块
            return "\n```mermaid\n" + mermaidCode + "\n```\n";
        }
    }

    private String renderMarkdown(String md) {
        MutableDataSet opts = new MutableDataSet();
        // 关键：将软换行渲染为 <br/>，避免 JEditorPane 折叠换行
        opts.set(HtmlRenderer.SOFT_BREAK, "<br/>");
        // 启用表格扩展
        opts.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));
        Parser parser = Parser.builder(opts).build();
        HtmlRenderer renderer = HtmlRenderer.builder(opts).build();
        Node doc = parser.parse(md == null ? "" : md);
        return renderer.render(doc);
    }

    /**
     * 规范化 Markdown 表格：
     * - 在表格块前后确保有一个空行
     * - 去除表格行首多余的竖线左侧空白
     */
    private String normalizeMarkdownTables(String src) {
        if (src == null || src.isEmpty()) return src;
        String[] lines = src.split("\n", -1);
        StringBuilder out = new StringBuilder(src.length() + 64);
        boolean inTableBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            boolean isFence = trimmed.startsWith("```");
            boolean isRow = !isFence && looksLikeTableRow(trimmed);
            boolean isSep = !isFence && looksLikeTableSeparator(trimmed);

            if (!inTableBlock && (isRow || isSep)) {
                // 表格块开始：确保表格前有一个空行（即前面以两个换行结尾）
                if (out.length() > 0) {
                    int len = out.length();
                    boolean hasBlank = len >= 2 && out.charAt(len - 1) == '\n' && out.charAt(len - 2) == '\n';
                    if (!hasBlank) out.append('\n');
                }
                inTableBlock = true;
            } else if (inTableBlock && !(isRow || isSep)) {
                // 表格块结束：补空行
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                out.append('\n');
                inTableBlock = false;
            }

            if (inTableBlock && (isRow || isSep)) {
                // 简单规范化：两端加上竖线
                String t = trimmed;
                // 避免影响对齐符号（:-:）的列，两侧只补缺失的一侧
                if (!t.startsWith("|")) t = "|" + t;
                if (!t.endsWith("|")) t = t + "|";
                out.append(t).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private boolean looksLikeTableRow(String trimmed) {
        // 至少包含两个竖线，且不是标题/列表行
        long bars = trimmed.chars().filter(ch -> ch == '|').count();
        if (bars < 2) return false;
        if (trimmed.startsWith("#") || trimmed.startsWith("*") || trimmed.startsWith("-") && !trimmed.contains("|")) return false;
        return true;
    }

    private boolean looksLikeTableSeparator(String trimmed) {
        if (!trimmed.contains("|")) return false;
        String withoutPipes = trimmed.replace("|", "").trim();
        // 仅由 - : 和 空格 组成
        for (int i = 0; i < withoutPipes.length(); i++) {
            char c = withoutPipes.charAt(i);
            if (!(c == '-' || c == ':' || Character.isWhitespace(c))) return false;
        }
        // 至少包含一个 -
        return withoutPipes.indexOf('-') >= 0;
    }

    private String replaceAllLatexWithImages(String text) {
        if (text == null) return "";
        String out = text;
        out = replaceByRegex(out, "\\$\\$(.+?)\\$\\$", true);
        out = replaceByRegex(out, "\\\\\\[(.+?)\\\\\\]", true);
        out = replaceByRegex(out, "\\\\\\((.+?)\\\\\\)", false);
        return out;
    }

    private final java.util.Map<String, BufferedImage> latexImageCache = new java.util.HashMap<>();
    private BufferedImage renderLatexToImage(String latex) {
        try{
            if (latexImageCache.containsKey(latex)) return latexImageCache.get(latex);
            TeXFormula formula = new TeXFormula(latex);
            TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20f);
            BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setColor(new Color(0,0,0,0));
            g2.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2.setColor(Color.BLACK);
            icon.paintIcon(new JLabel(), g2, 0, 0);
            g2.dispose();
            latexImageCache.put(latex, image);
            return image;
        }catch(Exception ex){
            return new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        }
    }

    private String replaceByRegex(String input, String regex, boolean block) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String latex = m.group(1);
            try {
                BufferedImage img = renderLatexToImage(latex);
                // 将图片写入临时文件，通过 file:// URI 嵌入，避免 JEditorPane 不支持 data URI 的问题
                File dir = new File(System.getProperty("java.io.tmpdir"), "fastpig_preview");
                if (!dir.exists()) dir.mkdirs();
                String name = Integer.toHexString((latex+"|"+block).hashCode()) + ".png";
                File f = new File(dir, name);
                ImageIO.write(img, "png", f);
                String style = block ? "display:block;margin:8px 0;" : "vertical-align:middle;";
                String tag = "<img style='"+style+"' src='" + f.toURI().toString() + "'/>";
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(tag));
            } catch (Exception e) {
                m.appendReplacement(sb, "<span style='color:#c00'>公式错误</span>");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void updateSuggestions(){
        // 仅在首行时触发联想
        int caret = bodyArea.getCaretPosition();
        int firstNl = bodyArea.getText().indexOf('\n');
        if (firstNl >= 0 && caret > firstNl) { suggestPopup.setVisible(false); return; }
        String[] parsed = parseFirstLine(bodyArea.getText());
        String q = parsed[1].isEmpty()? parsed[2] : parsed[1];
        if (q == null) q = "";
        if (q.isEmpty()) { suggestPopup.setVisible(false); return; }
        // 优先 key 前缀，无结果再按描述包含
        List<NoteDto> list = repository.searchByKeyPrefix(q, 20);
        if (list.isEmpty()) list = repository.searchByDescContains(q, 20);
        suggestModel.clear();
        for (NoteDto n : list){
            String key = (n.key!=null?n.key:"");
            String desc = (n.desc!=null?n.desc: n.title!=null?n.title:"");
            suggestModel.addElement(key + (key.isEmpty()? "": " ") + desc);
        }
        if (suggestModel.size()>0){
            // 如果只剩一项，自动选中它
            if (suggestModel.size() == 1) {
                suggestSelectedIndex = 0;
                suggestList.setSelectedIndex(0);
                suggestList.ensureIndexIsVisible(0);
            }
            try{
                // 在正文首行下方显示
                Rectangle r = bodyArea.modelToView(Math.min(firstNl>=0? firstNl : bodyArea.getText().length(), bodyArea.getCaretPosition()));
                if (r == null) r = new Rectangle(0, 0, 400, bodyArea.getFontMetrics(bodyArea.getFont()).getHeight());
                suggestPopup.show(bodyArea, 0, r.y + r.height);
                javax.swing.SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
            }catch(Exception ignored){}
        }else{
            suggestPopup.setVisible(false);
        }
    }

    private void applySuggestion(){
        String s;
        if (suggestSelectedIndex >= 0 && suggestSelectedIndex < suggestModel.size()) {
            s = suggestModel.get(suggestSelectedIndex);
        } else {
            s = suggestModel.size()>0 ? suggestModel.get(0) : null;
        }
        if (s==null || s.trim().isEmpty()) return;
        String key; String desc = "";
        int sp = s.indexOf(' ');
        if (sp > 0) { key = s.substring(0, sp).trim(); desc = s.substring(sp+1).trim(); }
        else { key = s.trim(); }
        // 替换首行为“key 空格 desc”，其余正文保持
        String text = bodyArea.getText();
        int nl = text.indexOf('\n');
        String rest = nl >= 0 ? text.substring(nl) : "";
        bodyArea.setText(key + (desc.isEmpty()? "": (" " + desc)) + rest);
        updateFirstLineHighlight();
        suggestPopup.setVisible(false);
        suggestSelectedIndex = -1;
        List<NoteDto> list = repository.searchByKeyOrText(key, 1);
        if (!list.isEmpty()) {
            loadNote(list.get(0));
        }
    }

    private void loadNote(NoteDto n){
        if (n == null) return;
        current = n;
        String first = (n.key==null? "" : n.key) + (n.desc!=null && !n.desc.isEmpty()? (" " + n.desc) : "");
        String body = n.bodyMd==null? "" : n.bodyMd;
        if (!body.startsWith("\n") && !body.isEmpty()) body = "\n" + body;
        bodyArea.setText(first + body);
        // 将光标移到文档开头（首行末尾）
        bodyArea.setCaretPosition(first.length());
        updateFirstLineHighlight();
        updateEditorStatus();
    }

    private void doSearchFromFirstLine() {
        String[] parsed = parseFirstLine(bodyArea.getText());
        String q = parsed[1].isEmpty()? parsed[2] : parsed[1];
        if (q==null) q="";
        List<NoteDto> list = repository.searchByKeyOrText(q.trim(), 1);
        if (!list.isEmpty()) {
            loadNote(list.get(0));
        }
    }

    private void clearEditor() {
        current = null;
        bodyArea.setText("");
        bodyArea.requestFocus();
        updateFirstLineHighlight();
        updateEditorStatus();
    }

    private void saveNew(boolean manual) {
        String[] parsed = splitFirstLineAndBody(bodyArea.getText());
        if (parsed[1].isEmpty()) { JOptionPane.showMessageDialog(this, "首行需包含快捷命令", "校验", JOptionPane.WARNING_MESSAGE); return; }
        // 保存光标位置
        int caretPos = bodyArea.getCaretPosition();
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = parsed[1];
        n.desc = parsed[2];
        n.title = n.desc;
        n.tags = new java.util.ArrayList<>();
        n.bodyMd = parsed[3];
        n.frontMatter = null;
        long now = System.currentTimeMillis();
        n.createdAt = now;
        n.updatedAt = now;
        n.version = 1;
        repository.save(n);
        if (manual) JOptionPane.showMessageDialog(this, "已保存为新", "提示", JOptionPane.INFORMATION_MESSAGE);
        // 只更新 current，不重新加载文本
        current = n;
        updateEditorStatus();
        // 恢复光标位置
        try {
            bodyArea.setCaretPosition(Math.min(caretPos, bodyArea.getText().length()));
        } catch (Exception ignored) {}
    }

    private void updateCurrent(boolean manual) {
        if (current == null) {
            // 等价保存为新
            saveNew(manual);
            return;
        }
        String[] parsed = splitFirstLineAndBody(bodyArea.getText());
        if (parsed[1].isEmpty()) { JOptionPane.showMessageDialog(this, "首行需包含快捷命令", "校验", JOptionPane.WARNING_MESSAGE); return; }
        // 保存光标位置
        int caretPos = bodyArea.getCaretPosition();
        String newKey = parsed[1];
        // 如果快捷命令已改变，则按"保存为新"处理；否则更新当前
        if (!newKey.equals(current.key)) {
            NoteDto n = new NoteDto();
            n.id = UUID.randomUUID().toString();
            n.key = newKey;
            n.desc = parsed[2];
            n.title = n.desc;
            n.tags = new java.util.ArrayList<>();
            n.bodyMd = parsed[3];
            n.frontMatter = null;
            long now = System.currentTimeMillis();
            n.createdAt = now;
            n.updatedAt = now;
            n.version = 1;
            repository.save(n);
            if (manual) JOptionPane.showMessageDialog(this, "已保存为新（快捷命令已变更）", "提示", JOptionPane.INFORMATION_MESSAGE);
            // 只更新 current，不重新加载文本
            current = n;
            updateEditorStatus();
            // 恢复光标位置
            try {
                bodyArea.setCaretPosition(Math.min(caretPos, bodyArea.getText().length()));
            } catch (Exception ignored) {}
            return;
        }
        current.key = newKey;
        current.desc = parsed[2];
        current.title = current.desc;
        current.tags = new java.util.ArrayList<>();
        current.bodyMd = parsed[3];
        current.updatedAt = System.currentTimeMillis();
        current.version = Math.max(1, current.version + 1);
        repository.save(current);
        if (manual) JOptionPane.showMessageDialog(this, "已更新", "提示", JOptionPane.INFORMATION_MESSAGE);
        // 只更新状态，不重新加载文本
        updateEditorStatus();
        // 恢复光标位置
        try {
            bodyArea.setCaretPosition(Math.min(caretPos, bodyArea.getText().length()));
        } catch (Exception ignored) {}
    }

    private void saveUnified(boolean manual) {
        try{
            statusLeft.setText(manual? "保存中…" : "自动保存中…");
            if (current == null) {
                saveNew(manual);
            } else {
                updateCurrent(manual);
            }
            lastSavedAt = System.currentTimeMillis();
            statusLeft.setText(manual? "已保存" : "已自动保存");
            updateEditorStatus();
        }catch(Exception ex){
            statusLeft.setText("保存失败（重试）");
        }
    }

    private void deleteCurrent() {
        if (current == null) {
            JOptionPane.showMessageDialog(this, "未选择条目", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int opt = JOptionPane.showConfirmDialog(this, "确认删除（可恢复）?", "确认", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            repository.softDelete(current.id);
            // 保存最近一次删除，用于撤销
            lastDeletedKey = current.key;
            lastDeletedExpireAt = System.currentTimeMillis() + 60000; // 60秒内可撤销
            clearEditor(); // 删除后不再自动加载下一条，保持界面空白
            startUndoCountdown(60);
        }
    }

    // 撤销删除（Alt+Z 在5秒内有效）
    private String lastDeletedKey;
    private long lastDeletedExpireAt = 0L;
    private javax.swing.Timer undoCountdownTimer;
    private int undoSecondsRemaining = 0;

    private void startUndoCountdown(int seconds){
        try{
            if (undoCountdownTimer != null) { undoCountdownTimer.stop(); }
            undoSecondsRemaining = Math.max(0, seconds);
            updateUndoStatusBar();
            undoCountdownTimer = new javax.swing.Timer(1000, e -> {
                undoSecondsRemaining--;
                if (undoSecondsRemaining <= 0) {
                    stopUndoCountdown();
                    // 撤销期结束
                    lastDeletedKey = null;
                    lastDeletedExpireAt = 0L;
                    statusLeft.setText("就绪");
                } else {
                    updateUndoStatusBar();
                }
            });
            undoCountdownTimer.setRepeats(true);
            undoCountdownTimer.start();
        }catch(Exception ignored){}
    }

    private void stopUndoCountdown(){
        if (undoCountdownTimer != null) {
            undoCountdownTimer.stop();
            undoCountdownTimer = null;
        }
        undoSecondsRemaining = 0;
    }

    private void updateUndoStatusBar(){
        statusLeft.setText("已删除，" + undoSecondsRemaining + "秒内按 Alt+Z 撤销");
    }

    private static String orDefault(String s, String d) { return (s==null||s.trim().isEmpty())?d:s; }
    private static String trimOrNull(String s) { return (s==null)?null:(s.trim().isEmpty()?null:s.trim()); }

    // 首行高亮更新：选中 0..首个换行（或全文长度）
    private void updateFirstLineHighlight(){
        try{
            Highlighter hl = bodyArea.getHighlighter();
            if (firstLineHighlightTag != null){
                hl.removeHighlight(firstLineHighlightTag);
                firstLineHighlightTag = null;
            }
            String text = bodyArea.getText();
            if (text == null) return;
            int end = text.indexOf('\n');
            if (end < 0) end = text.length();
            if (end > 0){
                firstLineHighlightTag = hl.addHighlight(0, end, firstLinePainter);
            }
        }catch(Exception ignored){}
    }

    private void undoSoftDelete(){
        long now = System.currentTimeMillis();
        if (lastDeletedKey == null || now > lastDeletedExpireAt){
            JOptionPane.showMessageDialog(this, "撤销已过期", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try{
            repository.restoreByKey(lastDeletedKey);
            NoteDto n = repository.findByKey(lastDeletedKey);
            lastDeletedKey = null;
            lastDeletedExpireAt = 0L;
            stopUndoCountdown();
            statusLeft.setText("已恢复");
            if (n != null) loadNote(n);
        }catch(Exception ex){
            JOptionPane.showMessageDialog(this, "恢复失败: "+ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 更新状态栏右侧：行:列、字数、更新时间
    private void updateEditorStatus(){
        try{
            int caret = bodyArea.getCaretPosition();
            String text = bodyArea.getText();
            int line = 1, col = 1;
            for (int i = 0; i < Math.min(caret, text.length()); i++){
                if (text.charAt(i) == '\n'){ line++; col = 1; } else { col++; }
            }
            int len = text == null ? 0 : text.length();
            String time = lastSavedAt == 0 ? "" : new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(lastSavedAt));
            statusRight.setText("" + line + ":" + col + "  |  " + len + "字  " + (time.isEmpty()? "": (" |  更新时间 " + time)));
        }catch(Exception ignored){}
    }

    // 解析首行：返回 [rawFirstLine, key, desc]
    private String[] parseFirstLine(String text){
        if (text == null) return new String[]{"","",""};
        int i = text.indexOf('\n');
        String first = i>=0? text.substring(0, i) : text;
        String key = ""; String desc = "";
        String t = first.trim();
        if (!t.isEmpty()){
            int sp = t.indexOf(' ');
            if (sp < 0){ key = t; }
            else { key = t.substring(0, sp).trim(); desc = t.substring(sp+1).trim(); }
        }
        return new String[]{first, key, desc};
    }

    // 拆分首行+正文：返回 [firstLine, key, desc, body]
    private String[] splitFirstLineAndBody(String text){
        if (text == null) return new String[]{"","","",""};
        int i = text.indexOf('\n');
        String first = i>=0? text.substring(0, i) : text;
        String body = i>=0? text.substring(i+1) : "";
        String[] f = parseFirstLine(first);
        // key 校验：去除多余空白，取第一个片段
        String key = f[1] == null ? "" : f[1].trim();
        if (key.contains(" ")) key = key.replaceAll("\\s+"," ").split(" ")[0];
        return new String[]{first, key, f[2], body};
    }

    // 判断首行是否为 "非空key + 空格 + 非空desc" 结构
    private boolean isFirstLineStructured(){
        String[] f = parseFirstLine(bodyArea.getText());
        String key = f[1] == null? "" : f[1].trim();
        String desc = f[2] == null? "" : f[2].trim();
        if (key.isEmpty() || key.contains(" ")) return false;
        return !desc.isEmpty();
    }

    /**
     * 设置窗口图标
     */
    private void setWindowIcon() {
        try {
            // 从资源文件加载图标
            java.io.InputStream iconStream = getClass().getResourceAsStream("/icons/FastPig.png");
            if (iconStream != null) {
                BufferedImage iconImage = javax.imageio.ImageIO.read(iconStream);
                setIconImage(iconImage);
                System.out.println("[图标] 窗口图标加载成功");
            } else {
                System.err.println("[图标] 未找到图标文件: /icons/FastPig.png");
            }
        } catch (Exception e) {
            System.err.println("[图标] 加载窗口图标失败: " + e.getMessage());
        }
    }

    /**
     * 初始化系统托盘
     */
    private void initSystemTray() {
        // 检查系统是否支持托盘
        if (!SystemTray.isSupported()) {
            System.out.println("[托盘] 系统不支持托盘功能");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();
            
            // 创建托盘图标（使用简单的图标）
            Image trayImage = createTrayIcon();
            
            // 创建弹出菜单
            PopupMenu popup = new PopupMenu();
            
            // 显示主窗口（使用英文避免乱码）
            MenuItem showItem = new MenuItem("Show Window");
            showItem.addActionListener(e -> showFromTray());
            popup.add(showItem);
            
            popup.addSeparator();
            
            // 退出程序（使用英文避免乱码）
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> exitApplication());
            popup.add(exitItem);
            
            // 创建托盘图标
            trayIcon = new TrayIcon(trayImage, "FastPig - 快捷命令助手", popup);
            trayIcon.setImageAutoSize(true);
            
            // 双击托盘图标显示窗口
            trayIcon.addActionListener(e -> showFromTray());
            
            // 添加到系统托盘
            tray.add(trayIcon);
            
            System.out.println("[托盘] 系统托盘初始化成功");
            
        } catch (Exception e) {
            System.err.println("[托盘] 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建托盘图标
     */
    private Image createTrayIcon() {
        try {
            // 尝试从资源文件加载用户的 logo
            java.io.InputStream iconStream = getClass().getResourceAsStream("/icons/FastPig.png");
            if (iconStream != null) {
                BufferedImage originalImage = javax.imageio.ImageIO.read(iconStream);
                // 缩放到 16x16 用于托盘
                BufferedImage scaledImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaledImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(originalImage, 0, 0, 16, 16, null);
                g.dispose();
                System.out.println("[托盘] 托盘图标加载成功");
                return scaledImage;
            }
        } catch (Exception e) {
            System.err.println("[托盘] 加载图标失败，使用默认图标: " + e.getMessage());
        }
        
        // 后备方案：创建简单的默认图标
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 120, 215));
        g.fillOval(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        String text = "F";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(text, x, y);
        g.dispose();
        return image;
    }

    /**
     * 隐藏窗口到托盘
     */
    private void hideToTray() {
        if (trayIcon != null) {
            setVisible(false);
            // 可选：显示托盘提示
            trayIcon.displayMessage(
                "FastPig Minimized", 
                "Double-click tray icon or right-click [Show Window] to restore",
                TrayIcon.MessageType.INFO
            );
            System.out.println("[托盘] 窗口已隐藏到托盘");
        } else {
            // 如果托盘不可用，则最小化窗口
            setState(Frame.ICONIFIED);
        }
    }

    /**
     * 从托盘恢复窗口
     */
    private void showFromTray() {
        setVisible(true);
        setState(Frame.NORMAL);
        toFront();
        requestFocus();
        System.out.println("[托盘] 从托盘恢复窗口");
    }

    /**
     * 退出应用程序（同步数据后退出）
     */
    private void exitApplication() {
        System.out.println("[退出] 正在同步数据库到云端...");
        DbSyncService.getInstance().syncToCloudSilently();
        System.out.println("[退出] 同步完成，退出程序");
        
        // 移除托盘图标
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        
        System.exit(0);
    }

    /**
     * 获取退出方法的引用（供外部调用）
     */
    public void performExit() {
        exitApplication();
    }

    /**
     * 更新状态栏左侧文本（供外部调用，如全局热键）
     */
    public void updateStatusLeft(String text) {
        SwingUtilities.invokeLater(() -> statusLeft.setText(text));
    }
    
    /**
     * 显示 `/` 快捷命令菜单
     */
    private void showSlashCommandMenu() {
        JPopupMenu slashMenu = new JPopupMenu();
        slashMenu.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        
        // 代码块 - 通用
        addSlashMenuItem(slashMenu, "💻 代码块", () -> insertTemplate("```\n\n```", 4));
        
        slashMenu.addSeparator();
        
        // 图片
        addSlashMenuItem(slashMenu, "🖼 图片(网络)", () -> insertTemplate("![描述](https://example.com/image.png)", 3));
        
        slashMenu.addSeparator();
        
        // 表格
        addSlashMenuItem(slashMenu, "📊 表格", () -> insertTemplate(
            "| 列1 | 列2 | 列3 |\n" +
            "|-----|-----|-----|\n" +
            "| 内容 | 内容 | 内容 |", 2));
        
        slashMenu.addSeparator();
        
        // 数学公式
        addSlashMenuItem(slashMenu, "🔢 行内公式", () -> insertTemplate("$  $", 1));
        addSlashMenuItem(slashMenu, "🔢 块级公式", () -> insertTemplate("$$\n\n$$", 3));
        
        slashMenu.addSeparator();
        
        // 列表
        addSlashMenuItem(slashMenu, "📝 无序列表", () -> insertTemplate("- ", 2));
        addSlashMenuItem(slashMenu, "🔢 有序列表", () -> insertTemplate("1. ", 3));
        addSlashMenuItem(slashMenu, "☑️ 任务列表", () -> insertTemplate("- [ ] ", 6));
        
        slashMenu.addSeparator();
        
        // 引用和分隔
        addSlashMenuItem(slashMenu, "💬 引用", () -> insertTemplate("> ", 2));
        addSlashMenuItem(slashMenu, "➖ 分隔线", () -> insertTemplate("\n---\n", 0));
        
        slashMenu.addSeparator();
        
        // 文本格式
        addSlashMenuItem(slashMenu, "**加粗**", () -> insertTemplate("****", 2));
        addSlashMenuItem(slashMenu, "*斜体*", () -> insertTemplate("**", 1));
        addSlashMenuItem(slashMenu, "`代码`", () -> insertTemplate("``", 1));
        addSlashMenuItem(slashMenu, "[链接](url)", () -> insertTemplate("[]()", 1));
        
        slashMenu.addSeparator();
        
        // 文本绘图（Mermaid）
        addSlashMenuItem(slashMenu, "📊 流程图", () -> insertTemplate(
            "```mermaid\n" +
            "graph TD\n" +
            "    A[开始] --> B{判断条件}\n" +
            "    B -->|是| C[执行操作1]\n" +
            "    B -->|否| D[执行操作2]\n" +
            "    C --> E[结束]\n" +
            "    D --> E\n" +
            "```", 26));
        
        addSlashMenuItem(slashMenu, "⏱️ 时序图", () -> insertTemplate(
            "```mermaid\n" +
            "sequenceDiagram\n" +
            "    participant 用户\n" +
            "    participant 系统\n" +
            "    用户->>系统: 发送请求\n" +
            "    系统->>系统: 处理请求\n" +
            "    系统-->>用户: 返回结果\n" +
            "```", 26));
        
        addSlashMenuItem(slashMenu, "📅 甘特图", () -> insertTemplate(
            "```mermaid\n" +
            "gantt\n" +
            "    title 项目进度\n" +
            "    dateFormat YYYY-MM-DD\n" +
            "    section 阶段1\n" +
            "    任务1           :a1, 2024-01-01, 30d\n" +
            "    任务2           :after a1, 20d\n" +
            "    section 阶段2\n" +
            "    任务3           :2024-02-01, 12d\n" +
            "```", 26));
        
        addSlashMenuItem(slashMenu, "🥧 饼图", () -> insertTemplate(
            "```mermaid\n" +
            "pie title 数据分布\n" +
            "    \"类别A\" : 45\n" +
            "    \"类别B\" : 30\n" +
            "    \"类别C\" : 25\n" +
            "```", 26));
        
        addSlashMenuItem(slashMenu, "🌳 思维导图", () -> insertTemplate(
            "```mermaid\n" +
            "mindmap\n" +
            "  root((中心主题))\n" +
            "    分支1\n" +
            "      子分支1.1\n" +
            "      子分支1.2\n" +
            "    分支2\n" +
            "      子分支2.1\n" +
            "      子分支2.2\n" +
            "```", 26));
        
        addSlashMenuItem(slashMenu, "📈 类图", () -> insertTemplate(
            "```mermaid\n" +
            "classDiagram\n" +
            "    class Animal {\n" +
            "        +String name\n" +
            "        +int age\n" +
            "        +eat()\n" +
            "        +sleep()\n" +
            "    }\n" +
            "    class Dog {\n" +
            "        +bark()\n" +
            "    }\n" +
            "    Animal <|-- Dog\n" +
            "```", 26));
        
        // 显示菜单
        try {
            Point p = bodyArea.modelToView2D(bodyArea.getCaretPosition()).getBounds().getLocation();
            SwingUtilities.convertPointToScreen(p, bodyArea);
            SwingUtilities.convertPointFromScreen(p, bodyArea);
            slashMenu.show(bodyArea, p.x, p.y + 20);
        } catch (Exception e) {
            slashMenu.show(bodyArea, 100, 100);
        }
    }
    
    /**
     * 添加快捷命令菜单项
     */
    private void addSlashMenuItem(JPopupMenu menu, String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> {
            action.run();
            menu.setVisible(false);
        });
        menu.add(item);
    }
    
    /**
     * 插入模板文本
     * @param template 模板文本
     * @param cursorOffset 插入后光标相对于起始位置的偏移量
     */
    private void insertTemplate(String template, int cursorOffset) {
        try {
            int pos = bodyArea.getCaretPosition();
            bodyArea.insert(template, pos);
            // 将光标移到合适位置
            bodyArea.setCaretPosition(pos + cursorOffset);
            bodyArea.requestFocus();
        } catch (Exception ignored) {}
    }
    
    /**
     * 初始化页内搜索面板
     */
    private void initSearchPanel() {
        searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        searchPanel.setBackground(new Color(245, 245, 245));
        
        JLabel searchLabel = new JLabel("查找:");
        searchField = new JTextField(20);
        searchResultLabel = new JLabel("");
        
        JButton prevBtn = new JButton("↑");
        JButton nextBtn = new JButton("↓");
        JButton closeBtn = new JButton("×");
        
        prevBtn.setMargin(new Insets(2, 8, 2, 8));
        nextBtn.setMargin(new Insets(2, 8, 2, 8));
        closeBtn.setMargin(new Insets(2, 8, 2, 8));
        
        prevBtn.addActionListener(e -> findPrevious());
        nextBtn.addActionListener(e -> findNext());
        closeBtn.addActionListener(e -> toggleSearchPanel());
        
        // 搜索框输入时实时搜索
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
        });
        
        // 搜索框回车时查找下一个
        searchField.addActionListener(e -> findNext());
        
        // ESC 关闭搜索面板
        searchField.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "closeSearch");
        searchField.getActionMap().put("closeSearch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleSearchPanel(); }
        });
        
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(searchLabel);
        leftPanel.add(searchField);
        leftPanel.add(prevBtn);
        leftPanel.add(nextBtn);
        leftPanel.add(searchResultLabel);
        
        searchPanel.add(leftPanel, BorderLayout.WEST);
        searchPanel.add(closeBtn, BorderLayout.EAST);
        
        searchPanel.setVisible(false); // 默认隐藏
    }
    
    /**
     * 切换搜索面板的显示/隐藏
     */
    private void toggleSearchPanel() {
        if (searchPanel.isVisible()) {
            // 隐藏搜索面板
            searchPanel.setVisible(false);
            getContentPane().remove(searchPanel);
            clearSearchHighlights();
            bodyArea.requestFocusInWindow();
            getContentPane().revalidate();
            getContentPane().repaint();
        } else {
            // 显示搜索面板
            // 将搜索面板添加到顶部,无论是否在预览模式
            getContentPane().add(searchPanel, BorderLayout.NORTH);
            searchPanel.setVisible(true);
            searchField.requestFocusInWindow();
            searchField.selectAll();
            getContentPane().revalidate();
            getContentPane().repaint();
        }
    }
    
    /**
     * 执行搜索
     */
    private void performSearch() {
        String keyword = searchField.getText();
        clearSearchHighlights();
        searchMatchPositions.clear();
        currentSearchIndex = -1;
        
        if (keyword.isEmpty()) {
            searchResultLabel.setText("");
            return;
        }
        
        String text = bodyArea.getText().toLowerCase();
        String searchText = keyword.toLowerCase();
        int pos = 0;
        
        while ((pos = text.indexOf(searchText, pos)) >= 0) {
            searchMatchPositions.add(pos);
            try {
                Object tag = bodyArea.getHighlighter().addHighlight(
                    pos, pos + keyword.length(), searchHighlightPainter);
                searchHighlightTags.add(tag);
            } catch (Exception e) {
                e.printStackTrace();
            }
            pos += searchText.length();
        }
        
        if (searchMatchPositions.isEmpty()) {
            searchResultLabel.setText("未找到");
        } else {
            currentSearchIndex = 0;
            highlightCurrentMatch();
            searchResultLabel.setText("1/" + searchMatchPositions.size());
        }
    }
    
    /**
     * 查找下一个匹配项
     */
    private void findNext() {
        if (searchMatchPositions.isEmpty()) return;
        
        currentSearchIndex = (currentSearchIndex + 1) % searchMatchPositions.size();
        highlightCurrentMatch();
        searchResultLabel.setText((currentSearchIndex + 1) + "/" + searchMatchPositions.size());
    }
    
    /**
     * 查找上一个匹配项
     */
    private void findPrevious() {
        if (searchMatchPositions.isEmpty()) return;
        
        currentSearchIndex = (currentSearchIndex - 1 + searchMatchPositions.size()) % searchMatchPositions.size();
        highlightCurrentMatch();
        searchResultLabel.setText((currentSearchIndex + 1) + "/" + searchMatchPositions.size());
    }
    
    /**
     * 高亮显示当前匹配项
     */
    private void highlightCurrentMatch() {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchMatchPositions.size()) return;
        
        int pos = searchMatchPositions.get(currentSearchIndex);
        bodyArea.setCaretPosition(pos);
        try {
            bodyArea.scrollRectToVisible(bodyArea.modelToView2D(pos).getBounds());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 清除所有搜索高亮
     */
    private void clearSearchHighlights() {
        for (Object tag : searchHighlightTags) {
            bodyArea.getHighlighter().removeHighlight(tag);
        }
        searchHighlightTags.clear();
    }

    // ===== 选区悬浮工具条 =====
    private void initSelectionToolbar(){
        if (selectionToolbarInitialized) return;
        selectionToolbar = new JPopupMenu();
        selectionToolbar.setBorder(BorderFactory.createLineBorder(new Color(200,200,200)));
        // 关键：工具条不抢焦点，避免打断 Shift+方向键的连续选择
        selectionToolbar.setFocusable(false);

        addSelItem(selectionToolbar, "加粗", () -> wrapSelection("**", "**"));
        addSelItem(selectionToolbar, "斜体", () -> wrapSelection("*", "*"));
        addSelItem(selectionToolbar, "删除线", () -> wrapSelection("~~", "~~"));
        selectionToolbar.addSeparator();
        addSelItem(selectionToolbar, "H1", () -> prefixLineSelection("# "));
        addSelItem(selectionToolbar, "H2", () -> prefixLineSelection("## "));
        addSelItem(selectionToolbar, "H3", () -> prefixLineSelection("### "));
        selectionToolbar.addSeparator();
        addSelItem(selectionToolbar, "行内代码", () -> wrapSelection("`", "`"));
        addSelItem(selectionToolbar, "代码块", () -> wrapSelection("```\n", "\n```"));
        selectionToolbar.addSeparator();
        addSelItem(selectionToolbar, "链接", () -> wrapSelection("[", "](url)"));
        addSelItem(selectionToolbar, "图片", () -> wrapSelection("![", "](url)"));
        selectionToolbar.addSeparator();
        // 颜色预设
        addSelItem(selectionToolbar, "红字", () -> wrapSelection("<span style=\"color:#e53935\">", "</span>"));
        addSelItem(selectionToolbar, "黄底", () -> wrapSelection("<span style=\"background:yellow\">", "</span>"));
        addSelItem(selectionToolbar, "蓝字", () -> wrapSelection("<span style=\"color:#1890ff\">", "</span>"));

        selectionToolbarInitialized = true;
    }

    private void addSelItem(JPopupMenu menu, String text, Runnable action){
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> { action.run(); bodyArea.requestFocusInWindow(); });
        menu.add(item);
    }

    private void toggleSelectionToolbar(){
        if (!selectionToolbarInitialized) initSelectionToolbar();
        if (selectionToolbar.isVisible()) selectionToolbar.setVisible(false);
        else showSelectionToolbarAtSelection();
    }

    private void showSelectionToolbarAtSelection(){
        try{
            int pos = Math.max(0, Math.min(bodyArea.getSelectionEnd(), bodyArea.getDocument().getLength()));
            Rectangle r = bodyArea.modelToView(pos);
            if (r == null) return;
            int x = r.x + 4;
            int y = r.y + r.height + 2;
            selectionToolbar.show(bodyArea, x, y);
            // 关键：展示后把焦点立即还给编辑器，保证后续 Shift+方向键可继续扩展选区
            javax.swing.SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
        }catch(Exception ignored){}
    }

    private void wrapSelection(String left, String right){
        try{
            int start = bodyArea.getSelectionStart();
            int end = bodyArea.getSelectionEnd();
            if (start == end) return;
            String sel = bodyArea.getSelectedText();
            bodyArea.replaceRange(left + sel + right, start, end);
            bodyArea.select(start + left.length(), start + left.length() + sel.length());
        }catch(Exception ignored){}
    }

    private void prefixLineSelection(String prefix){
        try{
            int start = bodyArea.getSelectionStart();
            int end = bodyArea.getSelectionEnd();
            int lineStart = bodyArea.getText().lastIndexOf('\n', Math.max(0, start-1)) + 1;
            int lineEnd = bodyArea.getText().indexOf('\n', end);
            if (lineEnd < 0) lineEnd = bodyArea.getText().length();
            String before = bodyArea.getText().substring(0, lineStart);
            String lines = bodyArea.getText().substring(lineStart, lineEnd);
            String after = bodyArea.getText().substring(lineEnd);
            String[] arr = lines.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<arr.length;i++){
                String l = arr[i];
                // 去掉已有的 # 前缀再加，避免重复
                String t = l.replaceFirst("^#{1,6}\\s+", "");
                sb.append(prefix).append(t);
                if (i < arr.length-1) sb.append('\n');
            }
            bodyArea.setText(before + sb.toString() + after);
            bodyArea.select(lineStart, lineStart + sb.length());
        }catch(Exception ignored){}
    }
}



