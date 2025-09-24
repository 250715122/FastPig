package com.gt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class NoteEditorFrame extends JFrame {
    private final JTextField keyField = new JTextField();
    private final JTextField titleField = new JTextField();
    private final JTextField tagsField = new JTextField();
    private final JEditorPane previewPane = new JEditorPane();
    private final JTextArea editorArea = new JTextArea();
    private final NoteRepository repository;

    private final Parser mdParser;
    private final HtmlRenderer mdRenderer;

    public NoteEditorFrame(NoteRepository repository) {
        super("迅猪 - 编辑/预览/保存");
        this.repository = repository;

        MutableDataSet options = new MutableDataSet();
        mdParser = Parser.builder(options).build();
        mdRenderer = HtmlRenderer.builder(options).build();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        JPanel meta = new JPanel(new GridLayout(1, 3, 8, 0));
        meta.add(labeled("Key", keyField));
        meta.add(labeled("Title", titleField));
        meta.add(labeled("Tags(,)", tagsField));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        editorArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        editorArea.setLineWrap(true);
        split.setLeftComponent(new JScrollPane(editorArea));

        previewPane.setEditable(false);
        previewPane.setContentType("text/html;charset=UTF-8");
        split.setRightComponent(new JScrollPane(previewPane));
        split.setDividerLocation(0.5);

        JButton saveBtn = new JButton(new AbstractAction("保存 (Ctrl+S)") {
            @Override public void actionPerformed(ActionEvent e) { saveNote(); }
        });
        JButton renderBtn = new JButton(new AbstractAction("预览 (Ctrl+P)") {
            @Override public void actionPerformed(ActionEvent e) { renderPreview(); }
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(renderBtn);
        actions.add(saveBtn);

        setLayout(new BorderLayout(8, 8));
        add(meta, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        // 快捷键
        editorArea.getInputMap().put(KeyStroke.getKeyStroke("control S"), "save");
        editorArea.getActionMap().put("save", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { saveNote(); }
        });
        editorArea.getInputMap().put(KeyStroke.getKeyStroke("control P"), "preview");
        editorArea.getActionMap().put("preview", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { renderPreview(); }
        });
    }

    private JPanel labeled(String name, JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(name + ": "), BorderLayout.WEST);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    public void setContentFromClipboard(String contentGuess) {
        if (contentGuess != null && !contentGuess.trim().isEmpty()) {
            editorArea.setText(contentGuess);
        }
    }

    private void renderPreview() {
        Node doc = mdParser.parse(editorArea.getText());
        String html = mdRenderer.render(doc);
        String wrapped = "<html><head><meta charset=\"utf-8\"/></head><body>" + html + "</body></html>";
        previewPane.setText(wrapped);
        previewPane.setCaretPosition(0);
    }

    private void saveNote() {
        NoteDto n = new NoteDto();
        n.id = UUID.randomUUID().toString();
        n.key = emptyToNull(keyField.getText());
        n.title = orDefault(titleField.getText(), "未命名");
        n.desc = n.title;
        n.tags = parseTags(tagsField.getText());
        n.bodyMd = editorArea.getText();
        n.frontMatter = null;
        long now = System.currentTimeMillis();
        n.createdAt = now;
        n.updatedAt = now;
        n.version = 1;
        repository.save(n);
        JOptionPane.showMessageDialog(this, "已保存", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String orDefault(String s, String d) { return (s==null||s.trim().isEmpty())?d:s; }
    private static String emptyToNull(String s) { return (s==null||s.trim().isEmpty())?null:s.trim(); }
    private static java.util.List<String> parseTags(String s) {
        if (s == null || s.trim().isEmpty()) return new ArrayList<>();
        String[] arr = s.split(",");
        java.util.List<String> list = new ArrayList<>();
        for (String a : arr) { String t = a.trim(); if (!t.isEmpty()) list.add(t); }
        return list;
    }
}


