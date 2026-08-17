package com.gt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * 私密笔记解锁对话框。
 *
 * 本项目没有笔记列表，整个界面就是一个编辑区，因此解锁入口是"打开笔记时"。
 * 支持用笔记密码解锁，也支持切换成主密码作为忘记密码后的恢复通道。
 */
public class UnlockNoteDialog extends JDialog {

    private JPasswordField passwordField;
    private JCheckBox useMasterCheckBox;
    private JLabel hintLabel;

    private char[] password;
    private boolean useMaster;
    private boolean confirmed;

    private UnlockNoteDialog(Window parent, String noteKey, boolean masterAvailable) {
        super(parent, "解锁笔记", ModalityType.APPLICATION_MODAL);
        initUI(noteKey, masterAvailable);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initUI(String noteKey, boolean masterAvailable) {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(16, 20, 14, 20));
        content.setBackground(UIColors.BG_PRIMARY);

        JLabel title = new JLabel("笔记「" + noteKey + "」已加密，请输入密码");
        title.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        title.setForeground(UIColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(10, 0, 6, 0));

        // UIComponents 没有密码框工厂方法，参照 CloudSyncPanel 的手工构造
        passwordField = new JPasswordField(24);
        passwordField.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        passwordField.setBackground(UIColors.BG_PRIMARY);
        passwordField.setForeground(UIColors.TEXT_PRIMARY);
        passwordField.setCaretColor(UIColors.TEXT_PRIMARY);
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIColors.BORDER_BASE),
                new EmptyBorder(6, 8, 6, 8)));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(passwordField);

        useMasterCheckBox = new JCheckBox("忘记密码，使用主密码恢复");
        useMasterCheckBox.setOpaque(false);
        useMasterCheckBox.setForeground(UIColors.TEXT_SECONDARY);
        useMasterCheckBox.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        useMasterCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        useMasterCheckBox.setEnabled(masterAvailable);
        useMasterCheckBox.addActionListener(e -> {
            hintLabel.setText(useMasterCheckBox.isSelected()
                    ? "将用主密码解锁这篇笔记" : " ");
            passwordField.requestFocusInWindow();
        });
        center.add(Box.createVerticalStrut(8));
        center.add(useMasterCheckBox);

        hintLabel = new JLabel(masterAvailable ? " " : "这篇笔记没有主密码恢复信息");
        hintLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 12));
        hintLabel.setForeground(UIColors.TEXT_SECONDARY);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(Box.createVerticalStrut(4));
        center.add(hintLabel);

        content.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancelBtn = new JButton("取消");
        JButton okBtn = new JButton("解锁");
        cancelBtn.addActionListener(e -> { confirmed = false; dispose(); });
        okBtn.addActionListener(e -> accept());
        buttons.add(cancelBtn);
        buttons.add(okBtn);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(okBtn);

        // Esc 取消
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelUnlock");
        getRootPane().getActionMap().put("cancelUnlock", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                confirmed = false;
                dispose();
            }
        });

        SwingUtilities.invokeLater(() -> passwordField.requestFocusInWindow());
    }

    private void accept() {
        char[] input = passwordField.getPassword();
        if (input == null || input.length == 0) {
            hintLabel.setText("请输入密码");
            return;
        }
        password = input;
        useMaster = useMasterCheckBox.isSelected();
        confirmed = true;
        dispose();
    }

    /**
     * 弹出解锁框。
     *
     * @return 用户输入，取消则返回 null。调用方用完必须调用 wipe() 清除密码。
     */
    public static Result prompt(Window parent, String noteKey, boolean masterAvailable) {
        UnlockNoteDialog dialog = new UnlockNoteDialog(parent, noteKey, masterAvailable);
        dialog.setVisible(true);
        if (!dialog.confirmed) {
            return null;
        }
        return new Result(dialog.password, dialog.useMaster);
    }

    /** 解锁输入。密码用 char[] 承载，便于用完立即清零。 */
    public static final class Result {
        public final char[] password;
        public final boolean useMaster;

        Result(char[] password, boolean useMaster) {
            this.password = password;
            this.useMaster = useMaster;
        }

        public void wipe() {
            com.gt.crypto.NoteCrypto.wipe(password);
        }
    }
}
