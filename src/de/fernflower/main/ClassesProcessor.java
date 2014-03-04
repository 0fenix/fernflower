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
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.main.collectors.ImportCollector;
import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.main.extern.IIdentifierRenamer;
import de.fernflower.main.rels.ClassWrapper;
import de.fernflower.main.rels.NestedClassProcessor;
import de.fernflower.main.rels.NestedMemberAccess;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructContext;
import de.fernflower.struct.attr.StructInnerClassesAttribute;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;

public class ClassesProcessor {

	private HashMap<String, ClassNode> mapRootClasses = new HashMap<String, ClassNode>();
	
	public ClassesProcessor(StructContext context) {
		
		HashMap<String, Object[]> mapInnerClasses = new HashMap<String, Object[]>(); 
		
		HashMap<String, HashSet<String>> mapNestedClassReferences = new HashMap<String, HashSet<String>>(); 
		HashMap<String, HashSet<String>> mapEnclosingClassReferences = new HashMap<String, HashSet<String>>(); 
		
		HashMap<String, String> mapNewSimpleNames = new HashMap<String, String>();
		
		boolean bDecompileInner = DecompilerContext.getOption(IFernflowerPreferences.DECOMPILE_INNER);
		
		// create class nodes
		for(StructClass cl: context.getClasses().values()) {
			if(cl.isOwn() && !mapRootClasses.containsKey(cl.qualifiedName)) {
				
				if(bDecompileInner) {
					StructInnerClassesAttribute inner = (StructInnerClassesAttribute)cl.getAttributes().getWithKey("InnerClasses");
					if(inner != null) {

						for(int i=0;i<inner.getClassentries().size();i++) {
							
							int[] entry = inner.getClassentries().get(i);
							String[] strentry = inner.getStringentries().get(i);
							
							Object[] arr = new Object[4]; // arr[0] not used

							String innername = strentry[0];

							// nested class type
							arr[2] = entry[1] == 0?(entry[2]==0?ClassNode.CLASS_ANONYMOUS:ClassNode.CLASS_LOCAL):ClassNode.CLASS_MEMBER;

							// original simple name
							String simpleName = strentry[2];
							String savedName = mapNewSimpleNames.get(innername);
							
							if(savedName != null) {
								simpleName = savedName;
							} else if(simpleName != null && DecompilerContext.getOption(IFernflowerPreferences.RENAME_ENTITIES)) {
								IIdentifierRenamer renamer = DecompilerContext.getPoolInterceptor().getHelper();
								if(renamer.toBeRenamed(IIdentifierRenamer.ELEMENT_CLASS, simpleName, null, null)) {
									simpleName = renamer.getNextClassname(innername, simpleName);
									mapNewSimpleNames.put(innername, simpleName);
								}
							}
							
							arr[1] = simpleName;

							// original access flags
							arr[3] = entry[3];

							// enclosing class
							String enclClassName = null; 
							if(entry[1] != 0) {
								enclClassName = strentry[1];
							} else {
								enclClassName = cl.qualifiedName;
							}

							if(!innername.equals(enclClassName)) {  // self reference
								StructClass enclosing_class = context.getClasses().get(enclClassName);
								if(enclosing_class != null && enclosing_class.isOwn()) { // own classes only
									
									Object[] arrold = mapInnerClasses.get(innername);
									if(arrold == null) {
										mapInnerClasses.put(innername, arr);
									} else {
										if(!InterpreterUtil.equalObjectArrays(arrold, arr)){
											DecompilerContext.getLogger().writeMessage("Inconsistent inner class entries for "+innername+"!", IFernflowerLogger.WARNING);
										}
									}
									
									// reference to the nested class 
									HashSet<String> set = mapNestedClassReferences.get(enclClassName);
									if(set == null) {
										mapNestedClassReferences.put(enclClassName, set = new HashSet<String>());
									}
									set.add(innername);

									// reference to the enclosing class 
									set = mapEnclosingClassReferences.get(innername);
									if(set == null) {
										mapEnclosingClassReferences.put(innername, set = new HashSet<String>());
									}
									set.add(enclClassName);
								}
							}
						}
					}
				}
				
				ClassNode node = new ClassNode(ClassNode.CLASS_ROOT, cl);
				node.access = cl.access_flags;
				mapRootClasses.put(cl.qualifiedName, node);
			}
		}
		
		if(bDecompileInner) {
			
			// connect nested classes
			for(Entry<String, ClassNode> ent: mapRootClasses.entrySet()) {
				// root class?
				if(!mapInnerClasses.containsKey(ent.getKey())) {
					
					HashSet<String> setVisited = new HashSet<String>();
					LinkedList<String> stack = new LinkedList<String>();
					
					stack.add(ent.getKey());
					setVisited.add(ent.getKey());
					
					while(!stack.isEmpty()) {
						
						String superClass = stack.removeFirst();
						ClassNode supernode = mapRootClasses.get(superClass);
						
						HashSet<String> setNestedClasses = mapNestedClassReferences.get(superClass);
						if(setNestedClasses != null) {
							for(String nestedClass : setNestedClasses) {
								
								if(setVisited.contains(nestedClass)) {
									continue;
								}
								setVisited.add(nestedClass);
								
								ClassNode nestednode = mapRootClasses.get(nestedClass);
								if(nestednode == null) {
									DecompilerContext.getLogger().writeMessage("Nested class "+nestedClass+" missing!", IFernflowerLogger.WARNING);
									continue;
								}

								Object[] arr = mapInnerClasses.get(nestedClass);
								
								if((Integer)arr[2] == ClassNode.CLASS_MEMBER) {
									// FIXME: check for consistent naming
								}
								
								nestednode.type = (Integer)arr[2];
								nestednode.simpleName = (String)arr[1];
								nestednode.access = (Integer)arr[3];

								if(nestednode.type == ClassNode.CLASS_ANONYMOUS) {
									StructClass cl = nestednode.classStruct;
									
									// remove static if anonymous class 
									// a common compiler bug
									nestednode.access &= ~CodeConstants.ACC_STATIC;
									
									int[] interfaces = cl.getInterfaces();
									
									if(interfaces.length > 0) {
										if(interfaces.length > 1) {
											throw new RuntimeException("Inconsistent anonymous class definition: "+cl.qualifiedName);
										}
										nestednode.anonimousClassType = new VarType(cl.getInterface(0), true);
									} else {
										nestednode.anonimousClassType = new VarType(cl.superClass.getString(), true);
									}
								} else if(nestednode.type == ClassNode.CLASS_LOCAL) {
									// only abstract and final are permitted
									// a common compiler bug
									nestednode.access &= (CodeConstants.ACC_ABSTRACT | CodeConstants.ACC_FINAL);
								}
								
								supernode.nested.add(nestednode);
								nestednode.parent = supernode;
								
								nestednode.enclosingClasses.addAll(mapEnclosingClassReferences.get(nestedClass));
								
								stack.add(nestedClass);
							}
						}
					}
				}
			}
			
		}
		
	}
	
	
	public void writeClass(StructContext context, StructClass cl, BufferedWriter outwriter) throws IOException {
		
		ClassNode root = mapRootClasses.get(cl.qualifiedName);
		if(root.type != ClassNode.CLASS_ROOT) {
			return;
		}
		
		try {
			DecompilerContext.setImpcollector(new ImportCollector(root));
			DecompilerContext.setCountercontainer(new CounterContainer());
			
			// add simple class names to implicit import
			addClassnameToImport(root, DecompilerContext.getImpcollector());
			// build wrappers for all nested classes
			// that's where the actual processing takes place
			initWrappers(root);
			
			NestedClassProcessor nestedproc = new NestedClassProcessor();
			nestedproc.processClass(root, root);
			
			NestedMemberAccess nstmember = new NestedMemberAccess();
			nstmember.propagateMemberAccess(root);
			
			ClassWriter clwriter = new ClassWriter();
			
			StringWriter strwriter = new StringWriter();
			clwriter.classToJava(root, new BufferedWriter(strwriter), 0);
			
			if(DecompilerContext.getOption(IFernflowerPreferences.OUTPUT_COPYRIGHT_COMMENT)) {
				outwriter.write("// Decompiled by:       Fernflower "+Fernflower.version);
				outwriter.newLine();
				outwriter.write("// Date:                "+new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()));
				outwriter.newLine();
				outwriter.write("// Copyright:           2008-2010, Stiver");
				outwriter.newLine();
				outwriter.write("// Home page:           http://www.reversed-java.com");
				outwriter.newLine();
				outwriter.newLine();
			}
			
			int index = cl.qualifiedName.lastIndexOf("/");
			if(index >= 0) {
				String strpackage = cl.qualifiedName.substring(0, index).replaceAll("/",".");
				
				outwriter.write("package "+strpackage+";");
				outwriter.newLine();
				outwriter.newLine();
			}
			
			DecompilerContext.setProperty(DecompilerContext.CURRENT_CLASSNODE, root);

			DecompilerContext.getImpcollector().writeImports(outwriter);
			outwriter.newLine();
			
			outwriter.write(strwriter.toString());
			outwriter.flush();
			
		} finally {
			destroyWrappers(root);
		}
	}
	
	private void initWrappers(ClassNode node) throws IOException {
		
		ClassWrapper wrapper = new ClassWrapper(node.classStruct);
		wrapper.init();
		
		node.wrapper = wrapper;
		
		for(ClassNode nd: node.nested) {
			initWrappers(nd);
		}
	}
	
	private void addClassnameToImport(ClassNode node, ImportCollector imp) {
		
		if(node.simpleName != null && node.simpleName.length() > 0) {
			imp.getShortName(node.type == ClassNode.CLASS_ROOT?node.classStruct.qualifiedName:node.simpleName, false);
		}

		for(ClassNode nd: node.nested) {
			addClassnameToImport(nd, imp);
		}
	}
	
	private void destroyWrappers(ClassNode node) {
		
		node.wrapper = null;
		node.classStruct.releaseResources();
		
		for(ClassNode nd: node.nested) {
			destroyWrappers(nd);
		}
	}

	public HashMap<String, ClassNode> getMapRootClasses() {
		return mapRootClasses;
	}
	
	
	public class ClassNode {
		
		public static final int CLASS_ROOT = 0;
		public static final int CLASS_MEMBER = 1;
		public static final int CLASS_ANONYMOUS = 2;
		public static final int CLASS_LOCAL = 4;
		
		public int type;
		
		public int access;
		
		public String simpleName;
		
		public StructClass classStruct;
		
		public ClassWrapper wrapper;
		
		public String enclosingMethod;
		
		public InvocationExprent superInvocation;
		
		public HashMap<String, VarVersionPaar> mapFieldsToVars = new HashMap<String, VarVersionPaar>(); 
		
		public VarType anonimousClassType;

		public List<ClassNode> nested = new ArrayList<ClassNode>();
		
		public Set<String> enclosingClasses = new HashSet<String>();
		
		public ClassNode parent;
		
		public ClassNode(int type, StructClass classStruct) {
			this.type = type;
			this.classStruct = classStruct;
			
			simpleName = classStruct.qualifiedName.substring(classStruct.qualifiedName.lastIndexOf('/')+1);
		}
		
		public ClassNode getClassNode(String qualifiedName) {
			for(ClassNode node : nested) {
				if(qualifiedName.equals(node.classStruct.qualifiedName)) {
					return node;
				}
			}
			return null;
		}

	}
}
