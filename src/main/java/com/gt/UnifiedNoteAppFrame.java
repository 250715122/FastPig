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
import java.io.Reader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.util.misc.Extension;
import java.util.Arrays;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Clipboard;
import java.awt.Toolkit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class UnifiedNoteAppFrame extends JFrame {
    
    /**
     * 行号显示组件
     * 显示在编辑区左侧，不属于编辑内容，复制时不会被复制
     */
    private class LineNumberComponent extends JComponent {
        private static final int PADDING = 8; // 行号与边界的间距
        
        public LineNumberComponent() {
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            setBackground(new Color(240, 240, 240));
            setForeground(new Color(128, 128, 128));
            setOpaque(true);
            
            // 监听文档变化，实时更新行号显示
            bodyArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
            });
            
            // 监听光标变化，确保行号区域及时更新
            bodyArea.addCaretListener(e -> repaint());
        }
        
        @Override
        public Dimension getPreferredSize() {
            // 计算行号区域的宽度
            int lines = getLineCount();
            int digits = Math.max(String.valueOf(lines).length(), 3); // 至少3位宽度
            FontMetrics fm = getFontMetrics(getFont());
            int width = fm.stringWidth("0") * digits + PADDING * 2;
            return new Dimension(width, bodyArea.getHeight());
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // 绘制背景
            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());
            
            // 绘制行号
            g2d.setColor(getForeground());
            g2d.setFont(getFont());
            FontMetrics fm = g2d.getFontMetrics();
            
            try {
                // 获取可见区域的起始和结束行
                Rectangle visibleRect = bodyArea.getVisibleRect();
                int startOffset = bodyArea.viewToModel2D(new Point(0, visibleRect.y));
                int endOffset = bodyArea.viewToModel2D(new Point(0, visibleRect.y + visibleRect.height));
                
                String text = bodyArea.getText();
                int startLine = getLineNumberAtOffset(text, startOffset);
                int endLine = getLineNumberAtOffset(text, endOffset);
                
                // 绘制每一行的行号
                for (int line = startLine; line <= endLine; line++) {
                    int offset = getOffsetOfLine(text, line);
                    if (offset >= 0) {
                        Rectangle r = bodyArea.modelToView2D(offset).getBounds();
                        String lineNum = String.valueOf(line);
                        int x = getWidth() - fm.stringWidth(lineNum) - PADDING;
                        int y = r.y + fm.getAscent();
                        g2d.drawString(lineNum, x, y);
                    }
                }
                
            } catch (Exception e) {
                // 如果出现异常，不影响编辑器的正常使用
            }
        }
        
        /**
         * 获取文档总行数
         */
        private int getLineCount() {
            String text = bodyArea.getText();
            if (text == null || text.isEmpty()) return 1;
            int lines = 1;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') lines++;
            }
            return lines;
        }
        
        /**
         * 获取指定偏移位置所在的行号（从1开始）
         */
        private int getLineNumberAtOffset(String text, int offset) {
            if (text == null || text.isEmpty() || offset < 0) return 1;
            int line = 1;
            for (int i = 0; i < Math.min(offset, text.length()); i++) {
                if (text.charAt(i) == '\n') line++;
            }
            return line;
        }
        
        /**
         * 获取指定行号的起始偏移位置
         */
        private int getOffsetOfLine(String text, int lineNumber) {
            if (lineNumber <= 0 || text == null) return 0;
            if (lineNumber == 1) return 0;
            
            int currentLine = 1;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    currentLine++;
                    if (currentLine == lineNumber) {
                        return i + 1;
                    }
                }
            }
            return -1; // 行号超出范围
        }
    }
    
    /**
     * 目录项数据结构
     */
    private static class TocItem {
        String text;        // 标题文本
        int level;          // 标题级别（1-6）
        int lineNumber;     // 在编辑器中的行号
        String anchorId;    // HTML预览中的锚点ID
        
        TocItem(String text, int level, int lineNumber, String anchorId) {
            this.text = text;
            this.level = level;
            this.lineNumber = lineNumber;
            this.anchorId = anchorId;
        }
        
        @Override
        public String toString() {
            return text;
        }
    }

    private final NoteRepository repository;
    private final com.gt.service.NoteService noteService;
    private TrayIcon trayIcon; // 系统托盘图标

    // 首行承载"快捷命令 空格 描述"，不再使用独立的输入框
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
                    drawShortcutGuide(g);
                }
            }catch(Exception ignored){}
        }
    };
    private final Highlighter.HighlightPainter firstLinePainter = new DefaultHighlighter.DefaultHighlightPainter(UIColors.HIGHLIGHT_FIRST_LINE);
    private Object firstLineHighlightTag;

    // 页内搜索组件
    private JPanel searchPanel;
    private JTextField searchField;
    private JLabel searchResultLabel;
    private final Highlighter.HighlightPainter searchHighlightPainter = new DefaultHighlighter.DefaultHighlightPainter(UIColors.HIGHLIGHT_SEARCH);
    private final List<Object> searchHighlightTags = new ArrayList<>();
    private int currentSearchIndex = -1;
    private final List<Integer> searchMatchPositions = new ArrayList<>();

    // 批量替换组件
    private JPanel replacePanel;
    private JTextField replaceFindField;
    private JTextField replaceWithField;
    private JLabel replaceResultLabel;
    private JCheckBox replaceCaseSensitive;
    private JCheckBox replaceUseRegex;
    private final List<Object> replaceHighlightTags = new ArrayList<>();
    private int currentReplaceIndex = -1;
    private final List<Integer> replaceMatchPositions = new ArrayList<>();
    private final List<Integer> replaceMatchLengths = new ArrayList<>();

    // 多光标管理器
    private MultiCursorManager multiCursorManager;

    private NoteDto current;

    public UnifiedNoteAppFrame(NoteRepository repository) {
        super("迅猪");
        this.repository = repository;
        this.noteService = com.gt.service.NoteService.getInstance(repository);
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
            
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                // 拦截 Alt + T（不区分左右 Alt）
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_T && e.isAltDown() && !e.isControlDown() && !e.isShiftDown()) {
                    toggleTocPanel();
                    e.consume(); // 阻止事件继续传播
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

        // 优化编辑器字体：使用等宽字体栈
        Font editorFont = new Font("Consolas", Font.PLAIN, 14);
        // 如果 Consolas 不可用，尝试其他等宽字体
        if (!editorFont.getFamily().equals("Consolas")) {
            editorFont = new Font("Monaco", Font.PLAIN, 14);
        }
        if (!editorFont.getFamily().equals("Monaco")) {
            editorFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
        }
        bodyArea.setFont(editorFont);

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
        
        // 图片粘贴功能已整合到 installPasteHandlers() → doPasteWithChoice() → tryPasteImage()
        // 注释掉此行避免 TransferHandler 冲突导致复制功能失效
        // setupImagePasteHandler();
        
        bodyScrollPane = new JScrollPane(bodyArea);
        
        // 添加行号显示组件到 JScrollPane 的左侧行头
        LineNumberComponent lineNumberComponent = new LineNumberComponent();
        bodyScrollPane.setRowHeaderView(lineNumberComponent);
        
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
        
        // 初始化批量替换面板
        initReplacePanel();
        
        // 初始化多光标管理器
        multiCursorManager = new MultiCursorManager(bodyArea);
        multiCursorManager.install();

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
        // 自动保存：10秒去抖
        javax.swing.Timer autosaveTimer = new javax.swing.Timer(10000, e -> {
            if (!isFirstLineStructured()) {
                // 首行未形成"key 空格 desc"，不执行自动保存
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
        
        // 为普通编辑模式的目录更新添加去抖定时器
        javax.swing.Timer tocUpdateTimer = new javax.swing.Timer(300, e -> {
            if (!previewVisible && tocVisibleInEditMode && tocModel != null) {
                updateTocPanel();
            }
        });
        tocUpdateTimer.setRepeats(false);
        bodyArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){ 
                if (!previewVisible && tocVisibleInEditMode) {
                    tocUpdateTimer.restart();
                }
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e){ 
                if (!previewVisible && tocVisibleInEditMode) {
                    tocUpdateTimer.restart();
                }
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e){ 
                if (!previewVisible && tocVisibleInEditMode) {
                    tocUpdateTimer.restart();
                }
            }
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
        
        // 程序内快捷键：Alt+F / 右Alt(AltGr)+F / Ctrl+Alt+F -> 全屏预览/退出全屏
        KeyStroke ksAltF = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.ALT_DOWN_MASK);
        KeyStroke ksAltGrF = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke ksCtrlAltF = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltF, "toggleFullscreenPreview");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltGrF, "toggleFullscreenPreview");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlAltF, "toggleFullscreenPreview");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltF, "toggleFullscreenPreview");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltGrF, "toggleFullscreenPreview");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksCtrlAltF, "toggleFullscreenPreview");
        root.getActionMap().put("toggleFullscreenPreview", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleFullscreenPreview(); }
        });
        
        // 程序内快捷键：Alt+T / 右Alt(AltGr)+T / Ctrl+Alt+T -> 切换目录面板
        KeyStroke ksAltT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.ALT_DOWN_MASK);
        KeyStroke ksAltGrT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke ksCtrlAltT = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltT, "toggleToc");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltGrT, "toggleToc");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlAltT, "toggleToc");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltT, "toggleToc");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksAltGrT, "toggleToc");
        root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ksCtrlAltT, "toggleToc");
        root.getActionMap().put("toggleToc", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleTocPanel(); }
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
        
        // 程序内快捷键：Alt+C / 右Alt(AltGr)+C / Ctrl+Alt+C -> 将选中文本包裹为代码块
        KeyStroke ksAltC = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.ALT_DOWN_MASK);
        KeyStroke ksAltGrC = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke ksCtrlAltC = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltC, "wrapCodeBlock");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltGrC, "wrapCodeBlock");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlAltC, "wrapCodeBlock");
        root.getActionMap().put("wrapCodeBlock", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { wrapCodeBlock(); }
        });
        
        // 程序内快捷键：Alt+X / 右Alt(AltGr)+X / Ctrl+Alt+X -> 清空编辑区
        KeyStroke ksAltX = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.ALT_DOWN_MASK);
        KeyStroke ksAltGrX = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK);
        KeyStroke ksCtrlAltX = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltX, "clearEditor");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksAltGrX, "clearEditor");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksCtrlAltX, "clearEditor");
        root.getActionMap().put("clearEditor", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { clearEditor(); }
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
        // 列表缩进/反缩进与续项
        KeyStroke ksTab = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0);
        KeyStroke ksShiftTab = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        KeyStroke ksEnter = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0);
        bodyArea.setFocusTraversalKeysEnabled(false); // 确保 Tab 由编辑器处理，不做焦点跳转
        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(ksTab, "listIndent");
        bodyArea.getActionMap().put("listIndent", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ indentSelection(true); }
        });
        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(ksShiftTab, "listOutdent");
        bodyArea.getActionMap().put("listOutdent", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ indentSelection(false); }
        });
        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(ksEnter, "listNewline");
        bodyArea.getActionMap().put("listNewline", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ continueListWithEnter(); }
        });
        
        // Ctrl+B 加粗
        KeyStroke ksBold = KeyStroke.getKeyStroke('B', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksBold, "boldSelection");
        root.getActionMap().put("boldSelection", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ wrapSelection("**", "**"); }
        });
        // Ctrl+Shift+R 标红
        KeyStroke ksRed = KeyStroke.getKeyStroke('R', java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksRed, "redSelection");
        root.getActionMap().put("redSelection", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ wrapSelection("<span style=\"color:#e53935\">", "</span>"); }
        });
        // Ctrl+R 批量替换
        KeyStroke ksReplace = KeyStroke.getKeyStroke('R', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksReplace, "showReplacePanel");
        root.getActionMap().put("showReplacePanel", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ toggleReplacePanel(); }
        });
        // Ctrl+, 打开设置
        KeyStroke ksSettings = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_COMMA, 
            java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksSettings, "openSettings");
        root.getActionMap().put("openSettings", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ openSettings(); }
        });
        // Alt+Shift+Up/Down 多光标
        KeyStroke ksMultiCursorUp = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 
            java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        KeyStroke ksMultiCursorDown = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 
            java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksMultiCursorUp, "addCaretUp");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksMultiCursorDown, "addCaretDown");
        root.getActionMap().put("addCaretUp", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ 
                if (multiCursorManager != null) multiCursorManager.addCaretUp(); 
            }
        });
        root.getActionMap().put("addCaretDown", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ 
                if (multiCursorManager != null) multiCursorManager.addCaretDown(); 
            }
        });
        // Ctrl+1..5 标题级别
        KeyStroke ksH1 = KeyStroke.getKeyStroke('1', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        KeyStroke ksH2 = KeyStroke.getKeyStroke('2', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        KeyStroke ksH3 = KeyStroke.getKeyStroke('3', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        KeyStroke ksH4 = KeyStroke.getKeyStroke('4', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        KeyStroke ksH5 = KeyStroke.getKeyStroke('5', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksH1, "h1Selection");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksH2, "h2Selection");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksH3, "h3Selection");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksH4, "h4Selection");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksH5, "h5Selection");
        root.getActionMap().put("h1Selection", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ prefixLineSelection("# "); }});
        root.getActionMap().put("h2Selection", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ prefixLineSelection("## "); }});
        root.getActionMap().put("h3Selection", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ prefixLineSelection("### "); }});
        root.getActionMap().put("h4Selection", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ prefixLineSelection("#### "); }});
        root.getActionMap().put("h5Selection", new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ prefixLineSelection("##### "); }});
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
                // 检查是否为 Alt+F 组合（全屏预览）
                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED && 
                    e.getKeyCode() == java.awt.event.KeyEvent.VK_F &&
                    (e.isAltDown() || e.isAltGraphDown())) {
                    
                    System.out.println("KeyEventDispatcher 捕获到 Alt+F，触发全屏预览切换");
                    SwingUtilities.invokeLater(() -> toggleFullscreenPreview());
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
        // 安装粘贴处理：Ctrl+V 弹窗选择，Ctrl+Shift+V 纯文本
        installPasteHandlers(root);
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
        
        // 初始化 all 命令（生成所有命令的清单）
        initializeAllCommand();
        
        // 应用窗口透明度配置
        applyWindowOpacity();
    }

    private boolean previewVisible = false;
    private boolean previewFullscreen = false; // 预览是否全屏显示
    private JSplitPane previewSplit;
    private JEditorPane htmlPane;
    private JScrollPane htmlScrollPane; // 右侧预览区的滚动面板，用于滚动同步
    private javax.swing.Timer previewTimer;
    private JScrollPane bodyScrollPane;
    
    // 目录面板相关
    private boolean tocVisible = false; // 预览模式下目录面板默认隐藏
    private boolean tocVisibleInEditMode = false; // 普通编辑模式下目录面板默认隐藏
    private JSplitPane previewWithTocSplit; // 预览区与目录的分割面板
    private JSplitPane editWithTocSplit; // 编辑模式下编辑区与目录的分割面板
    private JPanel tocPanel; // 目录面板容器
    private JList<TocItem> tocList; // 目录列表组件
    private DefaultListModel<TocItem> tocModel; // 目录数据模型
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
    // 记住文本组件原始的 TransferHandler，用于保持复制/剪切等行为
    private TransferHandler originalTransferHandler;
    
    /**
     * 让主编辑区获得焦点
     */
    public void focusEditor() {
        try {
            SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
        } catch (Exception ignored) {}
    }

    /**
     * 切换全屏预览模式
     * 如果预览未启动，先启动预览然后切换到全屏
     * 如果已是分屏预览，切换到全屏
     * 如果已是全屏，切换回分屏
     */
    private void toggleFullscreenPreview() {
        // 如果预览还未启动，先启动预览
        if (!previewVisible) {
            toggleInAppPreview();
        }
        
        // 切换全屏/分屏模式
        if (previewFullscreen) {
            // 从全屏切换回分屏
            exitFullscreenPreview();
        } else {
            // 从分屏切换到全屏
            enterFullscreenPreview();
        }
    }
    
    /**
     * 进入全屏预览模式
     */
    private void enterFullscreenPreview() {
        if (!previewVisible) return;
        
        try {
            // 移除当前的分屏布局
            getContentPane().remove(centerComponent);
            
            // 全屏显示：目录区 | 预览区（如果目录可见）
            if (tocVisible && tocPanel != null) {
                // 重新创建目录和预览的分割面板
                previewWithTocSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                        tocPanel, htmlScrollPane);
                previewWithTocSplit.setResizeWeight(0);
                centerComponent = previewWithTocSplit;
                SwingUtilities.invokeLater(() -> previewWithTocSplit.setDividerLocation(200));
            } else {
                // 只显示预览面板（全屏）
                centerComponent = htmlScrollPane;
            }
            
            getContentPane().add(centerComponent, BorderLayout.CENTER);
            
            previewFullscreen = true;
            revalidate();
            repaint();
            
            // 显示提示浮标
            showPreviewBadge("全屏预览（Alt+F退出，Alt+T切换目录）");
            
            System.out.println("[预览] 已进入全屏预览模式");
        } catch (Exception e) {
            System.err.println("[预览] 进入全屏预览失败: " + e.getMessage());
        }
    }
    
    /**
     * 退出全屏预览，返回分屏模式
     */
    private void exitFullscreenPreview() {
        if (!previewVisible || !previewFullscreen) return;
        
        try {
            // 移除全屏预览
            getContentPane().remove(centerComponent);
            
            // 恢复三栏分屏布局：编辑区 | 目录区 | 预览区
            JScrollPane leftScrollPane = new JScrollPane(bodyArea);
            LineNumberComponent lineNumber = new LineNumberComponent();
            leftScrollPane.setRowHeaderView(lineNumber);
            
            // 重新创建目录和预览的分割面板（内层）
            previewWithTocSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    tocVisible ? tocPanel : null, htmlScrollPane);
            previewWithTocSplit.setResizeWeight(0);
            if (!tocVisible) {
                previewWithTocSplit.setLeftComponent(null);
            }
            
            // 创建编辑区和(目录+预览)的分割面板（外层）
            previewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    leftScrollPane, previewWithTocSplit);
            previewSplit.setResizeWeight(0.5);
            
            centerComponent = previewSplit;
            getContentPane().add(centerComponent, BorderLayout.CENTER);
            
            previewFullscreen = false;
            revalidate();
            repaint();
            SwingUtilities.invokeLater(() -> {
                previewSplit.setDividerLocation(0.5);
                if (tocVisible) {
                    previewWithTocSplit.setDividerLocation(200);
                }
            });
            
            // 重新添加滚动同步
            addScrollSync(leftScrollPane);
            
            // 显示提示浮标
            showPreviewBadge("分屏预览（Alt+P退出，Alt+T切换目录）");
            
            System.out.println("[预览] 已退出全屏预览模式");
        } catch (Exception e) {
            System.err.println("[预览] 退出全屏预览失败: " + e.getMessage());
        }
    }
    
    /**
     * 切换目录面板显示/隐藏
     * 支持预览模式和普通编辑模式
     */
    private void toggleTocPanel() {
        if (previewVisible) {
            // 预览模式下切换目录
            tocVisible = !tocVisible;
            
            try {
                if (previewFullscreen) {
                    // 全屏模式下切换目录
                    getContentPane().remove(centerComponent);
                    
                    if (tocVisible && tocPanel != null) {
                        // 显示目录：目录区 | 预览区
                        previewWithTocSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                tocPanel, htmlScrollPane);
                        previewWithTocSplit.setResizeWeight(0);
                        centerComponent = previewWithTocSplit;
                        SwingUtilities.invokeLater(() -> previewWithTocSplit.setDividerLocation(200));
                    } else {
                        // 隐藏目录：只显示预览区
                        centerComponent = htmlScrollPane;
                    }
                    
                    getContentPane().add(centerComponent, BorderLayout.CENTER);
                } else {
                    // 分屏模式下切换目录
                    if (tocVisible && tocPanel != null) {
                        // 显示目录
                        previewWithTocSplit.setLeftComponent(tocPanel);
                        SwingUtilities.invokeLater(() -> previewWithTocSplit.setDividerLocation(200));
                    } else {
                        // 隐藏目录
                        previewWithTocSplit.setLeftComponent(null);
                    }
                }
                
                revalidate();
                repaint();
                
                String msg = tocVisible ? "目录已显示" : "目录已隐藏";
                showPreviewBadge(msg);
                System.out.println("[TOC] " + msg);
                
                // 焦点返回到编辑区
                SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
            } catch (Exception e) {
                System.err.println("[TOC] 切换目录失败: " + e.getMessage());
            }
        } else {
            // 普通编辑模式下切换目录
            tocVisibleInEditMode = !tocVisibleInEditMode;
            
            try {
                getContentPane().remove(centerComponent);
                
                if (tocVisibleInEditMode) {
                    // 显示目录：编辑区 | 目录区
                    // 创建或更新目录面板
                    if (tocPanel == null) {
                        tocPanel = createTocPanel();
                    }
                    updateTocPanel();
                    
                    // 创建编辑区和目录区的分割面板
                    editWithTocSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                            bodyScrollPane, tocPanel);
                    editWithTocSplit.setResizeWeight(1.0); // 编辑区占据大部分空间
                    
                    // 创建包含分割面板和状态栏的面板
                    JPanel containerPanel = new JPanel(new BorderLayout(8, 8));
                    containerPanel.add(editWithTocSplit, BorderLayout.CENTER);
                    containerPanel.add(statusBar, BorderLayout.SOUTH);
                    
                    centerComponent = containerPanel;
                    getContentPane().add(centerComponent, BorderLayout.CENTER);
                    
                    SwingUtilities.invokeLater(() -> {
                        int width = getWidth();
                        editWithTocSplit.setDividerLocation(width - 200 - 10);
                    });
                } else {
                    // 隐藏目录：只显示编辑区
                    JPanel editorOnlyPanel = new JPanel(new BorderLayout(8, 8));
                    editorOnlyPanel.add(bodyScrollPane, BorderLayout.CENTER);
                    editorOnlyPanel.add(statusBar, BorderLayout.SOUTH);
                    
                    centerComponent = editorOnlyPanel;
                    getContentPane().add(centerComponent, BorderLayout.CENTER);
                }
                
                revalidate();
                repaint();
                
                String msg = tocVisibleInEditMode ? "目录已显示" : "目录已隐藏";
                showPreviewBadge(msg);
                System.out.println("[TOC] 编辑模式 - " + msg);
                
                // 焦点返回到编辑区
                SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
            } catch (Exception e) {
                System.err.println("[TOC] 编辑模式切换目录失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 添加滚动同步监听器
     */
    private void addScrollSync(JScrollPane leftScrollPane) {
        leftScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                try {
                    JScrollBar leftBar = leftScrollPane.getVerticalScrollBar();
                    JScrollBar rightBar = htmlScrollPane.getVerticalScrollBar();
                    
                    int leftMax = leftBar.getMaximum() - leftBar.getVisibleAmount();
                    if (leftMax > 0) {
                        double scrollPercent = (double) leftBar.getValue() / leftMax;
                        int rightMax = rightBar.getMaximum() - rightBar.getVisibleAmount();
                        int rightValue = (int) (scrollPercent * rightMax);
                        rightBar.setValue(rightValue);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }
    
    /**
     * 显示预览提示浮标
     */
    private void showPreviewBadge(String text) {
        try {
            JLayeredPane layered = getLayeredPane();
            JLabel badge = new JLabel(text);
            badge.setOpaque(true);
            badge.setBackground(new Color(0,0,0,150));
            badge.setForeground(Color.WHITE);
            badge.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
            Dimension sz = badge.getPreferredSize();
            int x = getWidth() - sz.width - 24;
            int y = 12;
            badge.setBounds(x, y, sz.width, sz.height);
            layered.add(badge, JLayeredPane.POPUP_LAYER);
            javax.swing.Timer t = new javax.swing.Timer(1500, e -> {
                layered.remove(badge);
                layered.revalidate();
                layered.repaint();
            });
            t.setRepeats(false);
            t.start();
        } catch (Exception ignored) {}
    }
    
    /**
     * 创建目录面板UI
     */
    private JPanel createTocPanel() {
        tocModel = new DefaultListModel<>();
        tocList = new JList<>(tocModel);
        tocList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tocList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        // 自定义渲染器实现标题层级缩进
        tocList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                
                if (value instanceof TocItem) {
                    TocItem item = (TocItem) value;
                    // 根据层级缩进：每级缩进 15 像素
                    int indent = (item.level - 1) * 15;
                    label.setBorder(BorderFactory.createEmptyBorder(3, indent + 5, 3, 5));
                    
                    // 根据层级设置不同的字体大小
                    int fontSize = 12;
                    if (item.level == 1) fontSize = 14;
                    else if (item.level == 2) fontSize = 13;
                    label.setFont(new Font(Font.SANS_SERIF, 
                            item.level <= 2 ? Font.BOLD : Font.PLAIN, fontSize));
                }
                
                return label;
            }
        });
        
        // 添加点击监听器
        tocList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                TocItem selected = tocList.getSelectedValue();
                if (selected != null) {
                    jumpToHeading(selected);
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(tocList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("目录"));
        
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(200, 0));
        panel.setMinimumSize(new Dimension(200, 0));
        
        return panel;
    }
    
    /**
     * 更新目录列表
     */
    private void updateTocPanel() {
        if (tocModel == null) return;
        
        String md = bodyArea.getText();
        List<TocItem> toc = extractTocFromMarkdown(md);
        
        tocModel.clear();
        for (TocItem item : toc) {
            tocModel.addElement(item);
        }
    }
    
    /**
     * 跳转到指定标题
     * 同时滚动编辑区和预览区到对应位置
     */
    private void jumpToHeading(TocItem item) {
        if (item == null) return;
        
        try {
            // 跳转编辑区：定位到指定行号
            String text = bodyArea.getText();
            String[] lines = text.split("\n", -1);
            
            if (item.lineNumber > 0 && item.lineNumber <= lines.length) {
                // 计算目标行的偏移位置
                int offset = 0;
                for (int i = 0; i < item.lineNumber - 1 && i < lines.length; i++) {
                    offset += lines[i].length() + 1; // +1 for newline
                }
                
                // 设置光标位置并滚动到可见区域
                bodyArea.setCaretPosition(offset);
                bodyArea.requestFocusInWindow();
                
                // 尝试将目标行滚动到视口中央
                try {
                    Rectangle rect = bodyArea.modelToView(offset);
                    if (rect != null) {
                        Rectangle visibleRect = bodyArea.getVisibleRect();
                        rect.y = Math.max(0, rect.y - visibleRect.height / 2);
                        bodyArea.scrollRectToVisible(rect);
                    }
                } catch (Exception e) {
                    // 忽略滚动错误
                }
            }
            
            // 跳转预览区：使用锚点ID
            if (htmlPane != null && item.anchorId != null && !item.anchorId.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    htmlPane.scrollToReference(item.anchorId);
                });
            }
        } catch (Exception e) {
            System.err.println("[TOC] 跳转失败: " + e.getMessage());
        }
    }
    
    private void toggleInAppPreview() {
        if (!previewVisible) {
            // 创建右侧 HTML 预览（离线，无JS），公式以内联图片呈现
            htmlPane = new JEditorPane();
            htmlPane.setEditable(false);
            htmlPane.setContentType("text/html;charset=UTF-8");

            // 为预览模式创建带行号的 JScrollPane
            JScrollPane leftScrollPane = new JScrollPane(bodyArea);
            LineNumberComponent previewLineNumberComponent = new LineNumberComponent();
            leftScrollPane.setRowHeaderView(previewLineNumberComponent);
            
            // 创建右侧预览区的滚动面板
            htmlScrollPane = new JScrollPane(htmlPane);
            
            // 创建目录面板
            tocPanel = createTocPanel();
            
            // 创建目录区和预览区的分割面板（内层）
            // 布局：目录区 | 预览区
            previewWithTocSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    tocVisible ? tocPanel : null, htmlScrollPane);
            previewWithTocSplit.setResizeWeight(0);
            if (!tocVisible) {
                previewWithTocSplit.setLeftComponent(null);
            }
            
            // 创建编辑区和(目录+预览)的分割面板（外层）
            // 布局：编辑区 | (目录区 + 预览区)
            previewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    leftScrollPane, previewWithTocSplit);
            previewSplit.setResizeWeight(0.5);
            
            getContentPane().remove(centerComponent);
            centerComponent = previewSplit;
            getContentPane().add(centerComponent, BorderLayout.CENTER);
            revalidate();
            repaint();
            SwingUtilities.invokeLater(() -> {
                previewSplit.setDividerLocation(0.5);
                if (tocVisible) {
                    previewWithTocSplit.setDividerLocation(200);
                }
            });
            
            // 添加滚动同步
            addScrollSync(leftScrollPane);

            refreshInAppPreview();
            previewVisible = true;
            previewFullscreen = false; // 初始为分屏模式
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
            
            // 焦点返回到编辑区
            SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
        } else {
            // 关闭预览，恢复原布局
            getContentPane().remove(centerComponent);
            bodyScrollPane = new JScrollPane(bodyArea);
            
            // 添加行号显示组件（恢复普通模式时也需要行号）
            LineNumberComponent normalLineNumberComponent = new LineNumberComponent();
            bodyScrollPane.setRowHeaderView(normalLineNumberComponent);
            
            JPanel editor2 = new JPanel(new BorderLayout(8, 8));
            editor2.add(bodyScrollPane, BorderLayout.CENTER);
            editor2.add(statusBar, BorderLayout.SOUTH);
            centerComponent = editor2;
            getContentPane().add(centerComponent, BorderLayout.CENTER);
            revalidate();
            repaint();
            previewVisible = false;
            previewFullscreen = false; // 重置全屏状态
            tocVisible = false; // 重置目录可见状态为默认值（隐藏）
            
            // 焦点返回到编辑区
            SwingUtilities.invokeLater(() -> bodyArea.requestFocusInWindow());
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
        // 规范化将标题后的 (#id) 转为 {#id}
        String normalized = normalizeHeadingAnchors(mdWithImgs);
        // 为没有锚点ID的标题自动添加锚点
        String withAnchors = addHeadingAnchors(normalized);
        String html = renderMarkdown(withAnchors);
        
        // 将相对图片路径转换为绝对路径（用于预览本地图片）
        html = convertImagePathsToAbsolute(html);
        
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
        
        // 更新目录面板
        updateTocPanel();
        
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
        
        // 匹配 ```mermaid ... ``` 代码块（更宽松的匹配）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "```mermaid\\s*([\\s\\S]*?)```",
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
     * 清理 Mermaid 代码中的特殊字符和格式问题
     */
    private String cleanMermaidCode(String mermaidCode) {
        if (mermaidCode == null || mermaidCode.isEmpty()) return mermaidCode;
        
        String cleaned = mermaidCode;
        
        // 1. 移除零宽字符（常见的不可见字符）
        cleaned = cleaned.replaceAll("[\u200B\u200C\u200D\uFEFF]", "");
        
        // 2. 移除每行开头和结尾的竖线（当它们不是合法的 Mermaid 语法时）
        // 检测模式：行首的 | + 非空格内容，或内容 + 行尾的 |
        cleaned = cleaned.replaceAll("(?m)^\\s*\\|([^|])", "$1");  // 行首的单个 |
        cleaned = cleaned.replaceAll("(?m)([^|])\\|\\s*$", "$1");  // 行尾的单个 |
        
        // 3. 规范化行尾空白
        cleaned = cleaned.replaceAll("(?m)[ \\t]+$", "");
        
        // 4. 移除多余的空行（保留最多一个空行）
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        
        return cleaned.trim();
    }
    
    /**
     * 预处理 Mermaid 代码以提高兼容性
     */
    private String preprocessMermaidCode(String mermaidCode) {
        String processed = mermaidCode;
        
        // 1. 将 flowchart 转换为 graph（兼容旧版本 Mermaid，作为兜底）
        processed = processed.replaceAll("(?m)^\\s*flowchart\\s+", "graph ");
        
        // 2. 移除 Font Awesome 图标前缀（如果 Kroki 不支持）
        // fa:fa-xxx 保留后面的文本
        processed = processed.replaceAll("fa:fa-\\w+\\s+", "");
        
        return processed;
    }
    
    /**
     * 将 Mermaid 代码转换为图片标签
     * 使用 kroki.io 备用API（POST 方式，支持更多 Mermaid 特性），下载图片到本地后使用 file:// 协议加载
     */
    private String convertMermaidToImageTag(String mermaidCode) {
        try {
            System.out.println("[Mermaid] 原始代码长度: " + mermaidCode.length() + " 字符");
            
            // 步骤1: 清理特殊字符
            String cleanedCode = cleanMermaidCode(mermaidCode);
            System.out.println("[Mermaid] 清理后代码:\n" + cleanedCode);
            
            // 步骤2: 语法预处理（兼容性转换）
            String processedCode = preprocessMermaidCode(cleanedCode);
            if (!processedCode.equals(cleanedCode)) {
                System.out.println("[Mermaid] 预处理后代码:\n" + processedCode);
            }
            
            // 使用 kroki.io API（POST 方式，避免 URL 长度限制）
            String apiUrl = "https://kroki.io/mermaid/png";
            System.out.println("[Mermaid] 使用 Kroki API (POST): " + apiUrl);
            
            // 下载图片到本地（使用 POST 方式）
            File localImageFile = downloadMermaidImagePost(apiUrl, processedCode);
            
            if (localImageFile != null && localImageFile.exists()) {
                String localUrl = localImageFile.toURI().toString();
                System.out.println("[Mermaid] 本地图片路径: " + localUrl);
                return "\n<img src='" + localUrl + "' alt='Mermaid Diagram' style='max-width:100%; display:block; margin:10px auto;'/>\n";
            } else {
                System.err.println("[Mermaid] 图片下载失败");
                return "\n<div style='color:orange; border:1px solid orange; padding:10px; margin:10px;'>" +
                       "⚠️ Mermaid 图表加载失败，请检查网络连接</div>\n";
            }
            
        } catch (Exception e) {
            System.err.println("[Mermaid] 转换失败: " + e.getMessage());
            e.printStackTrace();
            return "\n<div style='color:red; border:1px solid red; padding:10px; margin:10px;'>" +
                   "❌ Mermaid 图表转换失败<br/>" +
                   "错误: " + e.getMessage() + "</div>\n";
        }
    }
    
    /**
     * 下载 Mermaid 图片到本地临时目录（使用 POST 方式）
     * @param apiUrl Kroki API 的端点 URL
     * @param mermaidCode 原始 Mermaid 代码
     * @return 本地图片文件，如果下载失败返回 null
     */
    private File downloadMermaidImagePost(String apiUrl, String mermaidCode) {
        try {
            // 创建临时目录
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "fastpig_mermaid");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            // 使用 Mermaid 代码的哈希作为文件名，避免重复下载
            String fileName = Integer.toHexString(mermaidCode.hashCode()) + ".png";
            File localFile = new File(tempDir, fileName);
            
            // 如果文件已存在，直接返回（缓存）
            if (localFile.exists() && localFile.length() > 0) {
                System.out.println("[Mermaid] 使用缓存图片: " + fileName);
                return localFile;
            }
            
            // 使用 POST 请求下载图片
            System.out.println("[Mermaid] 正在通过 POST 请求下载图片...");
            java.net.URL url = new java.net.URL(apiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            
            // 发送 Mermaid 代码
            java.io.OutputStream os = conn.getOutputStream();
            os.write(mermaidCode.getBytes("UTF-8"));
            os.flush();
            os.close();
            
            int responseCode = conn.getResponseCode();
            System.out.println("[Mermaid] HTTP 响应码: " + responseCode);
            
            if (responseCode == 200) {
                // 读取图片数据
                java.io.InputStream in = conn.getInputStream();
                java.io.FileOutputStream out = new java.io.FileOutputStream(localFile);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                
                out.close();
                in.close();
                
                System.out.println("[Mermaid] 图片下载成功: " + localFile.length() + " 字节");
                return localFile;
            } else {
                // 读取错误信息
                java.io.InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(errorStream, "UTF-8"));
                    StringBuilder errorMsg = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorMsg.append(line).append("\n");
                    }
                    reader.close();
                    System.err.println("[Mermaid] 服务器错误信息: " + errorMsg.toString());
                }
                System.err.println("[Mermaid] HTTP 错误: " + responseCode);
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("[Mermaid] 下载失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String renderMarkdown(String md) {
        MutableDataSet opts = new MutableDataSet();
        // 关键：将软换行渲染为 <br/>，避免 JEditorPane 折叠换行
        opts.set(HtmlRenderer.SOFT_BREAK, "<br/>");
        // 启用表格与属性扩展（属性用于 {#id} 锚点）
        opts.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), AttributesExtension.create()));
        Parser parser = Parser.builder(opts).build();
        HtmlRenderer renderer = HtmlRenderer.builder(opts).build();
        Node doc = parser.parse(md == null ? "" : md);
        return renderer.render(doc);
    }
    
    /**
     * 从 Markdown 文本中提取目录结构
     */
    private List<TocItem> extractTocFromMarkdown(String md) {
        List<TocItem> toc = new ArrayList<>();
        if (md == null || md.isEmpty()) return toc;
        
        String[] lines = md.split("\n", -1);
        java.util.regex.Pattern headingPattern = java.util.regex.Pattern.compile("^(#{1,6})\\s+(.+?)(?:\\s*\\{#([A-Za-z0-9_-]+)\\})?\\s*$");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            java.util.regex.Matcher matcher = headingPattern.matcher(line);
            if (matcher.find()) {
                String hashes = matcher.group(1);
                String text = matcher.group(2).trim();
                String anchorId = matcher.group(3);
                
                int level = hashes.length();
                int lineNumber = i + 1; // 行号从1开始
                
                // 如果没有显式指定 anchorId，生成一个
                if (anchorId == null || anchorId.isEmpty()) {
                    // 生成简单的锚点ID：移除特殊字符，转为小写，空格转为连字符
                    anchorId = "heading-" + lineNumber;
                }
                
                toc.add(new TocItem(text, level, lineNumber, anchorId));
            }
        }
        
        return toc;
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

    // 将 "## 标题 (#id)" 规范化为 "## 标题 {#id}"，便于 AttributesExtension 识别
    private String normalizeHeadingAnchors(String src){
        if (src == null || src.isEmpty()) return src;
        String[] lines = src.split("\n", -1);
        StringBuilder out = new StringBuilder(src.length());
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("^(#{1,6}\\s+.*)\\s\\(#([A-Za-z0-9_-]+)\\)\\s*$");
        for (String line : lines){
            java.util.regex.Matcher m = p.matcher(line);
            if (m.find()){
                String left = m.group(1);
                String id = m.group(2);
                out.append(left).append(' ').append("{#").append(id).append("}").append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
    
    /**
     * 为所有标题添加锚点ID（如果尚未指定）
     */
    private String addHeadingAnchors(String src) {
        if (src == null || src.isEmpty()) return src;
        
        String[] lines = src.split("\n", -1);
        StringBuilder out = new StringBuilder(src.length());
        
        // 匹配标题行，可能已经有 {#id} 或没有
        java.util.regex.Pattern headingPattern = java.util.regex.Pattern.compile("^(#{1,6}\\s+)(.+?)(?:\\s*\\{#([A-Za-z0-9_-]+)\\})?\\s*$");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            java.util.regex.Matcher matcher = headingPattern.matcher(line);
            
            if (matcher.find()) {
                String hashes = matcher.group(1);
                String text = matcher.group(2).trim();
                String existingId = matcher.group(3);
                
                if (existingId == null || existingId.isEmpty()) {
                    // 没有锚点ID，自动生成
                    String anchorId = "heading-" + (i + 1);
                    out.append(hashes).append(text).append(" {#").append(anchorId).append("}").append('\n');
                } else {
                    // 已有锚点ID，保持原样
                    out.append(line).append('\n');
                }
            } else {
                // 不是标题行，保持原样
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
        // 替换首行为"key 空格 desc"，其余正文保持
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
        // 确保从文件加载最新正文
        try {
            NoteDto full = noteService.load(n.id);
            if (full != null) {
                n = full;
            }
        } catch (Exception e) {
            System.err.println("加载笔记正文失败: " + n.id + " - " + e.getMessage());
        }
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

    /**
     * 清空编辑区内容
     */
    private void clearEditor() {
        current = null;
        bodyArea.setText("");
        bodyArea.requestFocus();
        updateFirstLineHighlight();
        updateEditorStatus();
        statusLeft.setText("已清空");
    }

    private void saveNew(boolean manual) {
        String[] parsed = splitFirstLineAndBody(bodyArea.getText());
        if (parsed[1].isEmpty()) { JOptionPane.showMessageDialog(this, "首行需包含快捷命令", "校验", JOptionPane.WARNING_MESSAGE); return; }
        // 保存选区状态
        int selectionStart = bodyArea.getSelectionStart();
        int selectionEnd = bodyArea.getSelectionEnd();
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
        // 使用 NoteService 保存（同时写入文件和索引）
        noteService.save(n);
        // 只更新 current，不重新加载文本
        current = n;
        updateEditorStatus();
        // 恢复选区状态
        try {
            int textLength = bodyArea.getText().length();
            int start = Math.min(selectionStart, textLength);
            int end = Math.min(selectionEnd, textLength);
            bodyArea.setSelectionStart(start);
            bodyArea.setSelectionEnd(end);
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
        // 保存选区状态
        int selectionStart = bodyArea.getSelectionStart();
        int selectionEnd = bodyArea.getSelectionEnd();
        String newKey = parsed[1];
        // 如果快捷命令已改变，则按"保存为新"处理；否则更新当前
        if (!newKey.equals(current.key)) {
            JOptionPane.showMessageDialog(this, "当前版本不支持修改 key，请新建笔记后删除旧笔记。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        current.key = newKey;
        current.desc = parsed[2];
        current.title = current.desc;
        current.tags = new java.util.ArrayList<>();
        current.bodyMd = parsed[3];
        current.updatedAt = System.currentTimeMillis();
        current.version = Math.max(1, current.version + 1);
        // 使用 NoteService 保存（同时写入文件和索引）
        noteService.save(current);
        // 只更新状态，不重新加载文本
        updateEditorStatus();
        // 恢复选区状态
        try {
            int textLength = bodyArea.getText().length();
            int start = Math.min(selectionStart, textLength);
            int end = Math.min(selectionEnd, textLength);
            bodyArea.setSelectionStart(start);
            bodyArea.setSelectionEnd(end);
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
            // 使用 NoteService 删除（同时更新文件和索引）
            noteService.delete(current.id);
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

    /**
     * 生成 all 命令的内容
     * 格式：每行显示 "命令 - 描述"
     */
    private String generateAllCommandsContent() {
        List<NoteDto> allNotes = repository.findAllCommandsAndDescriptions();
        StringBuilder sb = new StringBuilder();
        sb.append("# 所有命令列表\n\n");
        sb.append("共 **").append(allNotes.size()).append("** 条命令\n\n");
        sb.append("---\n\n");
        
        for (NoteDto note : allNotes) {
            if (note.key != null && !note.key.isEmpty()) {
                String desc = note.desc != null ? note.desc : note.title;
                if (desc == null || desc.isEmpty()) desc = "(无描述)";
                sb.append("- **").append(note.key).append("** — ").append(desc).append("\n");
            }
        }
        
        sb.append("\n---\n\n");
        sb.append("*最后更新时间：").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())).append("*\n");
        
        return sb.toString();
    }

    /**
     * 初始化或更新 all 命令
     * 在程序启动时调用，生成所有命令的清单
     */
    public void initializeAllCommand() {
        try {
            // 生成 all 命令的内容
            String content = generateAllCommandsContent();
            
            // 查找是否已存在 all 命令
            NoteDto existingAll = repository.findByKey("all");
            
            if (existingAll != null) {
                // 更新现有的 all 命令
                existingAll.desc = "所有命令列表";
                existingAll.title = "所有命令列表";
                existingAll.bodyMd = content;
                existingAll.updatedAt = System.currentTimeMillis();
                existingAll.version++;
                repository.save(existingAll);
                System.out.println("[all命令] 已更新");
            } else {
                // 创建新的 all 命令
                NoteDto allCommand = new NoteDto();
                allCommand.id = UUID.randomUUID().toString();
                allCommand.key = "all";
                allCommand.desc = "所有命令列表";
                allCommand.title = "所有命令列表";
                allCommand.tags = new ArrayList<>();
                allCommand.tags.add("system");
                allCommand.bodyMd = content;
                allCommand.frontMatter = null;
                long now = System.currentTimeMillis();
                allCommand.createdAt = now;
                allCommand.updatedAt = now;
                allCommand.version = 1;
                repository.save(allCommand);
                System.out.println("[all命令] 已创建");
            }
        } catch (Exception e) {
            System.err.println("[all命令] 初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
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
        System.out.println("[退出] 正在同步数据库到云端（最多等待5秒）...");
        boolean synced = DbSyncService.getInstance().syncToCloudWithTimeout(5);
        if (synced) {
            System.out.println("[退出] 同步完成，退出程序");
        } else {
            System.out.println("[退出] 同步跳过或超时，直接退出程序");
        }
        
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
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIColors.BORDER_LIGHT),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        searchPanel.setBackground(UIColors.BG_PANEL);
        
        JLabel searchLabel = new JLabel("查找:");
        searchLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        searchLabel.setForeground(UIColors.TEXT_PRIMARY);
        searchField = UIComponents.createTextField(20);
        searchResultLabel = UIComponents.createBadgeLabel("");
        
        JButton prevBtn = UIComponents.createCompactButton("↑");
        JButton nextBtn = UIComponents.createCompactButton("↓");
        JButton closeBtn = UIComponents.createCloseButton();
        
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

    // ===== 批量替换功能 =====
    
    /**
     * 初始化批量替换面板
     */
    private void initReplacePanel() {
        replacePanel = new JPanel(new BorderLayout(8, 0));
        replacePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIColors.BORDER_LIGHT),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        replacePanel.setBackground(UIColors.BG_PANEL);
        
        // 左侧面板：输入框和按钮
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        
        // 第一行：查找
        JPanel findRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        findRow.setOpaque(false);
        JLabel findLabel = new JLabel("查找:");
        findLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        findLabel.setForeground(UIColors.TEXT_PRIMARY);
        replaceFindField = UIComponents.createTextField(25);
        JButton findPrevBtn = UIComponents.createCompactButton("上一个");
        JButton findNextBtn = UIComponents.createCompactButton("下一个");
        
        findPrevBtn.addActionListener(e -> replaceFindPrevious());
        findNextBtn.addActionListener(e -> replaceFindNext());
        
        findRow.add(findLabel);
        findRow.add(replaceFindField);
        findRow.add(findPrevBtn);
        findRow.add(findNextBtn);
        
        // 第二行：替换
        JPanel replaceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        replaceRow.setOpaque(false);
        JLabel replaceLabel = new JLabel("替换:");
        replaceLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        replaceLabel.setForeground(UIColors.TEXT_PRIMARY);
        replaceWithField = UIComponents.createTextField(25);
        JButton replaceBtn = UIComponents.createPrimaryButton("替换");
        JButton replaceAllBtn = UIComponents.createPrimaryButton("全部替换");
        
        replaceBtn.addActionListener(e -> replaceCurrentMatch());
        replaceAllBtn.addActionListener(e -> replaceAllMatches());
        
        replaceRow.add(replaceLabel);
        replaceRow.add(replaceWithField);
        replaceRow.add(replaceBtn);
        replaceRow.add(replaceAllBtn);
        
        // 第三行：选项
        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 4));
        optionsRow.setOpaque(false);
        replaceCaseSensitive = UIComponents.createCheckBox("区分大小写");
        replaceUseRegex = UIComponents.createCheckBox("正则表达式");
        replaceResultLabel = UIComponents.createBadgeLabel("");
        
        optionsRow.add(replaceCaseSensitive);
        optionsRow.add(replaceUseRegex);
        optionsRow.add(replaceResultLabel);
        
        leftPanel.add(findRow);
        leftPanel.add(replaceRow);
        leftPanel.add(optionsRow);
        
        // 右侧：关闭按钮
        JButton closeBtn = UIComponents.createCloseButton();
        closeBtn.addActionListener(e -> toggleReplacePanel());
        
        replacePanel.add(leftPanel, BorderLayout.CENTER);
        replacePanel.add(closeBtn, BorderLayout.EAST);
        
        // 查找框输入时实时搜索
        replaceFindField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { performReplaceSearch(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { performReplaceSearch(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { performReplaceSearch(); }
        });
        
        // 查找框回车时查找下一个
        replaceFindField.addActionListener(e -> replaceFindNext());
        
        // 替换框回车时执行替换
        replaceWithField.addActionListener(e -> replaceCurrentMatch());
        
        // ESC 关闭替换面板
        replaceFindField.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "closeReplace");
        replaceFindField.getActionMap().put("closeReplace", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleReplacePanel(); }
        });
        
        replaceWithField.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "closeReplace");
        replaceWithField.getActionMap().put("closeReplace", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { toggleReplacePanel(); }
        });
        
        // 选项变化时重新搜索
        replaceCaseSensitive.addActionListener(e -> performReplaceSearch());
        replaceUseRegex.addActionListener(e -> performReplaceSearch());
        
        replacePanel.setVisible(false); // 默认隐藏
    }
    
    /**
     * 切换替换面板的显示/隐藏
     */
    private void toggleReplacePanel() {
        if (replacePanel.isVisible()) {
            // 隐藏替换面板
            replacePanel.setVisible(false);
            getContentPane().remove(replacePanel);
            clearReplaceHighlights();
            bodyArea.requestFocusInWindow();
            getContentPane().revalidate();
            getContentPane().repaint();
        } else {
            // 隐藏搜索面板（如果显示）
            if (searchPanel.isVisible()) {
                searchPanel.setVisible(false);
                getContentPane().remove(searchPanel);
                clearSearchHighlights();
            }
            
            // 显示替换面板
            getContentPane().add(replacePanel, BorderLayout.NORTH);
            replacePanel.setVisible(true);
            
            // 如果有选中的文本，自动填充到查找框
            String selectedText = bodyArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty() && !selectedText.contains("\n")) {
                replaceFindField.setText(selectedText);
            }
            
            replaceFindField.requestFocusInWindow();
            replaceFindField.selectAll();
            getContentPane().revalidate();
            getContentPane().repaint();
        }
    }
    
    /**
     * 执行替换搜索
     */
    private void performReplaceSearch() {
        String keyword = replaceFindField.getText();
        clearReplaceHighlights();
        replaceMatchPositions.clear();
        replaceMatchLengths.clear();
        currentReplaceIndex = -1;
        
        if (keyword.isEmpty()) {
            replaceResultLabel.setText("");
            return;
        }
        
        try {
            if (replaceUseRegex.isSelected()) {
                // 正则表达式搜索
                java.util.regex.Pattern pattern;
                if (replaceCaseSensitive.isSelected()) {
                    pattern = java.util.regex.Pattern.compile(keyword);
                } else {
                    pattern = java.util.regex.Pattern.compile(keyword, java.util.regex.Pattern.CASE_INSENSITIVE);
                }
                java.util.regex.Matcher matcher = pattern.matcher(bodyArea.getText());
                
                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    replaceMatchPositions.add(start);
                    replaceMatchLengths.add(end - start);
                    
                    Object tag = bodyArea.getHighlighter().addHighlight(
                        start, end, searchHighlightPainter);
                    replaceHighlightTags.add(tag);
                }
            } else {
                // 普通文本搜索
                String text = bodyArea.getText();
                String searchText = keyword;
                
                if (!replaceCaseSensitive.isSelected()) {
                    text = text.toLowerCase();
                    searchText = searchText.toLowerCase();
                }
                
                int pos = 0;
                while ((pos = text.indexOf(searchText, pos)) >= 0) {
                    replaceMatchPositions.add(pos);
                    replaceMatchLengths.add(keyword.length());
                    
                    Object tag = bodyArea.getHighlighter().addHighlight(
                        pos, pos + keyword.length(), searchHighlightPainter);
                    replaceHighlightTags.add(tag);
                    
                    pos += searchText.length();
                }
            }
            
            if (replaceMatchPositions.isEmpty()) {
                replaceResultLabel.setText("未找到");
            } else {
                currentReplaceIndex = 0;
                highlightCurrentReplaceMatch();
                replaceResultLabel.setText((currentReplaceIndex + 1) + "/" + replaceMatchPositions.size());
            }
        } catch (Exception e) {
            replaceResultLabel.setText("搜索错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 查找下一个匹配项
     */
    private void replaceFindNext() {
        if (replaceMatchPositions.isEmpty()) return;
        
        currentReplaceIndex = (currentReplaceIndex + 1) % replaceMatchPositions.size();
        highlightCurrentReplaceMatch();
        replaceResultLabel.setText((currentReplaceIndex + 1) + "/" + replaceMatchPositions.size());
    }
    
    /**
     * 查找上一个匹配项
     */
    private void replaceFindPrevious() {
        if (replaceMatchPositions.isEmpty()) return;
        
        currentReplaceIndex = (currentReplaceIndex - 1 + replaceMatchPositions.size()) % replaceMatchPositions.size();
        highlightCurrentReplaceMatch();
        replaceResultLabel.setText((currentReplaceIndex + 1) + "/" + replaceMatchPositions.size());
    }
    
    /**
     * 高亮显示当前替换匹配项
     */
    private void highlightCurrentReplaceMatch() {
        if (currentReplaceIndex < 0 || currentReplaceIndex >= replaceMatchPositions.size()) return;
        
        int pos = replaceMatchPositions.get(currentReplaceIndex);
        int length = replaceMatchLengths.get(currentReplaceIndex);
        
        bodyArea.setSelectionStart(pos);
        bodyArea.setSelectionEnd(pos + length);
        bodyArea.setCaretPosition(pos + length);
        
        try {
            bodyArea.scrollRectToVisible(bodyArea.modelToView2D(pos).getBounds());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 替换当前匹配项
     */
    private void replaceCurrentMatch() {
        if (replaceMatchPositions.isEmpty() || currentReplaceIndex < 0 || currentReplaceIndex >= replaceMatchPositions.size()) {
            return;
        }
        
        String replaceText = replaceWithField.getText();
        int pos = replaceMatchPositions.get(currentReplaceIndex);
        int length = replaceMatchLengths.get(currentReplaceIndex);
        
        try {
            // 执行替换
            bodyArea.replaceRange(replaceText, pos, pos + length);
            
            // 重新搜索（替换后位置会变化）
            performReplaceSearch();
            
            // 如果还有匹配项，移到下一个
            if (!replaceMatchPositions.isEmpty()) {
                if (currentReplaceIndex >= replaceMatchPositions.size()) {
                    currentReplaceIndex = replaceMatchPositions.size() - 1;
                }
                highlightCurrentReplaceMatch();
                replaceResultLabel.setText((currentReplaceIndex + 1) + "/" + replaceMatchPositions.size());
            }
        } catch (Exception e) {
            replaceResultLabel.setText("替换错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 批量替换所有匹配项
     */
    private void replaceAllMatches() {
        if (replaceMatchPositions.isEmpty()) {
            return;
        }
        
        String keyword = replaceFindField.getText();
        String replaceText = replaceWithField.getText();
        
        if (keyword.isEmpty()) {
            return;
        }
        
        // 确认对话框
        int count = replaceMatchPositions.size();
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "确定要替换全部 " + count + " 处匹配项吗？",
            "确认批量替换",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        try {
            String text = bodyArea.getText();
            String newText;
            
            if (replaceUseRegex.isSelected()) {
                // 正则表达式替换
                java.util.regex.Pattern pattern;
                if (replaceCaseSensitive.isSelected()) {
                    pattern = java.util.regex.Pattern.compile(keyword);
                } else {
                    pattern = java.util.regex.Pattern.compile(keyword, java.util.regex.Pattern.CASE_INSENSITIVE);
                }
                newText = pattern.matcher(text).replaceAll(replaceText);
            } else {
                // 普通文本替换
                if (replaceCaseSensitive.isSelected()) {
                    newText = text.replace(keyword, replaceText);
                } else {
                    // 不区分大小写的替换
                    newText = text.replaceAll("(?i)" + java.util.regex.Pattern.quote(keyword), 
                        java.util.regex.Matcher.quoteReplacement(replaceText));
                }
            }
            
            bodyArea.setText(newText);
            
            // 显示结果
            replaceResultLabel.setText("已替换 " + count + " 处");
            
            // 重新搜索以更新状态
            performReplaceSearch();
            
        } catch (Exception e) {
            replaceResultLabel.setText("批量替换错误: " + e.getMessage());
            JOptionPane.showMessageDialog(
                this,
                "批量替换时发生错误: " + e.getMessage(),
                "错误",
                JOptionPane.ERROR_MESSAGE
            );
            e.printStackTrace();
        }
    }
    
    /**
     * 清除所有替换高亮
     */
    private void clearReplaceHighlights() {
        for (Object tag : replaceHighlightTags) {
            bodyArea.getHighlighter().removeHighlight(tag);
        }
        replaceHighlightTags.clear();
    }

    // ===== 选区悬浮工具条 =====
    private void initSelectionToolbar(){
        if (selectionToolbarInitialized) return;
        selectionToolbar = new JPopupMenu();
        // 美化边框：使用圆角和阴影效果
        selectionToolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIColors.BORDER_LIGHT, 1),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        selectionToolbar.setBackground(Color.WHITE);
        // 关键：工具条不抢焦点，避免打断 Shift+方向键的连续选择
        selectionToolbar.setFocusable(false);

        addSelItem(selectionToolbar, "复制", () -> bodyArea.copy());
        selectionToolbar.addSeparator();
        addSelItem(selectionToolbar, "加粗", () -> wrapSelection("**", "**"));
        addSelItem(selectionToolbar, "斜体", () -> wrapSelection("*", "*"));
        addSelItem(selectionToolbar, "删除线", () -> wrapSelection("~~", "~~"));
        selectionToolbar.addSeparator();
        addSelItem(selectionToolbar, "H1", () -> prefixLineSelection("# "));
        addSelItem(selectionToolbar, "H2", () -> prefixLineSelection("## "));
        addSelItem(selectionToolbar, "H3", () -> prefixLineSelection("### "));
        selectionToolbar.addSeparator();
        addSelItem(selectionToolbar, "无序列表", () -> prefixLineSelection("- "));
        addSelItem(selectionToolbar, "有序列表", () -> prefixLineSelection("1. "));
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
        item.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        item.setForeground(UIColors.TEXT_PRIMARY);
        item.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        item.setOpaque(true);
        item.setBackground(Color.WHITE);
        // 添加悬停效果
        item.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                item.setBackground(UIColors.BG_HOVER);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                item.setBackground(Color.WHITE);
            }
        });
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
    
    /**
     * 将选中文本包裹为代码块
     * 单行：使用行内代码 `code`
     * 多行：使用代码块 ```\ncode\n```
     */
    private void wrapCodeBlock(){
        try{
            int start = bodyArea.getSelectionStart();
            int end = bodyArea.getSelectionEnd();
            if (start == end) return; // 没有选中内容
            
            String selected = bodyArea.getSelectedText();
            
            // 判断是单行还是多行
            if (selected.contains("\n")) {
                // 多行：使用代码块 ```
                String wrapped = "```\n" + selected + "\n```";
                bodyArea.replaceRange(wrapped, start, end);
                // 保持选中代码内容（不包括```标记）
                bodyArea.select(start + 4, start + 4 + selected.length());
            } else {
                // 单行：使用行内代码 `
                String wrapped = "`" + selected + "`";
                bodyArea.replaceRange(wrapped, start, end);
                // 保持选中代码内容（不包括反引号）
                bodyArea.select(start + 1, start + 1 + selected.length());
            }
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

    // ===== 列表缩进/续项 =====
    private static final String INDENT = "  "; // 每层两个空格

    private boolean isListLine(String s){
        // 支持："- ", "+ ", "* ", "1. ", 以及后续带内容的情形
        return s.matches("^\\s{0,100}(([-+*])|(\\d+\\.))(?:\\s*$|\\s+.*$)");
    }

    private void indentSelection(boolean indent){
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
            int deltaFirst = 0; // 第一行光标偏移
            int deltaTotal = 0; // 全部选区长度变化
            boolean hasListLine = false;
            for (int i=0;i<arr.length;i++){
                String l = arr[i];
                if (isListLine(l)){
                    hasListLine = true;
                    if (indent) {
                        l = INDENT + l;
                        deltaTotal += INDENT.length();
                        if (i==0) deltaFirst += INDENT.length();
                    } else {
                        if (l.startsWith(INDENT)) {
                            l = l.substring(INDENT.length());
                            deltaTotal -= INDENT.length();
                            if (i==0) deltaFirst -= INDENT.length();
                        } else {
                            int beforeLen = l.length();
                            l = l.replaceFirst("^\\s{1,2}", "");
                            int removed = beforeLen - l.length();
                            deltaTotal -= removed;
                            if (i==0) deltaFirst -= removed;
                        }
                    }
                }
                sb.append(l);
                if (i < arr.length-1) sb.append('\n');
            }
            if (!hasListLine){
                // 非列表行：保持默认体验，Tab 插入制表符（Shift+Tab 不做任何事）
                if (indent){
                    bodyArea.replaceSelection("\t");
                } // outdent on non-list lines: 忽略，避免破坏普通文本
                return;
            }
            bodyArea.replaceRange(sb.toString(), lineStart, lineEnd);
            if (start != end) {
                bodyArea.select(lineStart, lineStart + sb.length());
            } else {
                bodyArea.setCaretPosition(start + deltaFirst);
            }
        }catch(Exception ignored){}
    }

    private void continueListWithEnter(){
        try{
            int caret = bodyArea.getCaretPosition();
            int lineStart = bodyArea.getText().lastIndexOf('\n', Math.max(0, caret-1)) + 1;
            int lineEnd = bodyArea.getText().indexOf('\n', caret);
            if (lineEnd < 0) lineEnd = bodyArea.getText().length();
            String line = bodyArea.getText().substring(lineStart, lineEnd);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\s*)([-+*]|(\\d+)\\.)\\s*$").matcher(line);
            // 若当前行只有标记与空格，回车则删除标记并换行（退出列表）
            if (m.find()){
                String indent = m.group(1)==null?"":m.group(1);
                bodyArea.replaceRange("\n", lineStart, lineEnd);
                bodyArea.setCaretPosition(lineStart + 1);
                return;
            }
            // 正常续项：复制前导空白与标记
            m = java.util.regex.Pattern.compile("^(\\s*)([-+*]|(\\d+)\\.)\\s+.*$").matcher(line);
            if (m.find()){
                String indent = m.group(1)==null?"":m.group(1);
                String bullet = m.group(2);
                String num = m.group(3);
                String next;
                if (num != null){
                    int n = Integer.parseInt(num);
                    next = indent + (n+1) + ". ";
                } else {
                    next = indent + bullet + " ";
                }
                // 避免触发任何弹出菜单：仅插入文本并恢复焦点
                bodyArea.replaceSelection("\n" + next);
                bodyArea.requestFocusInWindow();
            } else {
                // 非列表行，执行默认换行
                bodyArea.replaceSelection("\n");
                bodyArea.requestFocusInWindow();
            }
        }catch(Exception ignored){}
    }

    // ===== 粘贴处理 =====
    private void installPasteHandlers(JRootPane root){
        // Ctrl+V：拦截并弹出选择（在 bodyArea 上绑定以覆盖默认粘贴）
        KeyStroke ksPaste = KeyStroke.getKeyStroke('V', java.awt.event.InputEvent.CTRL_DOWN_MASK);
        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(ksPaste, "pasteWithChoice");
        bodyArea.getActionMap().put("pasteWithChoice", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ doPasteWithChoice(false); }
        });
        // Ctrl+Shift+V：直接纯文本（同样绑定在 bodyArea）
        KeyStroke ksPastePlain = KeyStroke.getKeyStroke('V', java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK);
        bodyArea.getInputMap(JComponent.WHEN_FOCUSED).put(ksPastePlain, "pastePlain");
        bodyArea.getActionMap().put("pastePlain", new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ doPasteWithChoice(true); }
        });
        // 同时设置 TransferHandler 作为兜底（支持鼠标粘贴）
        originalTransferHandler = bodyArea.getTransferHandler();
        bodyArea.setTransferHandler(new TransferHandler(){
            @Override public void exportToClipboard(JComponent c, java.awt.datatransfer.Clipboard clip, int action){
                if (originalTransferHandler != null) originalTransferHandler.exportToClipboard(c, clip, action);
                else super.exportToClipboard(c, clip, action);
            }
            @Override public int getSourceActions(JComponent c){
                if (originalTransferHandler != null) return originalTransferHandler.getSourceActions(c);
                return COPY_OR_MOVE;
            }
            @Override public boolean importData(JComponent comp, Transferable t){
                try{
                    String plain = t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)
                            ? (String) t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) : null;
                    String html = extractClipboardHtml(t);
                    if (html != null){
                        // 鼠标粘贴：与键盘一致，弹出选择
                        doPasteWithChoice(false);
                        return true;
                    } else if (plain != null){
                        bodyArea.replaceSelection(plain);
                        return true;
                    }
                }catch(Exception ignored){}
                return false;
            }
            @Override public boolean canImport(JComponent comp, java.awt.datatransfer.DataFlavor[] flavors){
                return true;
            }
        });
    }

    private void doPasteWithChoice(boolean forcePlain){
        try{
            // 首先尝试粘贴图片（截图或从文件管理器复制的图片）
            if (!forcePlain && tryPasteImage()) {
                return; // 已成功粘贴图片
            }
            
            java.awt.datatransfer.Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = cb.getContents(null);
            if (t == null){ bodyArea.paste(); return; }
            String html = extractClipboardHtml(t);
            String plain = t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)
                    ? (String) t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) : null;

            if (forcePlain || html == null){
                if (plain != null) bodyArea.replaceSelection(plain);
                else bodyArea.paste();
                return;
            }

            // 弹窗选择
            Object[] options = {"保留样式(转为Markdown)", "仅粘贴纯文本", "保留原始HTML(完整样式)", "取消"};
            int defaultIdx = 0; // 默认“保留样式(转为Markdown)”
            int opt = JOptionPane.showOptionDialog(this, "检测到富文本，如何粘贴？", "粘贴选项",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[defaultIdx]);
            if (opt == 3 || opt == JOptionPane.CLOSED_OPTION) return; // 取消
            if (opt == 1){ // 纯文本
                if (plain != null) bodyArea.replaceSelection(plain); else bodyArea.paste();
                return;
            }
            if (opt == 2){ // 原始HTML（保留完整样式）
                // 将 HTML 包裹为 <span style> 等内联内容不一定合理，这里直接降级为 Markdown 中的行内 HTML
                bodyArea.replaceSelection(html);
                return;
            }
            // 默认：HTML->Markdown
            doPasteFromHtml(html, true);
        }catch(Exception ex){ bodyArea.paste(); }
    }

    private boolean looksLikeMarkdown(String s){
        if (s == null) return false;
        String sample = s.length() > 4000 ? s.substring(0, 4000) : s;
        int nl = 0; for (int i=0;i<sample.length();i++){ if (sample.charAt(i)=='\n') nl++; }
        int score = 0;
        if (sample.contains("```")) score += 2;
        if (sample.matches("(?s).*(^|\n)#{1,6}\\s+.*")) score += 2;
        if (sample.matches("(?s).*(^|\n)(-|\\*|\\+)\\s+.*")) score += 1;
        if (sample.matches("(?s).*(^|\n)\\d+\\.\\s+.*")) score += 1;
        if (sample.contains("**") || sample.contains("_")) score += 1;
        if (sample.contains("|")) score += 1; // 可能是表格
        if (nl >= 2) score += 1; // 多行文本
        return score >= 3;
    }

    private boolean doPasteFromHtml(String html, boolean fromDialog){
        try{
            // HTML -> Markdown
            String md = FlexmarkHtmlConverter.builder().build().convert(html);
            // 简单规整：将 <img> 标签转换为 Markdown（html2md 已处理大多数情况）
            md = md.replaceAll("!\\[\\]\\((data:[^)]+)\\)", ""); // 丢弃 data URI 大图
            bodyArea.replaceSelection(md);
            return true;
        }catch(Exception ex){
            if (fromDialog){ JOptionPane.showMessageDialog(this, "转换失败，已退回纯文本", "提示", JOptionPane.WARNING_MESSAGE); }
            try{
                bodyArea.paste();
            }catch(Exception ignored){}
            return false;
        }
    }

    private static String readAll(Reader r) throws java.io.IOException{
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while((n = r.read(buf)) != -1){ sb.append(buf, 0, n); }
        return sb.toString();
    }

    private String extractClipboardHtml(Transferable t){
        try{
            for (java.awt.datatransfer.DataFlavor f : t.getTransferDataFlavors()){
                String mime = f.getMimeType();
                if (mime == null) continue;
                String base = mime.split(";",2)[0].trim().toLowerCase();
                if (!"text/html".equals(base)) continue;
                Object data = t.getTransferData(f);
                if (data == null) continue;
                if (data instanceof String) return (String) data;
                if (data instanceof Reader) return readAll((Reader) data);
                if (data instanceof InputStream) return readAll(new InputStreamReader((InputStream) data, StandardCharsets.UTF_8));
                // 其它类型不支持，继续尝试下一个 flavor
            }
        }catch(Exception ignored){}
        return null;
    }

    // ==================== 图片粘贴功能 ====================

    /**
     * 设置图片粘贴处理器
     * 支持：截图粘贴、从文件管理器复制图片文件粘贴
     */
    private void setupImagePasteHandler() {
        // 保存原来的 TransferHandler，用于处理非图片粘贴
        final TransferHandler originalHandler = bodyArea.getTransferHandler();
        
        bodyArea.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                // 支持图片和文件
                if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    return true;
                }
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return true;
                }
                // 其他类型交给原处理器
                return originalHandler != null && originalHandler.canImport(support);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    Transferable t = support.getTransferable();
                    
                    // 尝试处理图片数据（截图）
                    if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        BufferedImage image = (BufferedImage) t.getTransferData(DataFlavor.imageFlavor);
                        if (image != null) {
                            return handleImagePaste(image, "png");
                        }
                    }
                    
                    // 尝试处理文件列表（从文件管理器复制的图片）
                    if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        java.util.List<File> files = (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        for (File file : files) {
                            if (isImageFile(file)) {
                                return handleImageFilePaste(file);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[ImagePaste] 粘贴失败: " + e.getMessage());
                }
                
                // 非图片粘贴，交给原处理器
                return originalHandler != null && originalHandler.importData(support);
            }
        });
        
        // 注意：Ctrl+V 快捷键由 installPasteHandlers 处理
        // 图片粘贴逻辑已整合到 doPasteWithChoice 方法中
    }

    /**
     * 尝试从剪贴板粘贴图片
     * @return true 如果成功粘贴了图片，false 如果剪贴板中不是图片
     */
    private boolean tryPasteImage() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = clipboard.getContents(null);
            
            if (t == null) return false;
            
            // 尝试获取图片数据（截图）
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                BufferedImage image = (BufferedImage) t.getTransferData(DataFlavor.imageFlavor);
                if (image != null) {
                    return handleImagePaste(image, "png");
                }
            }
            
            // 尝试获取文件列表（从文件管理器复制的图片）
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                java.util.List<File> files = (java.util.List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files) {
                    if (isImageFile(file)) {
                        return handleImageFilePaste(file);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ImagePaste] 检测剪贴板失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 处理图片数据粘贴（截图）
     */
    private boolean handleImagePaste(BufferedImage image, String format) {
        try {
            // 确保笔记已保存（需要 folderPath）
            if (current == null || current.folderPath == null || current.folderPath.isEmpty()) {
                // 先提示用户保存笔记
                int opt = JOptionPane.showConfirmDialog(this, 
                    "粘贴图片前需要先保存笔记。\n是否立即保存？", 
                    "保存笔记", JOptionPane.YES_NO_OPTION);
                if (opt != JOptionPane.YES_OPTION) {
                    return false;
                }
                saveUnified(true);
                // 保存后再次检查
                if (current == null || current.folderPath == null || current.folderPath.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "保存失败，无法粘贴图片", "错误", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            
            // 创建 assets 目录
            Path assetsDir = Paths.get(current.folderPath, "assets");
            if (!Files.exists(assetsDir)) {
                Files.createDirectories(assetsDir);
            }
            
            // 生成唯一文件名
            String fileName = "img_" + System.currentTimeMillis() + "." + format;
            Path imagePath = assetsDir.resolve(fileName);
            
            // 保存图片
            ImageIO.write(image, format, imagePath.toFile());
            System.out.println("[ImagePaste] 图片已保存: " + imagePath);
            
            // 在光标位置插入 Markdown 图片引用
            String markdownRef = "![](assets/" + fileName + ")";
            bodyArea.replaceSelection(markdownRef);
            
            statusLeft.setText("已粘贴图片: " + fileName);
            return true;
            
        } catch (Exception e) {
            System.err.println("[ImagePaste] 保存图片失败: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "保存图片失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 处理图片文件粘贴（从文件管理器复制）
     */
    private boolean handleImageFilePaste(File sourceFile) {
        try {
            // 确保笔记已保存
            if (current == null || current.folderPath == null || current.folderPath.isEmpty()) {
                int opt = JOptionPane.showConfirmDialog(this, 
                    "粘贴图片前需要先保存笔记。\n是否立即保存？", 
                    "保存笔记", JOptionPane.YES_NO_OPTION);
                if (opt != JOptionPane.YES_OPTION) {
                    return false;
                }
                saveUnified(true);
                if (current == null || current.folderPath == null || current.folderPath.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "保存失败，无法粘贴图片", "错误", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            
            // 创建 assets 目录
            Path assetsDir = Paths.get(current.folderPath, "assets");
            if (!Files.exists(assetsDir)) {
                Files.createDirectories(assetsDir);
            }
            
            // 获取文件扩展名
            String originalName = sourceFile.getName();
            String ext = originalName.contains(".") ? 
                originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase() : "png";
            
            // 生成唯一文件名（保留原始文件名作为参考）
            String baseName = originalName.contains(".") ? 
                originalName.substring(0, originalName.lastIndexOf(".")) : originalName;
            // 清理文件名中的特殊字符
            baseName = baseName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String fileName = baseName + "_" + System.currentTimeMillis() + "." + ext;
            Path targetPath = assetsDir.resolve(fileName);
            
            // 复制文件
            Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[ImagePaste] 图片已复制: " + targetPath);
            
            // 在光标位置插入 Markdown 图片引用
            String markdownRef = "![](assets/" + fileName + ")";
            bodyArea.replaceSelection(markdownRef);
            
            statusLeft.setText("已粘贴图片: " + fileName);
            return true;
            
        } catch (Exception e) {
            System.err.println("[ImagePaste] 复制图片失败: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "复制图片失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 判断文件是否为图片
     */
    private boolean isImageFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
               name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp");
    }

    /**
     * 获取当前笔记的文件夹路径
     * 用于预览时将相对路径转换为绝对路径
     */
    private String getCurrentNoteFolderPath() {
        if (current != null && current.folderPath != null && !current.folderPath.isEmpty()) {
            return current.folderPath;
        }
        return null;
    }

    /**
     * 将 HTML 中的相对图片路径转换为绝对路径
     * 这样 JEditorPane 才能正确加载本地图片
     */
    private String convertImagePathsToAbsolute(String html) {
        String folderPath = getCurrentNoteFolderPath();
        if (folderPath == null || html == null) {
            return html;
        }
        
        try {
            // 将 Windows 路径转换为 file:// URI 格式
            Path folder = Paths.get(folderPath);
            String fileUri = folder.toUri().toString();
            // 确保 URI 以 / 结尾
            if (!fileUri.endsWith("/")) {
                fileUri = fileUri + "/";
            }
            
            // 替换相对路径 src="assets/xxx" 为绝对路径
            // 匹配 <img src="assets/..."> 或 <img src='assets/...'>
            html = html.replaceAll(
                "(<img[^>]*\\ssrc=[\"'])assets/([^\"']+)([\"'][^>]*>)",
                "$1" + fileUri + "assets/$2$3"
            );
            
            // 也处理不带引号的情况（虽然不标准，但以防万一）
            html = html.replaceAll(
                "(<img[^>]*\\ssrc=)assets/([^\\s>]+)",
                "$1" + fileUri + "assets/$2"
            );
            
            System.out.println("[预览] 图片路径已转换，基础路径: " + fileUri);
            
        } catch (Exception e) {
            System.err.println("[预览] 图片路径转换失败: " + e.getMessage());
        }
        
        return html;
    }
    
    /**
     * 打开设置对话框
     */
    private void openSettings() {
        SettingsDialog settingsDialog = new SettingsDialog(this);
        settingsDialog.setVisible(true);
    }
    
    /**
     * 应用窗口透明度配置
     */
    private void applyWindowOpacity() {
        try {
            AppConfig config = AppConfig.getInstance();
            int opacityPercent = config.getWindowOpacity();
            float opacity = opacityPercent / 100.0f;
            setOpacity(opacity);
            logger.info("窗口透明度已设置为: {}%", opacityPercent);
        } catch (Exception e) {
            logger.error("设置窗口透明度失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 更新窗口透明度（供设置面板实时预览使用）
     */
    public void updateOpacity(float opacity) {
        try {
            setOpacity(opacity);
        } catch (Exception e) {
            logger.error("更新窗口透明度失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 绘制快捷键指南（编辑区为空时显示）- 2x2网格布局
     */
    private void drawShortcutGuide(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = bodyArea.getWidth();
        int height = bodyArea.getHeight();
        
        // 标题 - 简化为"FastPig（迅猪）"
        g2.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 36));
        String title = "FastPig（迅猪）";
        FontMetrics titleFm = g2.getFontMetrics();
        int titleX = (width - titleFm.stringWidth(title)) / 2;
        int titleY = 70;
        
        // 标题阴影
        g2.setColor(new Color(64, 158, 255, 40));
        g2.drawString(title, titleX + 2, titleY + 2);
        
        // 标题文字 - 使用渐变效果的蓝色
        g2.setColor(new Color(54, 142, 230));
        g2.drawString(title, titleX, titleY);
        
        // 获取快捷键数据
        java.util.List<ShortcutData.ShortcutCategory> categories = ShortcutData.getCategories();
        
        // 固定 2x2 网格布局
        int cols = 2;
        int rows = 2;
        int gapX = 40; // 水平间距增大
        int gapY = 30; // 垂直间距增大
        int margin = 80; // 左右边距增大
        
        int cardWidth = (width - margin * 2 - gapX) / cols;
        int cardHeight = 230; // 固定高度增大
        
        int startX = margin;
        int startY = titleY + 60;
        
        // 绘制4个核心分类卡片
        for (int i = 0; i < Math.min(4, categories.size()); i++) {
            int col = i % 2;
            int row = i / 2;
            int x = startX + col * (cardWidth + gapX);
            int y = startY + row * (cardHeight + gapY);
            
            drawCategoryWithIcon(g2, x, y, cardWidth, cardHeight, categories.get(i));
        }
        
        // 底部提示 - 更醒目的样式
        if (height > 600) {
            int bottomY = startY + rows * cardHeight + (rows - 1) * gapY + 40;
            
            g2.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
            g2.setColor(new Color(130, 135, 140));
            String hint = "按 Ctrl+, 查看所有快捷键设置";
            FontMetrics hintFm = g2.getFontMetrics();
            int hintX = (width - hintFm.stringWidth(hint)) / 2;
            g2.drawString(hint, hintX, bottomY);
        }
        
        g2.dispose();
    }
    
    /**
     * 绘制带彩色图标的分类卡片
     */
    private void drawCategoryWithIcon(Graphics2D g2, int x, int y, int width, int height, 
                                      ShortcutData.ShortcutCategory category) {
        // 绘制卡片背景
        drawCategoryCard(g2, x, y, width, height);
        
        // 绘制分类标题（不带图标）
        g2.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 16));
        g2.setColor(UIColors.TEXT_PRIMARY);
        g2.drawString(category.name, x + 20, y + 30);
        
        // 绘制分割线
        g2.setColor(new Color(225, 230, 235));
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.drawLine(x + 20, y + 48, x + width - 20, y + 48);
        g2.setStroke(new java.awt.BasicStroke(1.0f));
        
        // 绘制快捷键列表
        int listY = y + 75;
        for (ShortcutData.Shortcut shortcut : category.shortcuts) {
            // 绘制快捷键徽章
            drawKeyBadge(g2, x + 20, listY - 14, shortcut.keys);
            
            // 绘制描述
            g2.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
            g2.setColor(new Color(85, 90, 95));
            int descX = x + 155;
            g2.drawString(shortcut.description, descX, listY);
            
            listY += 26;
        }
    }
    
    /**
     * 绘制分类卡片背景
     */
    private void drawCategoryCard(Graphics2D g2, int x, int y, int width, int height) {
        // 多层阴影效果
        g2.setColor(new Color(0, 0, 0, 6));
        g2.fillRoundRect(x + 4, y + 4, width, height, 14, 14);
        g2.setColor(new Color(0, 0, 0, 10));
        g2.fillRoundRect(x + 2, y + 2, width, height, 14, 14);
        
        // 卡片背景 - 白色带30%透明度（alpha = 255 * 0.7 = 178）
        g2.setColor(new Color(255, 255, 255, 178));
        g2.fillRoundRect(x, y, width, height, 14, 14);
        
        // 卡片边框 - 更细腻的边框
        g2.setColor(new Color(220, 225, 230));
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        g2.drawRoundRect(x, y, width, height, 14, 14);
        g2.setStroke(new java.awt.BasicStroke(1.0f));
    }
    
    /**
     * 绘制快捷键徽章
     */
    private void drawKeyBadge(Graphics2D g2, int x, int y, String keys) {
        // 使用更大的字体
        g2.setFont(new Font("Consolas", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        int keyWidth = fm.stringWidth(keys);
        int keyHeight = fm.getHeight();
        
        // 内边距
        int badgeWidth = keyWidth + 18;
        int badgeHeight = keyHeight + 7;
        
        // 徽章阴影
        g2.setColor(new Color(0, 0, 0, 18));
        g2.fillRoundRect(x + 2, y + 2, badgeWidth, badgeHeight, 5, 5);
        
        // 徽章背景 - 渐变效果（模拟）
        GradientPaint gradient = new GradientPaint(
            x, y, new Color(250, 251, 252),
            x, y + badgeHeight, new Color(242, 244, 247)
        );
        g2.setPaint(gradient);
        g2.fillRoundRect(x, y, badgeWidth, badgeHeight, 5, 5);
        
        // 徽章边框 - 双层边框效果
        g2.setColor(new Color(200, 210, 220));
        g2.setStroke(new java.awt.BasicStroke(1.3f));
        g2.drawRoundRect(x, y, badgeWidth, badgeHeight, 5, 5);
        g2.setStroke(new java.awt.BasicStroke(1.0f));
        
        // 徽章文字 - 深蓝色
        g2.setColor(new Color(45, 125, 210));
        int textX = x + 9;
        int textY = y + badgeHeight - 8;
        g2.drawString(keys, textX, textY);
    }
}



