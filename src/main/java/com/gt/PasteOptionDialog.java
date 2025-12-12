package com.gt;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * 粘贴选项对话框 - 支持键盘方向键导航和Enter确认
 */
public class PasteOptionDialog extends JDialog {
    private int selectedOption = -1;
    private final String[] options;
    private JList<String> optionList;
    
    public PasteOptionDialog(Frame parent, String message, String title, String[] options, int defaultIndex) {
        super(parent, title, true); // modal
        this.options = options;
        
        initUI(message, defaultIndex);
        setupKeyBindings();
        
        setLocationRelativeTo(parent);
    }
    
    private void initUI(String message, int defaultIndex) {
        setLayout(new BorderLayout(10, 10));
        
        // 顶部消息
        JLabel messageLabel = new JLabel(message);
        messageLabel.setBorder(new EmptyBorder(15, 20, 10, 20));
        messageLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        add(messageLabel, BorderLayout.NORTH);
        
        // 中间选项列表
        optionList = new JList<>(options);
        optionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        optionList.setSelectedIndex(defaultIndex >= 0 && defaultIndex < options.length ? defaultIndex : 0);
        optionList.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        optionList.setBorder(new EmptyBorder(5, 10, 5, 10));
        
        // 自定义渲染器 - 增加行高和内边距
        optionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                                                         int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(8, 12, 8, 12));
                
                if (isSelected) {
                    label.setBackground(new Color(64, 158, 255));
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(Color.WHITE);
                    label.setForeground(new Color(60, 65, 70));
                }
                
                return label;
            }
        });
        
        // 鼠标双击确认
        optionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    confirmSelection();
                }
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(optionList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)));
        scrollPane.setPreferredSize(new Dimension(400, 150));
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new EmptyBorder(0, 20, 10, 20));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
        
        // 底部提示
        JLabel hintLabel = new JLabel("提示: 使用 ↑↓ 方向键选择，Enter 确认，Esc 取消");
        hintLabel.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 11));
        hintLabel.setForeground(new Color(130, 135, 140));
        hintLabel.setBorder(new EmptyBorder(5, 20, 15, 20));
        add(hintLabel, BorderLayout.SOUTH);
        
        pack();
    }
    
    private void setupKeyBindings() {
        // Enter键 - 确认选择
        optionList.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirm");
        optionList.getActionMap().put("confirm", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmSelection();
            }
        });
        
        // Esc键 - 取消
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
        
        // 让列表获得焦点
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                optionList.requestFocusInWindow();
            }
        });
    }
    
    private void confirmSelection() {
        selectedOption = optionList.getSelectedIndex();
        dispose();
    }
    
    private void cancel() {
        selectedOption = -1;
        dispose();
    }
    
    /**
     * 获取用户选择的选项索引
     * @return 选项索引，如果取消则返回-1
     */
    public int getSelectedOption() {
        return selectedOption;
    }
    
    /**
     * 显示对话框并返回用户选择
     * @param parent 父窗口
     * @param message 提示消息
     * @param title 对话框标题
     * @param options 选项数组
     * @param defaultIndex 默认选中的索引
     * @return 用户选择的选项索引，如果取消则返回-1
     */
    public static int showOptionDialog(Frame parent, String message, String title, 
                                      String[] options, int defaultIndex) {
        PasteOptionDialog dialog = new PasteOptionDialog(parent, message, title, options, defaultIndex);
        dialog.setVisible(true);
        return dialog.getSelectedOption();
    }
}

