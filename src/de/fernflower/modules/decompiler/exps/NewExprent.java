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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.ClassWriter;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.vars.CheckTypesResult;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.StructClass;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;
import de.fernflower.util.ListStack;

public class NewExprent extends Exprent {

	private InvocationExprent constructor;
	
	private VarType newtype;
	
	private List<Exprent> lstDims = new ArrayList<Exprent>();
	
	private List<Exprent> lstArrayElements = new ArrayList<Exprent>();
	
	private boolean directArrayInit;
	
	private boolean anonymous;

	private boolean lambda;
	
	private boolean enumconst;
	
	{
		this.type = EXPRENT_NEW;
	}
	
	public NewExprent(VarType newtype, ListStack<Exprent> stack, int arraydim) {
		this.newtype = newtype;
		for(int i=0;i<arraydim;i++) {
			lstDims.add(0, stack.pop());
		}
		
		setAnonymous();
	}

	public NewExprent(VarType newtype, List<Exprent> lstDims) {
		this.newtype = newtype;
		this.lstDims = lstDims;
		
		setAnonymous();
	}
	
	private void setAnonymous() {
		
		anonymous = false;
		lambda = false;

		if(newtype.type == CodeConstants.TYPE_OBJECT && newtype.arraydim == 0) {
			ClassNode node = DecompilerContext.getClassprocessor().getMapRootClasses().get(newtype.value); 
			
			if(node != null && (node.type == ClassNode.CLASS_ANONYMOUS || node.type == ClassNode.CLASS_LAMBDA)) {
				anonymous = true;
				
				if(node.type == ClassNode.CLASS_LAMBDA) {
					lambda = true;
				}
			}
		}
	}
	
	public VarType getExprType() {
		
		if(anonymous) {
			ClassNode node = DecompilerContext.getClassprocessor().getMapRootClasses().get(newtype.value); 

			return node.anonimousClassType;
		} else {
			return newtype;
		}
	}
	
	public CheckTypesResult checkExprTypeBounds() {
		CheckTypesResult result = new CheckTypesResult();

		if(newtype.arraydim != 0) {
			for(Exprent dim: lstDims) {
				result.addMinTypeExprent(dim, VarType.VARTYPE_BYTECHAR);
				result.addMaxTypeExprent(dim, VarType.VARTYPE_INT);
			}

			if(newtype.arraydim == 1) {

				VarType leftType = newtype.copy();
				leftType.decArrayDim();
				
				for(Exprent element: lstArrayElements) {
					result.addMinTypeExprent(element, VarType.getMinTypeInFamily(leftType.type_family));
					result.addMaxTypeExprent(element, leftType);
				}
			}
		} else {
			if(constructor != null) {
				return constructor.checkExprTypeBounds();
			}
		}	
		
		return result;
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		if(newtype.arraydim == 0) {
			if(constructor != null) {
				lst.addAll(constructor.getLstParameters());
			}
		} else {
			lst.addAll(lstDims);
			lst.addAll(lstArrayElements);
		}
		
		return lst;
	}
	
	public Exprent copy() {
		List<Exprent> lst = new ArrayList<Exprent>();
		for(Exprent expr: lstDims) {
			lst.add(expr.copy());
		}

		NewExprent ret = new NewExprent(newtype, lst);
		ret.setConstructor(constructor==null?null:(InvocationExprent)constructor.copy());
		ret.setLstArrayElements(lstArrayElements);
		ret.setDirectArrayInit(directArrayInit);
		ret.setAnonymous(anonymous);
		ret.setEnumconst(enumconst);
		return ret;
	}
	
	public int getPrecedence() {
		return 1; // precedence of new
	}
	
