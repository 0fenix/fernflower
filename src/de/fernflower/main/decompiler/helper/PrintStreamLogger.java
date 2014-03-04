/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.main.decompiler.helper;

import java.io.PrintStream;

import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.util.InterpreterUtil;

public class PrintStreamLogger implements IFernflowerLogger {

	private int severity;
	
	private int indent; 
	
	private PrintStream stream;
	
	public PrintStreamLogger(int severity, PrintStream stream) {
		this.severity = severity;
		this.indent = 0; 
		this.stream = stream;
	}
	
	
	public void writeMessage(String message, int severity) {
		writeMessage(message, severity, indent);
	}

	public void writeMessage(String message, int severity, int indent) {
		if(severity >= this.severity) {
			stream.println(InterpreterUtil.getIndentString(indent)+names[severity]+": "+message);
		}
	}
	
	public void startClass(String classname) {
		stream.println(InterpreterUtil.getIndentString(indent++)+"Processing class "+classname+" ...");
	}

	public void endClass() {
		stream.println(InterpreterUtil.getIndentString(--indent)+"... proceeded.");
	}

	public void startWriteClass(String classname) {
		stream.println(InterpreterUtil.getIndentString(indent++)+"Writing class "+classname+" ...");
	}

	public void endWriteClass() {
		stream.println(InterpreterUtil.getIndentString(--indent)+"... written.");
	}
	
	public void startMethod(String method) {
		if(severity <= INFO) {
			stream.println(InterpreterUtil.getIndentString(indent)+"Processing method "+method+" ...");
		}
	}

	public void endMethod() {
		if(severity <= INFO) {
			stream.println(InterpreterUtil.getIndentString(indent)+"... proceeded.");
		}
	}

	public int getSeverity() {
		return severity;
	}

	public void setSeverity(int severity) {
		this.severity = severity;
	}

	public boolean getShowStacktrace() {
		return true;
	}
}
