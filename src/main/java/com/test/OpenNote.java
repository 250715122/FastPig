package com.test;

//import java.awt.AWTException;
//import java.awt.Robot;
//import java.awt.event.KeyEvent;
//import java.io.IOException;
//
//import com.sun.jna.Native;
//import com.sun.jna.platform.win32.WinDef.HWND;
//import com.sun.jna.win32.StdCallLibrary;
//import com.test.Show_QQ_Window.User32;
//
//public class OpenNote {
//
//	public interface User32 extends StdCallLibrary {
//	      User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);
//	      boolean ShowWindow(HWND hWnd, int nCmdShow);
//        boolean SetForegroundWindow(HWND hWnd);
//        //HWND FindWindowA(String winClass, String title);
//        HWND GetForegroundWindow();
//	   }
//
//	public static void main(String[] args) {
//		try {
//			Runtime.getRuntime().exec("C:\\WINDOWS\\system32\\notepad.exe C:\\Users\\guote\\Desktop\\KSProfile.py");
//
//			final User32 user32 = User32.INSTANCE;
//
//			HWND hWnd = user32.GetForegroundWindow();
//
//			if (hWnd == null) {
//			    System.out.println("no 行");
//			}
//
//			pressCtrlAndSingleKeyByNumber(KeyEvent.VK_G);
//
//		} catch (IOException e1) {
//			System.out.println("游戏规则txt打开失败！");
//			e1.printStackTrace();
//		}
//
//	}
//
//
//	public static final void pressCtrlAndSingleKeyByNumber(int keycode) {
//	    try {
//	        Robot robot = new Robot();
//	        robot.keyPress(KeyEvent.VK_CONTROL);
//	        robot.keyPress(KeyEvent.VK_F);
//	        robot.keyRelease(KeyEvent.VK_F);
//	        robot.keyRelease(KeyEvent.VK_CONTROL);
//	        robot.delay(100);
//	    } catch (AWTException e) {
//	        e.printStackTrace();
//	    }
//	}
//}