	public String toJava(int indent) {
		StringBuffer buf = new StringBuffer();
		
		if(anonymous) {
			
			ClassNode child = DecompilerContext.getClassprocessor().getMapRootClasses().get(newtype.value); 

			buf.append("(");
			
			if(!lambda && constructor != null) {
				
				InvocationExprent invsuper = child.superInvocation;
				
				ClassNode newnode = DecompilerContext.getClassprocessor().getMapRootClasses().get(invsuper.getClassname()); 

				List<VarVersionPaar> sigFields = null;
				if(newnode != null) {  // own class
					if(newnode.wrapper != null) {
						sigFields = newnode.wrapper.getMethodWrapper("<init>", invsuper.getStringDescriptor()).signatureFields;
					} else {
						if(newnode.type == ClassNode.CLASS_MEMBER && (newnode.access & CodeConstants.ACC_STATIC) == 0 && 
								!constructor.getLstParameters().isEmpty()) { // member non-static class invoked with enclosing class instance
							sigFields = new ArrayList<VarVersionPaar>(Collections.nCopies(constructor.getLstParameters().size(), (VarVersionPaar)null));
							sigFields.set(0, new VarVersionPaar(-1, 0));
						}
					}
				}
				
				boolean firstpar = true;
				for(int i=0;i<invsuper.getLstParameters().size();i++) {
					if(sigFields == null || sigFields.get(i) == null) {
						if(!firstpar) {
							buf.append(", ");
						}
						
						Exprent param = invsuper.getLstParameters().get(i);
						if(param.type == Exprent.EXPRENT_VAR) {
							int varindex = ((VarExprent)param).getIndex();
							if(varindex > 0 && varindex <= constructor.getLstParameters().size()) {
								param = constructor.getLstParameters().get(varindex-1);
							}
						}
						
						StringBuilder buff = new StringBuilder();
						ExprProcessor.getCastedExprent(param, invsuper.getDescriptor().params[i], buff, indent, true);
						
						buf.append(buff);
						firstpar = false;
					}
				}

			}

			if(!enumconst) {
				String enclosing = null;
				if(!lambda && constructor != null) {
					enclosing = getQualifiedNewInstance(child.anonimousClassType.value, constructor.getLstParameters(), indent);
				}
				
				String typename = ExprProcessor.getCastTypeName(child.anonimousClassType);
				
				if(enclosing != null) {
					ClassNode anonimousNode = DecompilerContext.getClassprocessor().getMapRootClasses().get(child.anonimousClassType.value);
					if(anonimousNode != null) {
						typename = anonimousNode.simpleName; 
					} else {
						typename = typename.substring(typename.lastIndexOf('.')+1);
					}
				}
				buf.insert(0, "new "+typename);
				
				if(enclosing != null) {
					buf.insert(0, enclosing+".");
				}
			}
			
			buf.append(")");
			
			StringWriter strwriter = new StringWriter();
			BufferedWriter bufstrwriter = new BufferedWriter(strwriter);
			
			ClassWriter clwriter = new ClassWriter();
			try {
				if(lambda) {
					clwriter.classLambdaToJava(child, bufstrwriter, indent);
				} else {
					clwriter.classToJava(child, bufstrwriter, indent);
				}
				bufstrwriter.flush();
			} catch(IOException ex) {
				throw new RuntimeException(ex);
			}
			
			if(lambda && !DecompilerContext.getOption(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS)) {
				buf.setLength(0); // remove the usual 'new <class>()', it will be replaced with lambda style '() ->'  
			}
			
			buf.append(strwriter.toString());
			
		} else if(directArrayInit) {
			VarType leftType = newtype.copy();
			leftType.decArrayDim();
			
			buf.append("{");
			for(int i=0;i<lstArrayElements.size();i++) {
				if(i>0) {
					buf.append(", ");
				}
				StringBuilder buff = new StringBuilder();
				ExprProcessor.getCastedExprent(lstArrayElements.get(i), leftType, buff, indent, false);
				
				buf.append(buff);
			}
			buf.append("}");
		} else {
			if(newtype.arraydim == 0) {
				
				if(constructor != null) {

					List<Exprent> lstParameters = constructor.getLstParameters();
					
					ClassNode newnode = DecompilerContext.getClassprocessor().getMapRootClasses().get(constructor.getClassname()); 
					
					List<VarVersionPaar> sigFields = null;
					if(newnode != null) {  // own class
						if(newnode.wrapper != null) {
							sigFields = newnode.wrapper.getMethodWrapper("<init>", constructor.getStringDescriptor()).signatureFields;
						} else {
							if(newnode.type == ClassNode.CLASS_MEMBER && (newnode.access & CodeConstants.ACC_STATIC) == 0 &&
										!constructor.getLstParameters().isEmpty()) { // member non-static class invoked with enclosing class instance
								sigFields = new ArrayList<VarVersionPaar>(Collections.nCopies(lstParameters.size(), (VarVersionPaar)null));
								sigFields.set(0, new VarVersionPaar(-1, 0));
							}
						}
					}
					
					buf.append("(");
					
					boolean firstpar = true;
					for(int i=0;i<lstParameters.size();i++) {
						if(sigFields == null || sigFields.get(i) == null) {
							if(!firstpar) {
								buf.append(", ");
							}
							
							StringBuilder buff = new StringBuilder();
							ExprProcessor.getCastedExprent(lstParameters.get(i), constructor.getDescriptor().params[i], buff, indent, true);
							
							buf.append(buff);
							firstpar = false;
						}
					}
					buf.append(")");
				}
				
				if(!enumconst) {
					String enclosing = null;
					if(constructor != null) {
						enclosing = getQualifiedNewInstance(newtype.value, constructor.getLstParameters(), indent);
					}
					
					String typename = ExprProcessor.getTypeName(newtype);
					
					if(enclosing != null) {
						ClassNode newNode = DecompilerContext.getClassprocessor().getMapRootClasses().get(newtype.value);
						if(newNode != null) {
							typename = newNode.simpleName; 
						} else {
							typename = typename.substring(typename.lastIndexOf('.')+1);
						}
					}
					buf.insert(0, "new "+typename);
					
					if(enclosing != null) {
						buf.insert(0, enclosing+".");
					}
				}
				
			} else {
				buf.append("new "+ExprProcessor.getTypeName(newtype));

				if(lstArrayElements.isEmpty()) {
	 				for(int i=0;i<newtype.arraydim;i++) {
						buf.append("["+(i<lstDims.size()?lstDims.get(i).toJava(indent):"")+"]");
					}
				} else {
	 				for(int i=0;i<newtype.arraydim;i++) {
						buf.append("[]");
					}
	 				
	 				VarType leftType = newtype.copy();
	 				leftType.decArrayDim();
	 				
	 				buf.append("{");
					for(int i=0;i<lstArrayElements.size();i++) {
						if(i>0) {
							buf.append(", ");
						}
						StringBuilder buff = new StringBuilder();
						ExprProcessor.getCastedExprent(lstArrayElements.get(i), leftType, buff, indent, false);
						
						buf.append(buff);
					}
					buf.append("}");
				}
			}
		}
		return buf.toString();
	}
	
