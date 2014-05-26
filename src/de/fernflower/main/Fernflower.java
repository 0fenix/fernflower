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

package de.fernflower.main;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.HashMap;

import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.main.extern.IBytecodeProvider;
import de.fernflower.main.extern.IDecompilatSaver;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.modules.renamer.IdentifierConverter;
import de.fernflower.struct.IDecompiledData;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructContext;
import de.fernflower.struct.lazy.LazyLoader;


public class Fernflower implements IDecompiledData {
	
	public static final String version = "v0.8.4";

	private StructContext structcontext;
	
	private ClassesProcessor clprocessor; 
	
	public Fernflower(IBytecodeProvider provider, IDecompilatSaver saver,
			HashMap<String, Object> propertiesCustom) {

		StructContext context = new StructContext(saver, this, new LazyLoader(provider));
		
		structcontext = context;

		DecompilerContext.initContext(propertiesCustom);
		DecompilerContext.setCountercontainer(new CounterContainer());

	}

	public void decompileContext() {

		if(DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
			IdentifierConverter ren = new IdentifierConverter();
			ren.rename(structcontext);
			ren = null;
		}
		
		clprocessor = new ClassesProcessor(structcontext);
		
		DecompilerContext.setClassprocessor(clprocessor);
		DecompilerContext.setStructcontext(structcontext);
		
		structcontext.saveContext();
	}
	
	public String getClassEntryName(StructClass cl, String entryname) {
		
		ClassNode node = clprocessor.getMapRootClasses().get(cl.qualifiedName);
		if(node.type != ClassNode.CLASS_ROOT) {
			return null;
		} else {
			if(DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
				String simple_classname = cl.qualifiedName.substring(cl.qualifiedName.lastIndexOf('/')+1);
				return entryname.substring(0, entryname.lastIndexOf('/')+1)+simple_classname+".java";
			} else {
				return entryname.substring(0, entryname.lastIndexOf(".class"))+".java";
			}
		}
	}

	public StructContext getStructcontext() {
		return structcontext;
	}

	public String getClassContent(StructClass cl) {
		
		String res = null; 
		
		try {
			StringWriter strwriter = new StringWriter();
			clprocessor.writeClass(structcontext, cl, new BufferedWriter(strwriter));
			
			res = strwriter.toString();
		} catch(ThreadDeath ex) {
			throw ex;
		} catch(Throwable ex) {
			DecompilerContext.getLogger().writeMessage("Class "+cl.qualifiedName+" couldn't be fully decompiled.", ex);
		}
		
		return res;
	}
	
}
