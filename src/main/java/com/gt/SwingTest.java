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

import com.melloware.jintellitype.HotkeyListener;
import com.melloware.jintellitype.JIntellitype;

public class SwingTest extends JFrame {

	private static final long serialVersionUID = 1L;

	public static void main(String[] args) throws IOException {
        final JFrame jf = new JFrame("codeAssistant");
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
        textArea.setText(null);

        textArea.addKeyListener(new KeyListener() {
			
			public void keyTyped(KeyEvent e) {

			}
			
			public void keyReleased(KeyEvent e) {
				
			}
			
			public void keyPressed(KeyEvent e) {
				
				if(e.getKeyCode()==KeyEvent.VK_SPACE){
					
					try {
						String content = CodeReplace.search(textArea.getText());
						if(content.trim().length()>0){
							textArea.setText(content);
						}else{
							textArea.setText("");
						}
					} catch (Exception e2) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();		
						e2.printStackTrace(new PrintStream(baos));
						String exception = baos.toString();	
						
						try {
							
							File file3 =new File("A:\\error.txt");
							Writer out;
							out = new FileWriter(file3);
							out.write(exception);
							out.close();
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}
				}
			}
		});
        
        //定义热键标识，用于在设置多个热键时，在事件处理中区分用户按下的热键
        final int MAX_KEY_MARK = 1;
        final int MIN_KEY_MARK = 0;
        final int EXIT_KEY_MARK = 2;
        final int NORMAL_KEY_MARK = 3;
        
        //第一步：注册热键，第一个参数表示该热键的标识，第二个参数表示组合键，如果没有则为0，第三个参数为定义的主要热键
        try {
            JIntellitype.getInstance().registerHotKey(MAX_KEY_MARK, JIntellitype.MOD_ALT, (int)'M');  
            JIntellitype.getInstance().registerHotKey(MIN_KEY_MARK, JIntellitype.MOD_ALT, (int)'L');  
            JIntellitype.getInstance().registerHotKey(EXIT_KEY_MARK, JIntellitype.MOD_ALT, (int)'Q');
            JIntellitype.getInstance().registerHotKey(NORMAL_KEY_MARK, JIntellitype.MOD_ALT, (int)'N');
            
            //第二步：添加热键监听器
            JIntellitype.getInstance().addHotKeyListener(new HotkeyListener() {
                
                @SuppressWarnings("static-access")
    			public void onHotKey(int markCode) {
                	switch (markCode) {  
                		case NORMAL_KEY_MARK:  
                			jf.setExtendedState(jf.NORMAL);
                			jf.setVisible(true);
                			jf.toFront();
                			textArea.grabFocus();
                			break;
                		case MAX_KEY_MARK:  
                			jf.setExtendedState(jf.MAXIMIZED_BOTH);
                			jf.setVisible(true);
                			jf.toFront();
                			textArea.grabFocus();
                			break;
                		case MIN_KEY_MARK:  
                	    	jf.setExtendedState(jf.ICONIFIED);
                	    	break;
                	    case EXIT_KEY_MARK:  
                	        System.exit(0);
                	        break;
                	    }
                }
            });
            System.out.println("全局热键注册成功！");
            System.out.println("Alt+N: 显示/恢复窗口");
            System.out.println("Alt+M: 最大化窗口");
            System.out.println("Alt+L: 最小化窗口");
            System.out.println("Alt+Q: 退出程序");
        } catch (Exception e) {
            System.err.println("全局热键注册失败: " + e.getMessage());
            System.err.println("可能是JIntellitype库不兼容当前系统或Java版本");
            System.err.println("程序将继续运行，但全局热键功能不可用");
            e.printStackTrace();
        } 
        
        //增加文本提示 - 获取所有可用的命令提示
        ArrayList<String> items = CodeReplace.searchHelper("");
        setupAutoComplete(textArea, items);
        
        textArea.setEditable(true);
        
        jf.setContentPane(panel);
        jf.setVisible(true);
    }
	
	@SuppressWarnings({ "rawtypes", "unchecked", "serial" })
	public static void setupAutoComplete(final JTextArea txtInput,final ArrayList<String> items) {
        final DefaultComboBoxModel model = new DefaultComboBoxModel();
        final JComboBox cbInput = new JComboBox(model) {
            public Dimension getPreferredSize() {
                return new Dimension(super.getPreferredSize().width, 0);
            }
        };

        cbInput.setSelectedItem(null);

      txtInput.addKeyListener(new KeyAdapter() {

          @Override
          public void keyPressed(KeyEvent e) {

              if (e.getKeyCode() == KeyEvent.VK_ENTER
                      || e.getKeyCode() == KeyEvent.VK_UP
                      || e.getKeyCode() == KeyEvent.VK_DOWN) {
                  e.setSource(cbInput);
                  cbInput.dispatchEvent(e);
                  if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                	  if(null == cbInput.getSelectedItem() || "".equals(cbInput.getSelectedItem().toString())){

                	  }else{
                          //txtInput.setText(cbInput.getSelectedItem().toString().split(":")[0].trim());
                          try {
							txtInput.setText(CodeReplace.searchAccurate(cbInput.getSelectedItem().toString().split(":")[0].trim()));
						} catch (IOException e1) {
							e1.printStackTrace();
						}
                          cbInput.setPopupVisible(false);
                	  }
                  }
              }
              if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                  cbInput.setPopupVisible(false);
              }
              //setAdjusting(cbInput, false);
          }
      });        
        
        txtInput.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateList();
            }

            public void removeUpdate(DocumentEvent e) {
                updateList();
            }

            public void changedUpdate(DocumentEvent e) {
                updateList();
            }

            private void updateList() {
                try {
                    //setAdjusting(cbInput, true);
                    model.removeAllElements();
                    String input = txtInput.getText();
                    if (!input.trim().isEmpty()) {
                        // 动态获取匹配的命令提示
                        ArrayList<String> currentItems = CodeReplace.searchHelper(input);
                        for (String item : currentItems) {
                            if(item.trim().toLowerCase().indexOf(input.trim().toLowerCase())!=-1){
                                model.addElement(item.trim());
                            }
                        }
                        // 如果没有找到动态匹配，则使用原始items列表
                        if (model.getSize() == 0) {
                            for (String item : items) {
                                if(item.trim().toLowerCase().indexOf(input.trim().toLowerCase())!=-1){
                                    model.addElement(item.trim());
                                }
                            }
                        }
                    }
                    cbInput.setPopupVisible(model.getSize() > 0);
                    //setAdjusting(cbInput, false);
                } catch (Exception ex) {
                    // 如果动态获取失败，使用原始逻辑
                    model.removeAllElements();
                    String input = txtInput.getText();
                    if (!input.trim().isEmpty()) {
                        for (String item : items) {
                            if(item.trim().toLowerCase().indexOf(input.trim().toLowerCase())!=-1){
                                model.addElement(item.trim());
                            }
                        }
                    }
                    cbInput.setPopupVisible(model.getSize() > 0);
                }
            }
        });
        txtInput.setLayout(new BorderLayout());
        txtInput.add(cbInput, BorderLayout.SOUTH);
    }
	
}