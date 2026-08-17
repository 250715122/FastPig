package com.gt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 安全设置面板：管理主密码。
 *
 * 主密码是所有私密笔记的恢复通道，忘记单篇笔记密码时用它解锁。
 * 这里的操作立即生效并落盘，不参与设置对话框的"保存/取消"流程，
 * 否则用户点了取消会以为主密码没设成功，实际状态却已改变。
 */
public class SecurityPanel extends JPanel {

    private final SettingsDialog parent;
    private JLabel statusLabel;
    private JButton actionButton;

    public SecurityPanel(SettingsDialog parent) {
        this.parent = parent;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UIColors.BG_PRIMARY);
        setBorder(new EmptyBorder(30, 40, 30, 40));

        initializeComponents();
        refreshStatus();
    }

    private void initializeComponents() {
        JLabel title = new JLabel("主密码");
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 18));
        title.setForeground(UIColors.TEXT_PRIMARY);
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);

        add(Box.createVerticalStrut(12));

        JLabel desc = new JLabel("<html><body style='width:420px'>"
                + "私密笔记用各自的密码加密。主密码是忘记笔记密码后唯一的恢复通道，"
                + "设置后才能把笔记设为私密（Alt+K）。<br><br>"
                + "<b>主密码本身没有找回方式</b>，请务必记牢。"
                + "</body></html>");
        desc.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        desc.setForeground(UIColors.TEXT_SECONDARY);
        desc.setAlignmentX(LEFT_ALIGNMENT);
        add(desc);

        add(Box.createVerticalStrut(20));

        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(statusLabel);

        add(Box.createVerticalStrut(14));

        actionButton = UIComponents.createPrimaryButton("设置主密码");
        actionButton.setAlignmentX(LEFT_ALIGNMENT);
        actionButton.addActionListener(e -> onAction());
        add(actionButton);

        add(Box.createVerticalGlue());
    }

    private void refreshStatus() {
        boolean set = AppConfig.getInstance().isMasterPasswordSet();
        statusLabel.setText(set ? "状态：已设置" : "状态：未设置");
        statusLabel.setForeground(set ? UIColors.SUCCESS : UIColors.TEXT_SECONDARY);
        actionButton.setText(set ? "修改主密码" : "设置主密码");
    }

    private void onAction() {
        AppConfig config = AppConfig.getInstance();
        if (config.isMasterPasswordSet()) {
            changeMasterPassword(config);
        } else {
            setMasterPassword(config);
        }
        refreshStatus();
    }

    private void setMasterPassword(AppConfig config) {
        JPasswordField pwd = new JPasswordField(20);
        JPasswordField confirm = new JPasswordField(20);
        int result = JOptionPane.showConfirmDialog(this,
                new Object[]{"请输入主密码", pwd, "再次输入以确认", confirm},
                "设置主密码", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        char[] p1 = pwd.getPassword();
        char[] p2 = confirm.getPassword();
        try {
            if (p1.length < 6) {
                JOptionPane.showMessageDialog(this, "主密码至少 6 位", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!java.util.Arrays.equals(p1, p2)) {
                JOptionPane.showMessageDialog(this, "两次输入的密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                config.setMasterPassword(p1);
                config.save();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(this, "主密码已设置", "完成", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            com.gt.crypto.NoteCrypto.wipe(p1);
            com.gt.crypto.NoteCrypto.wipe(p2);
        }
    }

    private void changeMasterPassword(AppConfig config) {
        JPasswordField oldPwd = new JPasswordField(20);
        JPasswordField pwd = new JPasswordField(20);
        JPasswordField confirm = new JPasswordField(20);
        int result = JOptionPane.showConfirmDialog(this,
                new Object[]{"当前主密码", oldPwd, "新主密码", pwd, "再次输入以确认", confirm},
                "修改主密码", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        char[] old = oldPwd.getPassword();
        char[] p1 = pwd.getPassword();
        char[] p2 = confirm.getPassword();
        try {
            if (!config.verifyMasterPassword(old)) {
                JOptionPane.showMessageDialog(this, "当前主密码错误", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (p1.length < 6) {
                JOptionPane.showMessageDialog(this, "主密码至少 6 位", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!java.util.Arrays.equals(p1, p2)) {
                JOptionPane.showMessageDialog(this, "两次输入的密码不一致", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // 先把所有私密笔记的恢复通道换到新主密码，成功了才改配置。
            // 顺序反过来的话，中途失败就会出现"配置里是新主密码、笔记里包的是旧主密码"，
            // 那些笔记的恢复通道会直接失效。
            int rewrapped;
            try {
                rewrapped = parent.getParentFrame().getNoteService()
                        .rewrapAllWithNewMaster(old, p1);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "主密码未修改", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                config.setMasterPassword(p1);
                config.save();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "保存失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(this,
                    rewrapped > 0
                            ? "主密码已修改，已同步更新 " + rewrapped + " 篇私密笔记的恢复通道"
                            : "主密码已修改",
                    "完成", JOptionPane.INFORMATION_MESSAGE);
        } finally {
            com.gt.crypto.NoteCrypto.wipe(old);
            com.gt.crypto.NoteCrypto.wipe(p1);
            com.gt.crypto.NoteCrypto.wipe(p2);
        }
    }
}
