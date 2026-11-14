package com.gt.kb;

import com.gt.NoteDto;
import com.gt.UIColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * 笔记分类选择框：两级分类 + 可见性 + 层。
 *
 * 个人类与随笔不允许开放给 AI，这里直接不给选项；
 * 真正的兜底在 NoteDto.effectiveVisibility()，避免绕过 UI 改文件就能开放。
 */
public class KbClassifyDialog extends JDialog {

    public record Result(String domain, String topic, String visibility, String layer) {}

    private static final String LABEL_HUMAN = "仅人可见";
    private static final String LABEL_AI = "AI 可见";
    private static final String LABEL_PRIVATE = "私密（加密）";
    private static final String LABEL_CURATED = "提炼层";
    private static final String LABEL_RAW = "原始层";

    private final NoteDto note;
    private JComboBox<String> domainBox;
    private JComboBox<String> topicBox;
    private JComboBox<String> visibilityBox;
    private JComboBox<String> layerBox;
    private JLabel hintLabel;
    private boolean confirmed;

    private KbClassifyDialog(Window parent, Taxonomy taxonomy, NoteDto note) {
        super(parent, "笔记分类", ModalityType.APPLICATION_MODAL);
        this.note = note;
        initUI(taxonomy);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initUI(Taxonomy taxonomy) {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(16, 20, 14, 20));
        content.setBackground(UIColors.BG_PRIMARY);

        JLabel title = new JLabel("笔记「" + note.key + "」的分类");
        title.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        title.setForeground(UIColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 0, 4, 8);
        gc.anchor = GridBagConstraints.WEST;

        hintLabel = new JLabel(" ");
        hintLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        hintLabel.setForeground(UIColors.TEXT_SECONDARY);

        domainBox = new JComboBox<>(taxonomy.domainNames().toArray(new String[0]));
        domainBox.setSelectedItem(NoteDto.orDefault(note.domain, NoteDto.DEFAULT_DOMAIN));
        domainBox.addActionListener(e -> {
            reloadTopics(taxonomy);
            reloadVisibility(visibilityValue());
        });

        topicBox = new JComboBox<>();
        reloadTopics(taxonomy);
        topicBox.setSelectedItem(NoteDto.orDefault(note.topic, NoteDto.DEFAULT_TOPIC));

        visibilityBox = new JComboBox<>();
        layerBox = new JComboBox<>(new String[]{LABEL_CURATED, LABEL_RAW});
        layerBox.setSelectedItem(NoteDto.LAYER_RAW.equals(note.layer) ? LABEL_RAW : LABEL_CURATED);
        reloadVisibility(note.effectiveVisibility());

        addRow(form, gc, 0, "一级分类", domainBox);
        addRow(form, gc, 1, "二级分类", topicBox);
        addRow(form, gc, 2, "可见性", visibilityBox);
        addRow(form, gc, 3, "所属层", layerBox);

        gc.gridx = 0;
        gc.gridy = 4;
        gc.gridwidth = 2;
        form.add(hintLabel, gc);

        content.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancelBtn = new JButton("取消");
        JButton okBtn = new JButton("保存");
        cancelBtn.addActionListener(e -> { confirmed = false; dispose(); });
        okBtn.addActionListener(e -> { confirmed = true; dispose(); });
        buttons.add(cancelBtn);
        buttons.add(okBtn);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(okBtn);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelClassify");
        getRootPane().getActionMap().put("cancelClassify", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                confirmed = false;
                dispose();
            }
        });
    }

    private void addRow(JPanel form, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 1;
        JLabel l = new JLabel(label);
        l.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        l.setForeground(UIColors.TEXT_PRIMARY);
        form.add(l, gc);
        gc.gridx = 1;
        field.setPreferredSize(new Dimension(200, 28));
        form.add(field, gc);
    }

    private void reloadTopics(Taxonomy taxonomy) {
        String domain = (String) domainBox.getSelectedItem();
        String previous = (String) topicBox.getSelectedItem();
        topicBox.removeAllItems();
        for (String t : taxonomy.topicNames(domain)) {
            topicBox.addItem(t);
        }
        if (previous != null && taxonomy.contains(domain, previous)) {
            topicBox.setSelectedItem(previous);
        }
    }

    private void reloadVisibility(String desired) {
        visibilityBox.removeAllItems();
        if (note.encrypted) {
            visibilityBox.addItem(LABEL_PRIVATE);
            visibilityBox.setEnabled(false);
            hintLabel.setText("加密笔记固定为私密，不可开放");
            return;
        }
        visibilityBox.addItem(LABEL_HUMAN);
        String domain = (String) domainBox.getSelectedItem();
        boolean aiAllowed = !NoteDto.DOMAIN_PERSONAL.equals(domain)
                && !NoteDto.TYPE_ESSAY.equals(NoteDto.normalizeNoteType(note.noteType));
        if (aiAllowed) {
            visibilityBox.addItem(LABEL_AI);
            hintLabel.setText(" ");
        } else {
            hintLabel.setText("个人类与随笔不可开放给 AI");
        }
        visibilityBox.setEnabled(true);
        visibilityBox.setSelectedItem(NoteDto.VISIBILITY_AI.equals(desired) && aiAllowed ? LABEL_AI : LABEL_HUMAN);
    }

    private String visibilityValue() {
        String selected = (String) visibilityBox.getSelectedItem();
        if (LABEL_AI.equals(selected)) return NoteDto.VISIBILITY_AI;
        if (LABEL_PRIVATE.equals(selected)) return NoteDto.VISIBILITY_PRIVATE;
        return NoteDto.VISIBILITY_HUMAN;
    }

    /** 弹出分类框，取消返回 null。 */
    public static Result prompt(Window parent, Taxonomy taxonomy, NoteDto note) {
        KbClassifyDialog dialog = new KbClassifyDialog(parent, taxonomy, note);
        dialog.setVisible(true);
        if (!dialog.confirmed) {
            return null;
        }
        String domain = (String) dialog.domainBox.getSelectedItem();
        String topic = (String) dialog.topicBox.getSelectedItem();
        String layer = LABEL_RAW.equals(dialog.layerBox.getSelectedItem())
                ? NoteDto.LAYER_RAW : NoteDto.LAYER_CURATED;
        return new Result(domain, topic, dialog.visibilityValue(), layer);
    }
}