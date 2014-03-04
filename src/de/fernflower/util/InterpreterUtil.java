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

package de.fernflower.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class InterpreterUtil {
	
	public static final String INDENT_STRING = "   ";

	public static void copyFile(File in, File out) throws IOException {
		FileChannel inChannel = new FileInputStream(in).getChannel();
		FileChannel outChannel = new FileOutputStream(out).getChannel();
		try {
			// magic number for Windows, 64Mb - 32Kb)
			int maxCount = (64 * 1024 * 1024) - (32 * 1024);
			long size = inChannel.size();
			long position = 0;
			while (position < size) {
				position += inChannel.transferTo(position, maxCount, outChannel);
			}
		} catch (IOException e) {
			throw e;
		}
		finally {
			if (inChannel != null) {
				inChannel.close();
			}
			if (outChannel != null) {
				outChannel.close();
			}
		}
	}
	
	public static void copyInputStream(InputStream in, OutputStream out)throws IOException {
		
		byte[] buffer = new byte[1024];
		int len;
		
		while((len = in.read(buffer)) >= 0) {
			out.write(buffer, 0, len);
		}
		
	}
	
	public static String getIndentString(int length) {
		StringBuffer buf = new StringBuffer();
		while(length-->0) {
			buf.append(INDENT_STRING);
		}
		return buf.toString(); 
	}
	
	
	public static boolean equalSets(Collection<?> c1, Collection<?> c2) {
		
		if(c1 == null) {
			return c2 == null?true:false;
		} else {
			if(c2 == null) {
				return false;
			}
		}
		
		if(c1.size() != c2.size()) {
			return false;
		}
		
		HashSet<?> set = new HashSet(c1);
		set.removeAll(c2);
		
		return (set.size() == 0); 
	}
	
	public static boolean equalObjects(Object first, Object second) {
		return first==null?second==null:first.equals(second);
	}

	public static boolean equalObjectArrays(Object[] first, Object[] second) {
		
		if(first == null || second == null) {
			return equalObjects(first, second);
		} else {
			if(first.length != second.length) {
				return false;
			}
			
			for(int i=0;i<first.length;i++) {
				if(!equalObjects(first[i], second[i])) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	public static boolean equalLists(List<?> first, List<?> second) {
		
		if(first == null) {
			return second == null;
		} else if(second == null) {
			return first == null;
		}
		
		if(first.size() == second.size()) {
			for(int i=0;i<first.size();i++) {
				if(!equalObjects(first.get(i), second.get(i))) {
					return false;
				}
			}
			return true;
		}

		return false; 
	}
	
	public static String charToUnicodeLiteral(int value) {
		String sTemp = Integer.toHexString(value);
		sTemp = ("0000"+sTemp).substring(sTemp.length());
		
		return "\\u"+sTemp;
	}
	
	public static String makeUniqueKey(String name, String descriptor) {
		return name+" "+descriptor;
	}
	
}
