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

package de.fernflower.modules.decompiler.exps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.rels.MethodWrapper;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.vars.CheckTypesResult;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.StructMethod;
import de.fernflower.struct.consts.LinkConstant;
import de.fernflower.struct.gen.MethodDescriptor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;
import de.fernflower.util.ListStack;


public class InvocationExprent extends Exprent {

	public static final int INVOKE_SPECIAL = 1;
	public static final int INVOKE_VIRTUAL = 2;
	public static final int INVOKE_STATIC = 3;
	public static final int INVOKE_INTERFACE = 4;
	public static final int INVOKE_DYNAMIC = 5;
	
	public static final int TYP_GENERAL = 1;
	public static final int TYP_INIT = 2;
	public static final int TYP_CLINIT = 3;
	
	public static final int CONSTRUCTOR_NOT = 0;
	public static final int CONSTRUCTOR_THIS = 1;
	public static final int CONSTRUCTOR_SUPER = 2;
	
	private String name;
	
	private String classname;
	
	private boolean isStatic;
	
	private int functype = TYP_GENERAL;
	
	private Exprent instance;
	
	private MethodDescriptor descriptor;

	private String stringDescriptor;
	
	private int invocationTyp = INVOKE_VIRTUAL; 
	
	private List<Exprent> lstParameters = new ArrayList<Exprent>();

	{
		this.type = EXPRENT_INVOCATION;
	}
	
	public InvocationExprent() {}
			
	public InvocationExprent(int opcode, LinkConstant cn, ListStack<Exprent> stack) {
		
		name = cn.elementname;
		classname = cn.classname;
		
		switch(opcode) {
		case CodeConstants.opc_invokestatic:
			invocationTyp = INVOKE_STATIC;
			break;
		case CodeConstants.opc_invokespecial:
			invocationTyp = INVOKE_SPECIAL;
			break;
		case CodeConstants.opc_invokevirtual:
			invocationTyp = INVOKE_VIRTUAL;
			break;
		case CodeConstants.opc_invokeinterface:
			invocationTyp = INVOKE_INTERFACE;
			break;
		case CodeConstants.opc_invokedynamic:
			invocationTyp = INVOKE_DYNAMIC;
			classname = "java/lang/Class"; // dummy class name
		}
		
		if("<init>".equals(name)) {
			functype = TYP_INIT;
		}else if("<clinit>".equals(name)) {
			functype = TYP_CLINIT;
		}
	
		stringDescriptor = cn.descriptor;
		descriptor = MethodDescriptor.parseDescriptor(cn.descriptor);
		
		for(int i=0;i<descriptor.params.length;i++) {
			lstParameters.add(0, stack.pop());
		}
		
		if(opcode == CodeConstants.opc_invokestatic || opcode == CodeConstants.opc_invokedynamic) {
			isStatic = true;
		} else {
			instance = stack.pop(); 
		}
	}

	private InvocationExprent(InvocationExprent expr) {
		name = expr.getName();
		classname = expr.getClassname();
		isStatic = expr.isStatic();
		functype = expr.getFunctype();
		instance = expr.getInstance();
		if(instance != null) {
			instance = instance.copy();
		}
		invocationTyp = expr.getInvocationTyp();
		stringDescriptor = expr.getStringDescriptor();
		descriptor = expr.getDescriptor();
		lstParameters = new ArrayList<Exprent>(expr.getLstParameters());
		for(int i=0;i<lstParameters.size();i++) {
			lstParameters.set(i, lstParameters.get(i).copy());
		}
	}
	
	
	public VarType getExprType() {
		return descriptor.ret;
	}
	
	public CheckTypesResult checkExprTypeBounds() {
		CheckTypesResult result = new CheckTypesResult();

		for(int i=0;i<lstParameters.size();i++) {
			Exprent parameter = lstParameters.get(i);
			
			VarType leftType = descriptor.params[i];

			result.addMinTypeExprent(parameter, VarType.getMinTypeInFamily(leftType.type_family));
			result.addMaxTypeExprent(parameter, leftType);
		}
		
		return result;
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		if(instance != null) {
			lst.add(instance);
		}
		lst.addAll(lstParameters);
		return lst;
	}
	
	
	public Exprent copy() {
		return new InvocationExprent(this);
	}
	
