package com.gt;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.ArrayList;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class SimpleSwingTest extends JFrame {

	private static final long serialVersionUID = 1L;

	public static void main(String[] args) throws IOException {
        final JFrame jf = new JFrame("代码助手 - 简化版");
        jf.setSize(800, 600);
        jf.setLocationRelativeTo(null);
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JScrollPane panel = new JScrollPane();
        panel.setSize(780, 600);
        // 创建一个 5 行 10 列的文本区域
        final JTextArea textArea = new JTextArea(5, 20);
        textArea.setSize(780, 600);
        // 设置自动换行
        textArea.setLineWrap(true);
        // 添加到内容面板
        panel.add(textArea);
        panel.setViewportView(textArea);
        textArea.setText("欢迎使用代码助手！\n\n输入关键词并按空格键搜索代码片段。\n例如：输入 'dml' 然后按空格键。");

        textArea.addKeyListener(new KeyListener() {
			
			public void keyTyped(KeyEvent e) {

			}
			
			public void keyReleased(KeyEvent e) {
				
			}
			
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
						ByteArrayOutputStream baos = new ByteArrayOutputStream();		
						e2.printStackTrace(new PrintStream(baos));
						String exception = baos.toString();	
						
						try {
							File file3 = new File("error.txt");
							Writer out = new FileWriter(file3);
							out.write(exception);
							out.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
						
						textArea.setText("搜索时发生错误，请查看error.txt文件。");
					}
				}
			}
		});
        
        jf.add(panel);
        jf.setVisible(true);
    }
} 