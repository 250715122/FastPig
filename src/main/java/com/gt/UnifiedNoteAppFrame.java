package com.gt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.io.File;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

public class UnifiedNoteAppFrame extends JFrame {
    private final NoteRepository repository;

    // 首行承载“快捷命令 空格 描述”，不再使用独立的输入框
    private final JPopupMenu suggestPopup = new JPopupMenu();
    private final DefaultListModel<String> suggestModel = new DefaultListModel<>();
    private final JList<String> suggestList = new JList<>(suggestModel);
    private int suggestSelectedIndex = -1;
    // 去除左侧结果列表，以输入联想替代

    // 去除标签与独立标题编辑，仅保留正文编辑区
    private final JTextArea bodyArea = new JTextArea();

    private NoteDto current;

    public UnifiedNoteAppFrame(NoteRepository repository) {
        super("迅猪 - 统一界面（搜索/展示/编辑/保存）");
        this.repository = repository;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);

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
        bodyArea.setLineWrap(true);
        bodyScrollPane = new JScrollPane(bodyArea);

        JButton saveUnifiedBtn = new JButton(new AbstractAction("保存") {
            @Override public void actionPerformed(ActionEvent e) { saveUnified(); }
        });
        JButton deleteBtn = new JButton(new AbstractAction("删除(软)") {
            @Override public void actionPerformed(ActionEvent e) { deleteCurrent(); }
        });

        actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionsPanel.add(saveUnifiedBtn);
        actionsPanel.add(deleteBtn);

        JPanel editor = new JPanel(new BorderLayout(8, 8));
        editor.add(bodyScrollPane, BorderLayout.CENTER);
        editor.add(actionsPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout(8, 8));
        add(editor, BorderLayout.CENTER);
        centerComponent = editor;
        ACTIVE = this;

        // 绑定全局快捷键：Ctrl+S -> 保存
        JRootPane root = getRootPane();
        KeyStroke saveKs = KeyStroke.getKeyStroke("control S");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(saveKs, "saveUnified");
        root.getActionMap().put("saveUnified", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveUnified(); }
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
                return false;
            }
        });
    }

    private boolean previewVisible = false;
    private JSplitPane previewSplit;
    private JEditorPane htmlPane;
    private javax.swing.Timer previewTimer;
    private JScrollPane bodyScrollPane;
    private JPanel actionsPanel;
    private Component centerComponent;
    // 预览按钮已移除，保留占位避免大范围改动
    // private JButton previewBtnRef;
    // 保持最近激活实例，便于全局热键调用
    private static volatile UnifiedNoteAppFrame ACTIVE;
    public static UnifiedNoteAppFrame getActiveInstance() { return ACTIVE; }

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
            editor2.add(actionsPanel, BorderLayout.SOUTH);
            centerComponent = editor2;
            getContentPane().add(centerComponent, BorderLayout.CENTER);
            revalidate();
            repaint();
            previewVisible = false;
        }
    }

    private void refreshInAppPreview() {
        String md = bodyArea.getText();
        // 将所有 LaTeX 片段替换为内联图片占位
        String mdWithImgs = replaceAllLatexWithImages(md);
        String html = renderMarkdown(mdWithImgs);
        htmlPane.setText("<html><head><meta charset='utf-8'></head><body style='font-family:Segoe UI;line-height:1.6;white-space:pre-wrap;'>" + html + "</body></html>");
        htmlPane.setCaretPosition(0);
    }

    private String renderMarkdown(String md) {
        MutableDataSet opts = new MutableDataSet();
        // 关键：将软换行渲染为 <br/>，避免 JEditorPane 折叠换行
        opts.set(HtmlRenderer.SOFT_BREAK, "<br/>");
        Parser parser = Parser.builder(opts).build();
        HtmlRenderer renderer = HtmlRenderer.builder(opts).build();
        Node doc = parser.parse(md == null ? "" : md);
        return renderer.render(doc);
    }

    private String replaceAllLatexWithImages(String text) {
        if (text == null) return "";
        String out = text;
        out = replaceByRegex(out, "\\$\\$(.+?)\\$\\$", true);
        out = replaceByRegex(out, "\\\\\\[(.+?)\\\\\\]", true);
        out = replaceByRegex(out, "\\\\\\((.+?)\\\\\\)", false);
        return out;
    }

    private BufferedImage renderLatexToImage(String latex) {
        TeXFormula formula = new TeXFormula(latex);
        TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20f);
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(new Color(0,0,0,0));
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2.setColor(Color.BLACK);
        icon.paintIcon(new JLabel(), g2, 0, 0);
        g2.dispose();
        return image;
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
    }

    private void saveNew() {
        String[] parsed = splitFirstLineAndBody(bodyArea.getText());
        if (parsed[1].isEmpty()) { JOptionPane.showMessageDialog(this, "首行需包含快捷命令", "校验", JOptionPane.WARNING_MESSAGE); return; }
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
        JOptionPane.showMessageDialog(this, "已保存为新", "提示", JOptionPane.INFORMATION_MESSAGE);
        loadNote(n);
    }

    private void updateCurrent() {
        if (current == null) {
            // 等价保存为新
            saveNew();
            return;
        }
        String[] parsed = splitFirstLineAndBody(bodyArea.getText());
        if (parsed[1].isEmpty()) { JOptionPane.showMessageDialog(this, "首行需包含快捷命令", "校验", JOptionPane.WARNING_MESSAGE); return; }
        String newKey = parsed[1];
        // 如果快捷命令已改变，则按“保存为新”处理；否则更新当前
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
            JOptionPane.showMessageDialog(this, "已保存为新（快捷命令已变更）", "提示", JOptionPane.INFORMATION_MESSAGE);
            loadNote(n);
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
        JOptionPane.showMessageDialog(this, "已更新", "提示", JOptionPane.INFORMATION_MESSAGE);
        loadNote(current);
    }

    private void saveUnified() {
        if (current == null) {
            saveNew();
        } else {
            updateCurrent();
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
            clearEditor(); // 删除后不再自动加载下一条，保持界面空白
        }
    }

    private static String orDefault(String s, String d) { return (s==null||s.trim().isEmpty())?d:s; }
    private static String trimOrNull(String s) { return (s==null)?null:(s.trim().isEmpty()?null:s.trim()); }

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
}