	public String toJava(int indent) {
		StringBuilder buf = new StringBuilder("");

		String super_qualifier = null;
		boolean isInstanceThis = false;
		
		if(isStatic) {
			if(invocationTyp != INVOKE_DYNAMIC) {
				ClassNode node = (ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE);
				if(node == null || !classname.equals(node.classStruct.qualifiedName)) {
					buf.append(DecompilerContext.getImpcollector().getShortName(ExprProcessor.buildJavaClassName(classname)));
				}
			}
		} else {

			if(instance != null && instance.type == Exprent.EXPRENT_VAR) {
				VarExprent instvar = (VarExprent)instance;
				VarVersionPaar varpaar = new VarVersionPaar(instvar);
				
				VarProcessor vproc = instvar.getProcessor();
				if(vproc == null) {
					MethodWrapper current_meth = (MethodWrapper)DecompilerContext.getProperty(DecompilerContext.CURRENT_METHOD_WRAPPER);
					if(current_meth != null) {
						vproc = current_meth.varproc;
					}
				}
				
				String this_classname = null;
				if(vproc != null) {
					this_classname = vproc.getThisvars().get(varpaar);
				}
				
				if(this_classname != null) {
					isInstanceThis = true;
					
					if(invocationTyp == INVOKE_SPECIAL) {
						if(!classname.equals(this_classname)) { // TODO: direct comparison to the super class?
							super_qualifier = this_classname;
						}
					}
				}
			}
			
			if(functype == TYP_GENERAL){
				if(super_qualifier != null) {
					StructClass current_class = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE)).classStruct;
					
					if(!super_qualifier.equals(current_class.qualifiedName)) {
						buf.append(DecompilerContext.getImpcollector().getShortName(ExprProcessor.buildJavaClassName(super_qualifier)));
						buf.append(".");
					}
					buf.append("super");
				} else {
					String res = instance.toJava(indent);
					
					VarType rightType = instance.getExprType(); 
					VarType leftType = new VarType(CodeConstants.TYPE_OBJECT, 0, classname);
					
					if(rightType.equals(VarType.VARTYPE_OBJECT) && !leftType.equals(rightType)) {
						buf.append("(("+ExprProcessor.getCastTypeName(leftType)+")");
						
						if(instance.getPrecedence() >= FunctionExprent.getPrecedence(FunctionExprent.FUNCTION_CAST)) {
							res = "("+res+")";
						}
						buf.append(res+")");
					} else if(instance.getPrecedence() > getPrecedence()) {
						buf.append("("+res+")");
					} else {
						buf.append(res);
					}
				}
			}
		}
		
		switch(functype) {
		case TYP_GENERAL:
			if(VarExprent.VAR_NAMELESS_ENCLOSURE.equals(buf.toString())) {
				buf = new StringBuilder("");
			}
			
			if(buf.length() > 0) {
				buf.append(".");
			}
			
			buf.append(name);
			if(invocationTyp == INVOKE_DYNAMIC) {
				buf.append("<invokedynamic>");
			}
			buf.append("(");

			break;
		case TYP_CLINIT:
			throw new RuntimeException("Explicite invocation of <clinit>");
		case TYP_INIT:
			if(super_qualifier != null) {
				buf.append("super(");
			} else if(isInstanceThis) {
				buf.append("this(");
			} else {
				buf.append(instance.toJava(indent));
				buf.append(".<init>(");
//				throw new RuntimeException("Unrecognized invocation of <init>"); // FIXME: activate
			}
		}
		
		List<VarVersionPaar> sigFields = null;
		if(functype == TYP_INIT) {
			ClassNode newnode = DecompilerContext.getClassprocessor().getMapRootClasses().get(classname); 
			
			if(newnode != null) {  // own class
				if(newnode.wrapper != null) {
					sigFields = newnode.wrapper.getMethodWrapper("<init>", stringDescriptor).signatureFields;
				} else {
					if(newnode.type == ClassNode.CLASS_MEMBER && (newnode.access & CodeConstants.ACC_STATIC) == 0) { // non-static member class  
						sigFields = new ArrayList<VarVersionPaar>(Collections.nCopies(lstParameters.size(), (VarVersionPaar)null));
						sigFields.set(0, new VarVersionPaar(-1, 0));
					}
				}
			}
		}
		
		Set<Integer> setAmbiguousParameters = getAmbiguousParameters();
		
		boolean firstpar = true;
		for(int i=0;i<lstParameters.size();i++) {
			if(sigFields == null || sigFields.get(i) == null) {
				if(!firstpar) {
					buf.append(", ");
				}
	
				StringBuilder buff = new StringBuilder();
				ExprProcessor.getCastedExprent(lstParameters.get(i), descriptor.params[i], buff, indent, true, setAmbiguousParameters.contains(i));
				
				buf.append(buff);
				firstpar = false;
			}
		}
		buf.append(")");
		
		return buf.toString();
	}

	private Set<Integer> getAmbiguousParameters() {
		
		Set<Integer> ret = new HashSet<Integer>();
		
		StructClass cstr = DecompilerContext.getStructcontext().getClass(classname);
		if(cstr != null) {
			List<MethodDescriptor> lstMethods = new ArrayList<MethodDescriptor>();
			for(StructMethod meth : cstr.getMethods()) {
				if(name.equals(meth.getName())) {
					MethodDescriptor md = MethodDescriptor.parseDescriptor(meth.getDescriptor());
					if(md.params.length == descriptor.params.length) {
						boolean equals = true;
						for(int i=0;i<md.params.length;i++) {
							if(md.params[i].type_family != descriptor.params[i].type_family) {
								equals = false;
								break;
							}
						}
						
						if(equals) {
							lstMethods.add(md);
						}
					}
				}
			}
			
			if(lstMethods.size() > 1) {
				for(int i=0;i<descriptor.params.length;i++) {
					VarType partype = descriptor.params[i];

					for(MethodDescriptor md : lstMethods) {
						if(!partype.equals(md.params[i])) {
							ret.add(i);
							break;
						}
					}
				}
			}
		}
		
		return ret;
	}
	
	public boolean equals(Object o) {
		if(o!=null && o instanceof InvocationExprent) {
			InvocationExprent it = (InvocationExprent)o;

			return InterpreterUtil.equalObjects(name, it.getName()) &&
					InterpreterUtil.equalObjects(classname, it.getClassname()) && 
					isStatic == it.isStatic() &&
					InterpreterUtil.equalObjects(instance, it.getInstance()) &&
					InterpreterUtil.equalObjects(descriptor, it.getDescriptor()) &&
					functype == it.getFunctype() &&
					InterpreterUtil.equalLists(lstParameters, it.getLstParameters());
					 
		}
		return false;
	}
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == instance) {
			instance = newexpr;
		} 
		
		for(int i=0;i<lstParameters.size();i++) {
			if(oldexpr == lstParameters.get(i)) {
				lstParameters.set(i, newexpr);
			} 
		}
	}
	
	public List<Exprent> getLstParameters() {
		return lstParameters;
	}

	public void setLstParameters(List<Exprent> lstParameters) {
		this.lstParameters = lstParameters;
	}

	public MethodDescriptor getDescriptor() {
		return descriptor;
	}

	public void setDescriptor(MethodDescriptor descriptor) {
		this.descriptor = descriptor;
	}

	public String getClassname() {
		return classname;
	}

	public void setClassname(String classname) {
		this.classname = classname;
	}

	public int getFunctype() {
		return functype;
	}

	public void setFunctype(int functype) {
		this.functype = functype;
	}

	public Exprent getInstance() {
		return instance;
	}

	public void setInstance(Exprent instance) {
		this.instance = instance;
	}

	public boolean isStatic() {
		return isStatic;
	}

	public void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getStringDescriptor() {
		return stringDescriptor;
	}

	public void setStringDescriptor(String stringDescriptor) {
		this.stringDescriptor = stringDescriptor;
	}

	public int getInvocationTyp() {
		return invocationTyp;
	}

	public void setInvocationTyp(int invocationTyp) {
		this.invocationTyp = invocationTyp;
	}	
	
}
