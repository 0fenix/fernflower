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

import de.fernflower.code.CodeConstants;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.rels.ClassWrapper;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.exps.NewExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructField;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.gen.FieldDescriptor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;

public class EnumProcessor {

	public static void clearEnum(ClassWrapper wrapper) {

		StructClass cl = wrapper.getClassStruct();
		
		// hide values() and valueOf()
		for(StructMethod meth : cl.getMethods()) {
			
			String name = meth.getName(); 
			int flag = 0;
			
			if("values".equals(name)) {
				flag = 1;
			} else if("valueOf".equals(name)) {
				flag = 2;
			}
			
			if(flag>0) {
				String[] arr = meth.getDescriptor().split("[()]");
				String par = arr[1];
				
				if((flag == 1 && par.length() == 0) ||
						flag == 2 && "Ljava/lang/String;".equals(par)) {
					wrapper.getHideMembers().add(InterpreterUtil.makeUniqueKey(name, meth.getDescriptor()));
				}
			}
		}
		
		// hide all super invocations
		for(MethodWrapper meth : wrapper.getMethods()) {
			if("<init>".equals(meth.methodStruct.getName())) {
				Statement firstdata = findFirstData(meth.root);
				if(firstdata == null || firstdata.getExprents().isEmpty()) {
					return;
				}
				
				Exprent exprent = firstdata.getExprents().get(0);
				if(exprent.type == Exprent.EXPRENT_INVOCATION) {
					InvocationExprent invexpr = (InvocationExprent)exprent;
					if(isInvocationSuperConstructor(invexpr, meth, wrapper)) {
						firstdata.getExprents().remove(0);
					}
				}
			}
		}
		
		// hide dummy synthetic fields of enum constants
		for(StructField fd: cl.getFields()) {
			if((fd.access_flags & CodeConstants.ACC_ENUM) != 0) {
				Exprent initializer = wrapper.getStaticFieldInitializers().getWithKey(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
				if(initializer != null && initializer.type == Exprent.EXPRENT_NEW) {
					NewExprent nexpr = (NewExprent)initializer;
					if(nexpr.isAnonymous()) {
						ClassNode child = DecompilerContext.getClassprocessor().getMapRootClasses().get(nexpr.getNewtype().value); 
						hideDummyFieldInConstant(child.wrapper);					}
				}
			}
		}
		
		
		
	}
	
	private static void hideDummyFieldInConstant(ClassWrapper wrapper) {
		
		StructClass cl = wrapper.getClassStruct();
		for(StructField fd: cl.getFields()) {
			if((fd.access_flags & CodeConstants.ACC_SYNTHETIC) != 0) {
				FieldDescriptor descr = FieldDescriptor.parseDescriptor(fd.getDescriptor());
				VarType ret = descr.type;
				
				if(ret.type == CodeConstants.TYPE_OBJECT && ret.arraydim == 1 && cl.qualifiedName.equals(ret.value)) {
					wrapper.getHideMembers().add(InterpreterUtil.makeUniqueKey(fd.getName(), fd.getDescriptor()));
				}
			}
		}
		
	}
	
	// FIXME: move to a util class (see also InitializerProcessor)
	private static Statement findFirstData(Statement stat) {

		if(stat.getExprents() != null) {
			return stat;
		} else {
			if(stat.isLabeled()) {
				return null;
			}
			
			switch(stat.type) {
			case Statement.TYPE_SEQUENCE:
			case Statement.TYPE_IF:
			case Statement.TYPE_ROOT:
			case Statement.TYPE_SWITCH:
			case Statement.TYPE_SYNCRONIZED:
				return findFirstData(stat.getFirst());
			default:
				return null;
			}
		}
	}

	// FIXME: move to util class (see also InitializerProcessor)
	private static boolean isInvocationSuperConstructor(InvocationExprent inv, MethodWrapper meth, ClassWrapper wrapper) {
		
		if(inv.getFunctype() == InvocationExprent.TYP_INIT) {
			if(inv.getInstance().type == Exprent.EXPRENT_VAR) {
				VarExprent instvar = (VarExprent)inv.getInstance();
				VarVersionPaar varpaar = new VarVersionPaar(instvar);
				
				String classname = meth.varproc.getThisvars().get(varpaar);
				
				if(classname!=null) { // any this instance. TODO: Restrict to current class? 
					if(!wrapper.getClassStruct().qualifiedName.equals(inv.getClassname())) {
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
}
