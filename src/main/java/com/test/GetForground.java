package com.test;
//
//import com.sun.jna.*;
//import com.sun.jna.win32.*;
//
//public class GetForground {
//    public interface User32 extends StdCallLibrary {
//        User32 INSTANCE = (User32) Native.loadLibrary("user32", User32.class);
//
//        int GetWindowTextA(PointerType hWnd, byte[] lpString, int nMaxCount);
//    }
//
//    public static void main(){
//        byte[] windowText = new byte[512];
//
////        PointerType hwnd = GetForegroundWindow();
////        User32.INSTANCE.GetWindowTextA(hwnd, windowText, 512);
////        System.out.println(Native.toString(windowText));
//
//    }
//}