package com.test;
//
//import com.sun.jna.Native;
//import com.sun.jna.platform.win32.WinDef.HWND;
//import com.sun.jna.win32.StdCallLibrary;
//
//public class Show_QQ_Window {
//	public interface User32 extends StdCallLibrary {
//	      User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);
//	      boolean ShowWindow(HWND hWnd, int nCmdShow);
//          boolean SetForegroundWindow(HWND hWnd);
//          //HWND FindWindowA(String winClass, String title);
//          HWND GetForegroundWindow();
//	   }
//
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) {
//
//		final User32 user32 = User32.INSTANCE;
//
//
////		HWND hWnd = user32.FindWindowA(null, "暴雪");
////		qq聊天窗口好友名
//		HWND hWnd = user32.GetForegroundWindow();
//		//qq聊天窗口群备注名
//		user32.ShowWindow(hWnd,1);
//		user32.SetForegroundWindow(hWnd);
//
//		if (hWnd == null) {
//		    System.out.println("no 行");
//		}
//
//	}
//}