	private String getQualifiedNewInstance(String classname, List<Exprent> lstParams, int indent) {

		ClassNode node = DecompilerContext.getClassprocessor().getMapRootClasses().get(classname); 
		
		if(node != null && node.type != ClassNode.CLASS_ROOT && (node.access & CodeConstants.ACC_STATIC) == 0) {
			if(!lstParams.isEmpty()) {
				Exprent enclosing = lstParams.get(0);
				
				boolean isQualifiedNew = false;
				
				if(enclosing.type == Exprent.EXPRENT_VAR) {
					VarExprent varEnclosing = (VarExprent)enclosing;
					
					StructClass current_class = ((ClassNode)DecompilerContext.getProperty(DecompilerContext.CURRENT_CLASSNODE)).classStruct;
					String this_classname = varEnclosing.getProcessor().getThisvars().get(new VarVersionPaar(varEnclosing));

					if(!current_class.qualifiedName.equals(this_classname)) {
						isQualifiedNew = true;
					}
				} else {
					isQualifiedNew = true;
				}
				
				if(isQualifiedNew) {
					return enclosing.toJava(indent);
				}
			}
		}
		
		return null;
	}

	public boolean equals(Object o) {
		if(o!=null && o instanceof NewExprent) {
			NewExprent ne = (NewExprent)o;
			
			return InterpreterUtil.equalObjects(newtype, ne.getNewtype()) && 
					InterpreterUtil.equalLists(lstDims, ne.getLstDims()) &&
					InterpreterUtil.equalObjects(constructor, ne.getConstructor()) &&
					directArrayInit == ne.directArrayInit &&
					InterpreterUtil.equalLists(lstArrayElements, ne.getLstArrayElements());
			
		}
		return false;
	}
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == constructor) {
			constructor = (InvocationExprent)newexpr;
		} 
		
		if(constructor != null) {
			constructor.replaceExprent(oldexpr, newexpr);
		}
		
		for(int i=0;i<lstDims.size();i++) {
			if(oldexpr == lstDims.get(i)) {
				lstDims.set(i, newexpr);
			} 
		}
		
		for(int i=0;i<lstArrayElements.size();i++) {
			if(oldexpr == lstArrayElements.get(i)) {
				lstArrayElements.set(i, newexpr);
			} 
		}
	}
	
	public InvocationExprent getConstructor() {
		return constructor;
	}

	public void setConstructor(InvocationExprent constructor) {
		this.constructor = constructor;
	}

	public List<Exprent> getLstDims() {
		return lstDims;
	}

	public VarType getNewtype() {
		return newtype;
	}

	public List<Exprent> getLstArrayElements() {
		return lstArrayElements;
	}

	public void setLstArrayElements(List<Exprent> lstArrayElements) {
		this.lstArrayElements = lstArrayElements;
	}

	public boolean isDirectArrayInit() {
		return directArrayInit;
	}

	public void setDirectArrayInit(boolean directArrayInit) {
		this.directArrayInit = directArrayInit;
	}

	public boolean isLambda() {
		return lambda;
	}

	public boolean isAnonymous() {
		return anonymous;
	}

	public void setAnonymous(boolean anonymous) {
		this.anonymous = anonymous;
	}

	public boolean isEnumconst() {
		return enumconst;
	}

	public void setEnumconst(boolean enumconst) {
		this.enumconst = enumconst;
	}

}
