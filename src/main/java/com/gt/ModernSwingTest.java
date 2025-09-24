package com.gt;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.ArrayList;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

/**
 * 现代化的代码助手GUI - 使用混合热键管理器
 */
public class ModernSwingTest extends JFrame {

    private static final long serialVersionUID = 1L;
    private HotKeyManager hotKeyManager;
    private JTextArea textArea;

    public static void main(String[] args) throws IOException {
        System.out.println("启动现代化代码助手...");
        
        // 启动统一界面
        NoteRepository repo = new NoteRepository(System.getProperty("user.dir") + "/fastpig.db");
        UnifiedNoteAppFrame unified = new UnifiedNoteAppFrame(repo);
        unified.setVisible(true);
        // 同时初始化热键（Alt+S 可再次呼出界面）
        final ModernSwingTest app = new ModernSwingTest();
        app.initializeGUI();
        app.initializeHotKeys();
    }
    
    /**
     * 初始化GUI界面
     */
    private void initializeGUI() throws IOException {
        setTitle("代码助手 - 现代版");
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        
        // 添加窗口关闭事件处理
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (hotKeyManager != null) {
                    hotKeyManager.cleanup();
                }
                System.exit(0);
            }
        });

        JScrollPane panel = new JScrollPane();
        panel.setSize(780, 600);
        
        // 创建文本区域
        textArea = new JTextArea(5, 20);
        textArea.setSize(780, 600);
        textArea.setLineWrap(true);
        panel.add(textArea);
        panel.setViewportView(textArea);
        textArea.setText("欢迎使用现代化代码助手！\n\n" +
                        "功能说明：\n" +
                        "1. 输入关键词并按空格键搜索代码片段\n" +
                        "2. 输入时会在光标位置下方显示智能提示弹窗\n" +
                        "3. 使用↑↓键选择提示，Enter确认，Esc关闭\n" +
                        "4. 全局热键功能会自动检测并启用最佳方案\n\n" +
                        "例如：输入 'mysql' 然后按空格键。");

        // 添加搜索功能
        textArea.addKeyListener(new KeyListener() {
            public void keyTyped(KeyEvent e) {}
            public void keyReleased(KeyEvent e) {}
            
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode()==KeyEvent.VK_SPACE){
                    try {
                        String content = CodeReplace.search(textArea.getText());
                        if(content != null && content.trim().length()>0){
                            textArea.setText(content);
                        }else{
                            textArea.setText("未找到相关代码片段，请尝试其他关键词。");
                        }
                    } catch (Exception e2) {
                        handleException(e2);
                    }
                }
            }
        });
        
        // 设置自动补全
        try {
            ArrayList<String> items = CodeReplace.searchHelper("");
            setupAutoComplete(textArea, items);
        } catch (IOException e) {
            System.err.println("初始化自动补全失败: " + e.getMessage());
        }
        
        textArea.setEditable(true);
        setContentPane(panel);
    }
    
    /**
     * 初始化热键管理器
     */
    private void initializeHotKeys() {
        hotKeyManager = new HotKeyManager(this, textArea);
        hotKeyManager.initialize();
        
        // 根据热键方法显示相应信息
        String methodInfo = "";
        switch (hotKeyManager.getActiveMethod()) {
            case JNATIVEHOOK:
                methodInfo = "✓ 使用JNativeHook全局热键（推荐）";
                break;
            case JINTELLITYPE:
                methodInfo = "✓ 使用JIntellitype全局热键（传统）";
                break;
            case NONE:
                methodInfo = "⚠ 全局热键不可用，请以管理员权限运行";
                break;
        }
        
        // 在文本区域底部显示状态信息
        String currentText = textArea.getText();
        textArea.setText(currentText + "\n\n状态：" + methodInfo + "\n提示：Alt+S 打开三合一编辑器");
    }
    
    /**
     * 设置光标位置跟随的自动补全功能
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void setupAutoComplete(final JTextArea txtInput, final ArrayList<String> items) {
        final JPopupMenu popup = new JPopupMenu();
        final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        final JList<String> suggestionList = new JList<>(model);
        
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setVisibleRowCount(8);
        
        JScrollPane scrollPane = new JScrollPane(suggestionList);
        scrollPane.setPreferredSize(new Dimension(300, 160));
        // 避免弹窗组件抢夺焦点
        suggestionList.setFocusable(false);
        scrollPane.setFocusable(false);
        popup.setFocusable(false);
        popup.add(scrollPane);

        // 键盘事件处理 - 专注于导航和选择
        txtInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (popup.isVisible()) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        String selectedValue = suggestionList.getSelectedValue();
                        if (selectedValue != null && !selectedValue.isEmpty()) {
                            try {
                                String keyword = selectedValue.split(":")[0].trim();
                                txtInput.setText(CodeReplace.searchAccurate(keyword));
                            } catch (IOException e1) {
                                handleException(e1);
                            }
                            popup.setVisible(false);
                            e.consume();
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        popup.setVisible(false);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                        int selectedIndex = suggestionList.getSelectedIndex();
                        if (selectedIndex > 0) {
                            suggestionList.setSelectedIndex(selectedIndex - 1);
                        }
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        int selectedIndex = suggestionList.getSelectedIndex();
                        if (selectedIndex < model.getSize() - 1) {
                            suggestionList.setSelectedIndex(selectedIndex + 1);
                        }
                        e.consume();
                    }
                    // 允许其他键继续正常输入，不再消费
                }
            }
        });

        // 文档监听器 - 处理文本变化
        txtInput.getDocument().addDocumentListener(new DocumentListener() {
            private Timer updateTimer;
            private boolean updating = false;

            public void insertUpdate(DocumentEvent e) {
                scheduleUpdate();
            }

            public void removeUpdate(DocumentEvent e) {
                scheduleUpdate();
            }

            public void changedUpdate(DocumentEvent e) {
                scheduleUpdate();
            }

            private void scheduleUpdate() {
                if (updating) return;
                
                if (updateTimer != null) {
                    updateTimer.stop();
                }
                
                updateTimer = new Timer(200, evt -> {
                    if (!updating) {
                        updating = true;
                        try {
                            updateSuggestions();
                        } finally {
                            updating = false;
                        }
                    }
                });
                updateTimer.setRepeats(false);
                updateTimer.start();
            }

            private void updateSuggestions() {
                try {
                    String input = txtInput.getText().trim();
                    
                    if (input.isEmpty()) {
                        popup.setVisible(false);
                        return;
                    }

                    if (input.length() > 50) {
                        return;
                    }

                    model.removeAllElements();
                    
                    // 动态获取匹配的命令提示
                    ArrayList<String> currentItems = CodeReplace.searchHelper(input);
                    int addedCount = 0;

                    for (String item : currentItems) {
                        if (item.trim().toLowerCase().contains(input.toLowerCase())) {
                            model.addElement(item.trim());
                            addedCount++;
                            if (addedCount >= 10) break;
                        }
                    }

                    // 如果没有找到动态匹配，则使用原始items列表
                    if (addedCount == 0) {
                        for (String item : items) {
                            if (item.trim().toLowerCase().contains(input.toLowerCase())) {
                                model.addElement(item.trim());
                                addedCount++;
                                if (addedCount >= 10) break;
                            }
                        }
                    }

                    if (addedCount > 0) {
                        suggestionList.setSelectedIndex(0);
                        showPopupAtCursor(txtInput, popup);
                        // 显示后立即把焦点还给文本框，防止输入被阻断
                        javax.swing.SwingUtilities.invokeLater(() -> txtInput.requestFocusInWindow());
                    } else {
                        popup.setVisible(false);
                    }

                } catch (Exception ex) {
                    System.err.println("自动补全更新失败: " + ex.getMessage());
                    popup.setVisible(false);
                }
            }
        });
    }

    /**
     * 在光标位置下方显示弹出窗口
     */
    private void showPopupAtCursor(JTextArea textArea, JPopupMenu popup) {
        try {
            int caretPosition = textArea.getCaretPosition();
            Rectangle caretRect = textArea.modelToView(caretPosition);
            
            if (caretRect != null) {
                // 计算弹出窗口位置：光标下方一行
                int x = caretRect.x;
                int y = caretRect.y + caretRect.height + 2; // 光标下方2像素
                
                // 确保弹出窗口不超出文本区域边界
                Dimension textAreaSize = textArea.getSize();
                Dimension popupSize = popup.getPreferredSize();
                
                if (x + popupSize.width > textAreaSize.width) {
                    x = textAreaSize.width - popupSize.width;
                }
                if (x < 0) x = 0;
                
                if (y + popupSize.height > textAreaSize.height) {
                    // 如果下方空间不够，显示在光标上方
                    y = caretRect.y - popupSize.height - 2;
                }
                if (y < 0) y = caretRect.y + caretRect.height + 2;
                
                popup.show(textArea, x, y);
            }
        } catch (BadLocationException e) {
            // 如果无法获取光标位置，则不显示弹出窗口
            popup.setVisible(false);
        }
    }
    
    
    /**
     * 处理异常
     */
    private void handleException(Exception e) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();        
        e.printStackTrace(new PrintStream(baos));
        String exception = baos.toString();    
        
        try {
            File file3 = new File("error.txt");
            Writer out = new FileWriter(file3);
            out.write(exception);
            out.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        
        textArea.setText("搜索时发生错误，请查看error.txt文件。\n错误信息：" + e.getMessage());
    }
}

