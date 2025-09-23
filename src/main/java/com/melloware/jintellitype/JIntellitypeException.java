// Decompiled by Jad v1.5.8e2. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://kpdus.tripod.com/jad.html
// Decompiler options: packimports(3) fieldsfirst ansi space 
// Source File Name:   JIntellitypeException.java

package com.melloware.jintellitype;


public class JIntellitypeException extends RuntimeException
{

	public JIntellitypeException()
	{
	}

	public JIntellitypeException(String aMessage, Throwable aCause)
	{
		super(aMessage, aCause);
	}

	public JIntellitypeException(String aMessage)
	{
		super(aMessage);
	}

	public JIntellitypeException(Throwable aCause)
	{
		super(aCause);
	}
}
