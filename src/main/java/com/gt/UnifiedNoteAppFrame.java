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

    private final JTextField queryField = new JTextField();
    private final JPopupMenu suggestPopup = new JPopupMenu();
    private final DefaultListModel<String> suggestModel = new DefaultListModel<>();
    private final JList<String> suggestList = new JList<>(suggestModel);
    private int suggestSelectedIndex = -1;
    // 去除左侧结果列表，以输入联想替代

    private final JTextField titleField = new JTextField();
    // 去除标签编辑
    private final JTextArea bodyArea = new JTextArea();

    private NoteDto current;

    public UnifiedNoteAppFrame(NoteRepository repository) {
        super("迅猪 - 统一界面（搜索/展示/编辑/保存）");
        this.repository = repository;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);

        // 顶部：搜索条
        JButton searchBtn = new JButton(new AbstractAction("搜索") {
            @Override public void actionPerformed(ActionEvent e) { doSearch(); }
        });
        queryField.addActionListener(e -> doSearch());
        // 顶部条将在稍后与“描述”合并为一行

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
        queryField.addKeyListener(new java.awt.event.KeyAdapter(){
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

        // 顶部一行：快捷命令 + 描述 + 搜索按钮
        JPanel fieldsRow = new JPanel(new GridLayout(1, 2, 8, 0));
        JPanel cmdPane = new JPanel(new BorderLayout());
        cmdPane.add(new JLabel("快捷命令:"), BorderLayout.WEST);
        cmdPane.add(queryField, BorderLayout.CENTER);
        JPanel descPane = new JPanel(new BorderLayout());
        descPane.add(new JLabel("描述:"), BorderLayout.WEST);
        descPane.add(titleField, BorderLayout.CENTER);
        fieldsRow.add(cmdPane);
        fieldsRow.add(descPane);
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.add(fieldsRow, BorderLayout.CENTER);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.add(searchBtn);
        previewBtnRef = new JButton(new AbstractAction("预览") {
            @Override public void actionPerformed(ActionEvent e) { toggleInAppPreview(); }
        });
        rightButtons.add(previewBtnRef);
        topBar.add(rightButtons, BorderLayout.EAST);

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
        add(topBar, BorderLayout.NORTH);
        add(editor, BorderLayout.CENTER);
        centerComponent = editor;

        // 绑定全局快捷键：Ctrl+S -> 保存
        JRootPane root = getRootPane();
        KeyStroke saveKs = KeyStroke.getKeyStroke("control S");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(saveKs, "saveUnified");
        root.getActionMap().put("saveUnified", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { saveUnified(); }
        });
    }

    private boolean previewVisible = false;
    private JSplitPane previewSplit;
    private JEditorPane htmlPane;
    private javax.swing.Timer previewTimer;
    private JScrollPane bodyScrollPane;
    private JPanel actionsPanel;
    private Component centerComponent;
    private JButton previewBtnRef;

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
            previewBtnRef.setText("收起预览");
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
            previewBtnRef.setText("预览");
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
        String q = queryField.getText()==null? "": queryField.getText().trim();
        if (q.isEmpty()) { suggestPopup.setVisible(false); return; }
        List<NoteDto> list = repository.searchByKeyOrText(q, 20);
        suggestModel.clear();
        for (NoteDto n : list){
            String key = (n.key!=null?n.key:"");
            String desc = (n.desc!=null?n.desc: n.title!=null?n.title:"");
            suggestModel.addElement(key + (key.isEmpty()? "": ": ") + desc);
        }
        if (suggestModel.size()>0){
            try{
                Rectangle r = queryField.getBounds();
                suggestPopup.show(queryField, 0, r.height);
                javax.swing.SwingUtilities.invokeLater(() -> queryField.requestFocusInWindow());
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
        String key = s.contains(":")? s.substring(0, s.indexOf(":")) : s;
        queryField.setText(key);
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
        // 将快捷命令显示到顶部输入框
        queryField.setText(n.key==null? "": n.key);
        // 描述优先显示 desc，不存在则回退 title
        titleField.setText(n.desc!=null? n.desc : (n.title==null? "": n.title));
        // 已去除标签显示
        bodyArea.setText(n.bodyMd==null? "": n.bodyMd);
    }

    private void doSearch() {
        String q = queryField.getText()==null? "": queryField.getText().trim();
        updateSuggestions();
        List<NoteDto> list = repository.searchByKeyOrText(q, 1);
        if (!list.isEmpty()) {
            loadNote(list.get(0));
        }
    }

    private void clearEditor() {
        current = null;
        queryField.setText("");
        titleField.setText("");
        bodyArea.setText("");
        queryField.requestFocus();
    }

    private void saveNew() {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = trimOrNull(queryField.getText());
        n.desc = orDefault(titleField.getText(), "");
        n.title = n.desc; // 保持标题=描述
        n.tags = new java.util.ArrayList<>();
        n.bodyMd = bodyArea.getText();
        n.frontMatter = null;
        long now = System.currentTimeMillis();
        n.createdAt = now;
        n.updatedAt = now;
        n.version = 1;
        repository.save(n);
        JOptionPane.showMessageDialog(this, "已保存为新", "提示", JOptionPane.INFORMATION_MESSAGE);
        doSearch();
    }

    private void updateCurrent() {
        if (current == null) {
            // 等价保存为新
            saveNew();
            return;
        }
        current.key = trimOrNull(queryField.getText());
        current.desc = orDefault(titleField.getText(), "");
        current.title = current.desc; // 保持标题=描述
        current.tags = new java.util.ArrayList<>();
        current.bodyMd = bodyArea.getText();
        current.updatedAt = System.currentTimeMillis();
        current.version = Math.max(1, current.version + 1);
        repository.save(current);
        JOptionPane.showMessageDialog(this, "已更新", "提示", JOptionPane.INFORMATION_MESSAGE);
        doSearch();
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
            clearEditor();
            doSearch();
        }
    }

    private static String orDefault(String s, String d) { return (s==null||s.trim().isEmpty())?d:s; }
    private static String trimOrNull(String s) { return (s==null)?null:(s.trim().isEmpty()?null:s.trim()); }
    // 已去除标签编辑，保留空实现
}



