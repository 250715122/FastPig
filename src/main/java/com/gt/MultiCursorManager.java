package com.gt;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * 多光标和矩形选区管理器
 * 支持 VS Code 风格的多光标编辑和矩形选区功能
 */
public class MultiCursorManager {
    
    private final JTextArea textArea;
    private boolean multiCursorMode = false;
    private final List<Integer> caretPositions = new ArrayList<>();
    private final List<Object> caretHighlightTags = new ArrayList<>();
    private final Highlighter.HighlightPainter caretPainter;
    
    // 矩形选区相关
    private boolean rectangularSelectionMode = false;
    private Point rectangularStart;
    private Point rectangularEnd;
    private final List<Object> rectangularHighlightTags = new ArrayList<>();
    private final Highlighter.HighlightPainter rectangularPainter;
    
    private KeyListener keyListener;
    private MouseListener mouseListener;
    private MouseMotionListener mouseMotionListener;
    
    public MultiCursorManager(JTextArea textArea) {
        this.textArea = textArea;
        // 使用浅蓝色半透明作为光标标记
        this.caretPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(100, 150, 255, 80));
        // 使用浅灰色半透明作为矩形选区
        this.rectangularPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(150, 150, 150, 60));
    }
    
    /**
     * 安装事件监听器
     */
    public void install() {
        installKeyListener();
        installMouseListeners();
    }
    
    /**
     * 卸载事件监听器
     */
    public void uninstall() {
        if (keyListener != null) {
            textArea.removeKeyListener(keyListener);
        }
        if (mouseListener != null) {
            textArea.removeMouseListener(mouseListener);
        }
        if (mouseMotionListener != null) {
            textArea.removeMouseMotionListener(mouseMotionListener);
        }
        exitMultiCursorMode();
        exitRectangularSelectionMode();
    }
    
    /**
     * 安装键盘监听器
     */
    private void installKeyListener() {
        keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // 在多光标模式下，拦截某些按键
                if (multiCursorMode && !caretPositions.isEmpty()) {
                    handleMultiCursorKeyPress(e);
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {
                // 在多光标模式下，同步输入字符
                if (multiCursorMode && !caretPositions.isEmpty()) {
                    handleMultiCursorKeyTyped(e);
                }
            }
        };
        
        // 使用低优先级添加，让其他监听器先处理
        textArea.addKeyListener(keyListener);
    }
    
    /**
     * 安装鼠标监听器（用于矩形选区）
     */
    private void installMouseListeners() {
        mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isAltDown()) {
                    // Alt + 鼠标按下，开始矩形选区
                    rectangularStart = e.getPoint();
                    rectangularSelectionMode = true;
                    exitMultiCursorMode(); // 退出多光标模式
                    e.consume();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (rectangularSelectionMode) {
                    rectangularEnd = e.getPoint();
                    finishRectangularSelection();
                    e.consume();
                }
            }
        };
        
        mouseMotionListener = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (rectangularSelectionMode && rectangularStart != null) {
                    rectangularEnd = e.getPoint();
                    updateRectangularSelection();
                    e.consume();
                }
            }
        };
        
        textArea.addMouseListener(mouseListener);
        textArea.addMouseMotionListener(mouseMotionListener);
    }
    
    /**
     * 向上添加光标
     */
    public void addCaretUp() {
        try {
            int currentPos = textArea.getCaretPosition();
            Rectangle rect = textArea.modelToView2D(currentPos).getBounds();
            int x = rect.x;
            int y = rect.y - rect.height / 2; // 上一行的Y坐标
            
            if (y < 0) return; // 已经在第一行
            
            Point targetPoint = new Point(x, y);
            int targetPos = textArea.viewToModel2D(targetPoint);
            
            if (targetPos >= 0 && targetPos != currentPos) {
                // 进入多光标模式
                if (!multiCursorMode) {
                    multiCursorMode = true;
                    caretPositions.add(currentPos); // 添加当前光标
                }
                
                // 添加新光标（避免重复）
                if (!caretPositions.contains(targetPos)) {
                    caretPositions.add(targetPos);
                    Collections.sort(caretPositions);
                    updateCaretHighlights();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 向下添加光标
     */
    public void addCaretDown() {
        try {
            int currentPos = textArea.getCaretPosition();
            Rectangle rect = textArea.modelToView2D(currentPos).getBounds();
            int x = rect.x;
            int y = rect.y + rect.height + rect.height / 2; // 下一行的Y坐标
            
            int maxY = textArea.getHeight();
            if (y > maxY) return; // 已经在最后一行
            
            Point targetPoint = new Point(x, y);
            int targetPos = textArea.viewToModel2D(targetPoint);
            
            if (targetPos >= 0 && targetPos != currentPos) {
                // 进入多光标模式
                if (!multiCursorMode) {
                    multiCursorMode = true;
                    caretPositions.add(currentPos); // 添加当前光标
                }
                
                // 添加新光标（避免重复）
                if (!caretPositions.contains(targetPos)) {
                    caretPositions.add(targetPos);
                    Collections.sort(caretPositions);
                    updateCaretHighlights();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 退出多光标模式
     */
    public void exitMultiCursorMode() {
        if (multiCursorMode) {
            multiCursorMode = false;
            caretPositions.clear();
            clearCaretHighlights();
        }
    }
    
    /**
     * 更新光标高亮
     */
    private void updateCaretHighlights() {
        clearCaretHighlights();
        
        try {
            for (int pos : caretPositions) {
                if (pos >= 0 && pos <= textArea.getDocument().getLength()) {
                    // 高亮单个字符位置来表示光标
                    int endPos = Math.min(pos + 1, textArea.getDocument().getLength());
                    Object tag = textArea.getHighlighter().addHighlight(pos, endPos, caretPainter);
                    caretHighlightTags.add(tag);
                }
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 清除光标高亮
     */
    private void clearCaretHighlights() {
        for (Object tag : caretHighlightTags) {
            textArea.getHighlighter().removeHighlight(tag);
        }
        caretHighlightTags.clear();
    }
    
    /**
     * 处理多光标模式下的按键
     */
    private void handleMultiCursorKeyPress(KeyEvent e) {
        int keyCode = e.getKeyCode();
        
        // ESC 退出多光标模式
        if (keyCode == KeyEvent.VK_ESCAPE) {
            exitMultiCursorMode();
            e.consume();
            return;
        }
        
        // 退格键
        if (keyCode == KeyEvent.VK_BACK_SPACE) {
            deleteAtCarets(-1);
            e.consume();
            return;
        }
        
        // Delete 键
        if (keyCode == KeyEvent.VK_DELETE) {
            deleteAtCarets(0);
            e.consume();
            return;
        }
        
        // Enter 键
        if (keyCode == KeyEvent.VK_ENTER) {
            insertAtCarets("\n");
            e.consume();
            return;
        }
        
        // Tab 键
        if (keyCode == KeyEvent.VK_TAB) {
            insertAtCarets("\t");
            e.consume();
            return;
        }
    }
    
    /**
     * 处理多光标模式下的字符输入
     */
    private void handleMultiCursorKeyTyped(KeyEvent e) {
        char ch = e.getKeyChar();
        
        // 过滤控制字符（已在 keyPressed 中处理）
        if (Character.isISOControl(ch)) {
            return;
        }
        
        // 在所有光标位置插入字符
        insertAtCarets(String.valueOf(ch));
        e.consume();
    }
    
    /**
     * 在所有光标位置插入文本
     */
    private void insertAtCarets(String text) {
        try {
            Document doc = textArea.getDocument();
            
            // 从后往前插入，避免位置偏移
            List<Integer> sortedPositions = new ArrayList<>(caretPositions);
            Collections.sort(sortedPositions, Collections.reverseOrder());
            
            for (int pos : sortedPositions) {
                if (pos >= 0 && pos <= doc.getLength()) {
                    doc.insertString(pos, text, null);
                }
            }
            
            // 更新光标位置
            int offset = text.length();
            for (int i = 0; i < caretPositions.size(); i++) {
                caretPositions.set(i, caretPositions.get(i) + offset);
            }
            
            // 更新高亮
            updateCaretHighlights();
            
            // 移动主光标到最后一个位置
            if (!caretPositions.isEmpty()) {
                textArea.setCaretPosition(caretPositions.get(caretPositions.size() - 1));
            }
            
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 在所有光标位置删除字符
     * @param offset -1 表示删除前一个字符(Backspace)，0 表示删除当前字符(Delete)
     */
    private void deleteAtCarets(int offset) {
        try {
            Document doc = textArea.getDocument();
            
            // 从后往前删除，避免位置偏移
            List<Integer> sortedPositions = new ArrayList<>(caretPositions);
            Collections.sort(sortedPositions, Collections.reverseOrder());
            
            for (int pos : sortedPositions) {
                int deletePos = pos + offset;
                if (deletePos >= 0 && deletePos < doc.getLength()) {
                    doc.remove(deletePos, 1);
                }
            }
            
            // 更新光标位置
            if (offset < 0) {
                for (int i = 0; i < caretPositions.size(); i++) {
                    int pos = caretPositions.get(i);
                    if (pos > 0) {
                        caretPositions.set(i, pos - 1);
                    }
                }
            }
            
            // 更新高亮
            updateCaretHighlights();
            
            // 移动主光标到最后一个位置
            if (!caretPositions.isEmpty()) {
                textArea.setCaretPosition(caretPositions.get(caretPositions.size() - 1));
            }
            
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 更新矩形选区的可视化
     */
    private void updateRectangularSelection() {
        clearRectangularHighlights();
        
        if (rectangularStart == null || rectangularEnd == null) {
            return;
        }
        
        try {
            // 计算矩形的行列范围
            int startPos = textArea.viewToModel2D(rectangularStart);
            int endPos = textArea.viewToModel2D(rectangularEnd);
            
            String text = textArea.getText();
            
            // 计算起始和结束的行列
            int startLine = getLineOfOffset(text, startPos);
            int endLine = getLineOfOffset(text, endPos);
            int startCol = getColumnOfOffset(text, startPos);
            int endCol = getColumnOfOffset(text, endPos);
            
            // 确保顺序正确
            if (startLine > endLine) {
                int temp = startLine;
                startLine = endLine;
                endLine = temp;
            }
            if (startCol > endCol) {
                int temp = startCol;
                startCol = endCol;
                endCol = temp;
            }
            
            // 高亮矩形区域的每一行
            for (int line = startLine; line <= endLine; line++) {
                int lineStart = getOffsetOfLine(text, line);
                int lineEnd = getLineEnd(text, lineStart);
                
                // 计算该行的实际列范围
                int actualStartCol = Math.min(startCol, lineEnd - lineStart);
                int actualEndCol = Math.min(endCol, lineEnd - lineStart);
                
                if (actualStartCol < actualEndCol) {
                    int highlightStart = lineStart + actualStartCol;
                    int highlightEnd = lineStart + actualEndCol;
                    
                    Object tag = textArea.getHighlighter().addHighlight(
                        highlightStart, highlightEnd, rectangularPainter);
                    rectangularHighlightTags.add(tag);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 完成矩形选区（创建多光标）
     */
    private void finishRectangularSelection() {
        if (rectangularStart == null || rectangularEnd == null) {
            exitRectangularSelectionMode();
            return;
        }
        
        try {
            // 计算矩形的行列范围
            int startPos = textArea.viewToModel2D(rectangularStart);
            int endPos = textArea.viewToModel2D(rectangularEnd);
            
            String text = textArea.getText();
            
            // 计算起始和结束的行列
            int startLine = getLineOfOffset(text, startPos);
            int endLine = getLineOfOffset(text, endPos);
            int startCol = getColumnOfOffset(text, startPos);
            int endCol = getColumnOfOffset(text, endPos);
            
            // 确保顺序正确
            if (startLine > endLine) {
                int temp = startLine;
                startLine = endLine;
                endLine = temp;
            }
            if (startCol > endCol) {
                int temp = startCol;
                startCol = endCol;
                endCol = temp;
            }
            
            // 为每一行创建光标
            caretPositions.clear();
            for (int line = startLine; line <= endLine; line++) {
                int lineStart = getOffsetOfLine(text, line);
                int lineEnd = getLineEnd(text, lineStart);
                
                // 计算该行的实际列位置（使用 endCol 作为光标位置）
                int actualCol = Math.min(endCol, lineEnd - lineStart);
                int caretPos = lineStart + actualCol;
                
                caretPositions.add(caretPos);
            }
            
            // 进入多光标模式
            if (!caretPositions.isEmpty()) {
                multiCursorMode = true;
                updateCaretHighlights();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        exitRectangularSelectionMode();
    }
    
    /**
     * 退出矩形选区模式
     */
    private void exitRectangularSelectionMode() {
        rectangularSelectionMode = false;
        rectangularStart = null;
        rectangularEnd = null;
        clearRectangularHighlights();
    }
    
    /**
     * 清除矩形选区高亮
     */
    private void clearRectangularHighlights() {
        for (Object tag : rectangularHighlightTags) {
            textArea.getHighlighter().removeHighlight(tag);
        }
        rectangularHighlightTags.clear();
    }
    
    // ===== 辅助方法 =====
    
    /**
     * 获取偏移量所在的行号（从0开始）
     */
    private int getLineOfOffset(String text, int offset) {
        int line = 0;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
    
    /**
     * 获取偏移量在行内的列号（从0开始）
     */
    private int getColumnOfOffset(String text, int offset) {
        int col = 0;
        for (int i = offset - 1; i >= 0 && i < text.length(); i--) {
            if (text.charAt(i) == '\n') {
                break;
            }
            col++;
        }
        return col;
    }
    
    /**
     * 获取指定行号的起始偏移量
     */
    private int getOffsetOfLine(String text, int line) {
        int currentLine = 0;
        for (int i = 0; i < text.length(); i++) {
            if (currentLine == line) {
                return i;
            }
            if (text.charAt(i) == '\n') {
                currentLine++;
            }
        }
        return text.length();
    }
    
    /**
     * 获取行的结束位置（不包含换行符）
     */
    private int getLineEnd(String text, int lineStart) {
        int end = lineStart;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        return end;
    }
    
    /**
     * 是否处于多光标模式
     */
    public boolean isMultiCursorMode() {
        return multiCursorMode;
    }
    
    /**
     * 是否处于矩形选区模式
     */
    public boolean isRectangularSelectionMode() {
        return rectangularSelectionMode;
    }
}

