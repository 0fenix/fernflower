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

package de.fernflower.modules.decompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.ClassesProcessor.ClassNode;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.modules.decompiler.exps.ArrayExprent;
import de.fernflower.modules.decompiler.exps.AssignmentExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.ExitExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FunctionExprent;
import de.fernflower.modules.decompiler.exps.IfExprent;
import de.fernflower.modules.decompiler.exps.InvocationExprent;
import de.fernflower.modules.decompiler.exps.MonitorExprent;
import de.fernflower.modules.decompiler.exps.NewExprent;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.sforms.SSAConstructorSparseEx;
import de.fernflower.modules.decompiler.stats.IfStatement;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.modules.decompiler.vars.VarVersionPaar;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.FastSparseSetFactory.FastSparseSet;

public class SimplifyExprentsHelper {

	private boolean firstInvocation;
	
	public SimplifyExprentsHelper(boolean firstInvocation) {
		this.firstInvocation = firstInvocation;
	}
	
	public boolean simplifyStackVarsStatement(Statement stat, HashSet<Integer> setReorderedIfs, SSAConstructorSparseEx ssa) {
		
		boolean res = false;
		
		if(stat.getExprents() == null) {

			for(;;) {
				
				boolean changed = false;
				
				for(Statement st: stat.getStats()) {
					res |= simplifyStackVarsStatement(st, setReorderedIfs, ssa);
					
					// collapse composed if's
					if(changed = IfHelper.mergeIfs(st, setReorderedIfs)) {
						break;
					}
					
					// collapse iff ?: statement 
					if(changed = buildIff(st, ssa)) {
						break;
					}
				}
				
				res |= changed;
				
				if(!changed) {
					break;
				}
			}
			
		} else {
			res |= simplifyStackVarsExprents(stat.getExprents());
		}
		
		return res;
	}
	
	private boolean simplifyStackVarsExprents(List<Exprent> list) {
		
		boolean res = false;
		
		int index = 0;
		
		while(index < list.size()) {
			
			Exprent current = list.get(index);
			
			Exprent ret = isSimpleConstructorInvocation(current);
			if(ret != null) {
				list.set(index, ret);
				res = true;
				
				continue;
			}

			// remove monitor exit
			if(isMonitorExit(current)) {
				list.remove(index);
				res = true;
				
				continue;
			}
			
			// trivial assignment of a stack variable
			if(isTrivialStackAssignment(current)) {
				list.remove(index);
				res = true;
				
				continue;
			}
			
			if(index == list.size()-1) {
				break;
			}
			
			
			Exprent next = list.get(index+1);
			
			
			// constructor invocation
			if(isConstructorInvocationRemote(list, index)) {
				list.remove(index);
				res = true;
				
				continue;
			}
			
			// remove getClass() invocation, which is part of a qualified new 
			if(DecompilerContext.getOption(IFernflowerPreferences.REMOVE_GETCLASS_NEW)) {
				if(isQualifiedNewGetClass(current, next)) {
					list.remove(index);
					res = true;
					
					continue;
				}
			}
			
			// direct initialization of an array
			int arrcount = isArrayInitializer(list, index);
			if(arrcount > 0) {
				for(int i=0;i<arrcount;i++) {
					list.remove(index+1);
				}
				res = true;
				
				continue;
 			}

			// add array initializer expression
			if(addArrayInitializer(current, next)) {
				list.remove(index+1);
				res = true;
				
				continue;
			}
			
			// integer ++expr and --expr  (except for vars!)
			Exprent func = isPPIorMMI(current);
			if(func != null) {
				list.set(index, func);
				res = true;
				
				continue;
			}
			
			// expr++ and expr--
			if(isIPPorIMM(current, next)) {
				list.remove(index+1);
				res = true;
				
				continue;
			}
			
			// assignment on stack
			if(isStackAssignement(current, next)) {
				list.remove(index+1);
				res = true;
				
				continue;
			}

			if(!firstInvocation && isStackAssignement2(current, next)) {
				list.remove(index+1);
				res = true;
				
				continue;
			}
			
			index++;
		}
		
		return res;
	}

	private static boolean addArrayInitializer(Exprent first, Exprent second) {

		if(first.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent as = (AssignmentExprent)first;
			
			if(as.getRight().type == Exprent.EXPRENT_NEW && as.getLeft().type == Exprent.EXPRENT_VAR) {
				NewExprent newex = (NewExprent)as.getRight();

				if(!newex.getLstArrayElements().isEmpty()) {
				
					VarExprent arrvar = (VarExprent)as.getLeft();
					
					if(second.type == Exprent.EXPRENT_ASSIGNMENT) {
						AssignmentExprent aas = (AssignmentExprent)second;
						if(aas.getLeft().type == Exprent.EXPRENT_ARRAY) {
							ArrayExprent arrex = (ArrayExprent)aas.getLeft();
							if(arrex.getArray().type == Exprent.EXPRENT_VAR && arrvar.equals(arrex.getArray()) 
									&& arrex.getIndex().type == Exprent.EXPRENT_CONST) {
								
								int constvalue = ((ConstExprent)arrex.getIndex()).getIntValue();
					
								if(constvalue < newex.getLstArrayElements().size()) {
									Exprent init = newex.getLstArrayElements().get(constvalue);
									if(init.type == Exprent.EXPRENT_CONST) {
										ConstExprent cinit = (ConstExprent)init;
										
										VarType arrtype = newex.getNewtype().copy();
										arrtype.decArrayDim();
										
										ConstExprent defaultval = ExprProcessor.getDefaultArrayValue(arrtype);
										
										if(cinit.equals(defaultval)) {

											Exprent tempexpr = aas.getRight();
											
											if(!tempexpr.containsExprent(arrvar)) {
												newex.getLstArrayElements().set(constvalue, tempexpr);
												
												if(tempexpr.type == Exprent.EXPRENT_NEW) {
													NewExprent tempnewex = (NewExprent)tempexpr;
													int dims = newex.getNewtype().arraydim;
													if(dims > 1 && !tempnewex.getLstArrayElements().isEmpty()) {
														tempnewex.setDirectArrayInit(true);
													}
												}
												
												return true;
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		return false;
	}
	
	
	
	private static int isArrayInitializer(List<Exprent> list, int index) {
		
		Exprent current = list.get(index);
		if(current.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent as = (AssignmentExprent)current;
			
			if(as.getRight().type == Exprent.EXPRENT_NEW && as.getLeft().type == Exprent.EXPRENT_VAR) {
				NewExprent newex = (NewExprent)as.getRight();
				
				if(newex.getExprType().arraydim > 0 && newex.getLstDims().size() == 1 && newex.getLstArrayElements().isEmpty() &&
						newex.getLstDims().get(0).type == Exprent.EXPRENT_CONST) {
					
					int size = ((Integer)((ConstExprent)newex.getLstDims().get(0)).getValue()).intValue();
					if(size == 0) {
						return 0;
					}
					
					VarExprent arrvar = (VarExprent)as.getLeft();
					
					HashMap<Integer, Exprent> mapInit = new HashMap<Integer, Exprent>(); 
					
					int i=1;
					while(index+i < list.size() && i <= size) {
						boolean found = false;
						
						Exprent expr = list.get(index+i);
						if(expr.type == Exprent.EXPRENT_ASSIGNMENT) {
							AssignmentExprent aas = (AssignmentExprent)expr;
							if(aas.getLeft().type == Exprent.EXPRENT_ARRAY) {
								ArrayExprent arrex = (ArrayExprent)aas.getLeft();
								if(arrex.getArray().type == Exprent.EXPRENT_VAR && arrvar.equals(arrex.getArray()) 
										&& arrex.getIndex().type == Exprent.EXPRENT_CONST) {
									
									int constvalue = ((ConstExprent)arrex.getIndex()).getIntValue(); // TODO: check for a number type. Failure extremely improbable, but nevertheless...
									
									if(constvalue < size && !mapInit.containsKey(constvalue)) {
										
										if(!aas.getRight().containsExprent(arrvar)) {
											mapInit.put(constvalue, aas.getRight());
											found = true;
										}
									}
								}
							}
						}
						
						if(!found) {
							break;
						}
						
						i++;
					}

					double fraction = ((double)mapInit.size()) / size; 
					
					if((arrvar.isStack() && fraction > 0) || (size <= 7 && fraction >= 0.3) || 
							(size > 7 && fraction >= 0.7)) {
						
						List<Exprent> lstRet = new ArrayList<Exprent>();
						
						VarType arrtype = newex.getNewtype().copy();
						arrtype.decArrayDim();

						ConstExprent defaultval = ExprProcessor.getDefaultArrayValue(arrtype);
						
						for(int j=0;j<size;j++) {
							lstRet.add(defaultval.copy());
						}
						
						int dims = newex.getNewtype().arraydim;
						for(Entry<Integer, Exprent> ent: mapInit.entrySet()) {
							Exprent tempexpr = ent.getValue();
							lstRet.set(ent.getKey(), tempexpr);

							if(tempexpr.type == Exprent.EXPRENT_NEW) {
								NewExprent tempnewex = (NewExprent)tempexpr;
								if(dims > 1 && !tempnewex.getLstArrayElements().isEmpty()) {
									tempnewex.setDirectArrayInit(true);
								}
							}
						}
						
						newex.setLstArrayElements(lstRet);
						
						return mapInit.size();
					}
				}
			}
		}
 		
		return 0;
	}
	
	private static boolean isTrivialStackAssignment(Exprent first) {
		
		if(first.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent asf = (AssignmentExprent)first;
			
			if(asf.getLeft().type == Exprent.EXPRENT_VAR && asf.getRight().type == Exprent.EXPRENT_VAR) {
				VarExprent varleft = (VarExprent)asf.getLeft(); 
				VarExprent varright = (VarExprent)asf.getRight(); 
				
				if(varleft.getIndex() == varright.getIndex() && varleft.isStack() &&
						varright.isStack()) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	private static boolean isStackAssignement2(Exprent first, Exprent second) {  // e.g. 1.4-style class invocation
		
		if(first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent asf = (AssignmentExprent)first;
			AssignmentExprent ass = (AssignmentExprent)second;
			
			if(asf.getLeft().type == Exprent.EXPRENT_VAR && ass.getRight().type == Exprent.EXPRENT_VAR &&
					asf.getLeft().equals(ass.getRight()) && ((VarExprent)asf.getLeft()).isStack()) {
				if(ass.getLeft().type != Exprent.EXPRENT_VAR || !((VarExprent)ass.getLeft()).isStack()) {
					asf.setRight(new AssignmentExprent(ass.getLeft(), asf.getRight()));
					return true;
				}
			}
		}
		
		return false;
	}
	
	private static boolean isStackAssignement(Exprent first, Exprent second) {
		
		if(first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent asf = (AssignmentExprent)first;
			AssignmentExprent ass = (AssignmentExprent)second;

			for(;;) {
				if(asf.getRight().equals(ass.getRight())) {
					if((asf.getLeft().type == Exprent.EXPRENT_VAR && ((VarExprent)asf.getLeft()).isStack()) &&
							(ass.getLeft().type != Exprent.EXPRENT_VAR || !((VarExprent)ass.getLeft()).isStack())) {
						
						if(!ass.getLeft().containsExprent(asf.getLeft())) {
							asf.setRight(ass);
							return true;
						}
					}
				}
				if(asf.getRight().type == Exprent.EXPRENT_ASSIGNMENT) {
					asf = (AssignmentExprent)asf.getRight(); 
				} else {
					break;
				}
			}
			
		}
		
		return false;
	}
	
	private static Exprent isPPIorMMI(Exprent first) {
		
		if(first.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent as = (AssignmentExprent)first;
			
			if(as.getRight().type == Exprent.EXPRENT_FUNCTION) {
				FunctionExprent func = (FunctionExprent)as.getRight();
				
				if(func.getFunctype() == FunctionExprent.FUNCTION_ADD ||
						func.getFunctype() == FunctionExprent.FUNCTION_SUB) {
					Exprent econd = func.getLstOperands().get(0);
					Exprent econst = func.getLstOperands().get(1);
					
					if(econst.type != Exprent.EXPRENT_CONST && econd.type == Exprent.EXPRENT_CONST &&
							func.getFunctype() == FunctionExprent.FUNCTION_ADD) {
						econd = econst;
						econst = func.getLstOperands().get(0);
					}
					
					if(econst.type == Exprent.EXPRENT_CONST && ((ConstExprent)econst).hasValueOne()) {
						Exprent left = as.getLeft();
						
						if(left.type != Exprent.EXPRENT_VAR && left.equals(econd)) {
							FunctionExprent ret = new FunctionExprent(
									func.getFunctype() == FunctionExprent.FUNCTION_ADD?FunctionExprent.FUNCTION_PPI:FunctionExprent.FUNCTION_MMI,
											Arrays.asList(new Exprent[]{econd}));
							ret.setImplicitType(VarType.VARTYPE_INT);
							return ret;
						}
					}
				}
			}
		}
		
		return null;
	}
	
	private static boolean isIPPorIMM(Exprent first, Exprent second) {
		
		if(first.type == Exprent.EXPRENT_ASSIGNMENT && second.type == Exprent.EXPRENT_FUNCTION) {
			AssignmentExprent as = (AssignmentExprent)first;
			FunctionExprent in = (FunctionExprent)second;

			if((in.getFunctype() == FunctionExprent.FUNCTION_MMI || in.getFunctype() == FunctionExprent.FUNCTION_PPI) &&
					in.getLstOperands().get(0).equals(as.getRight())) {

				if(in.getFunctype() == FunctionExprent.FUNCTION_MMI) {
					in.setFunctype(FunctionExprent.FUNCTION_IMM);
				} else {
					in.setFunctype(FunctionExprent.FUNCTION_IPP);
				}
				as.setRight(in);
				
				return true;
			}
		}
		
		return false;
	}

	private static boolean isMonitorExit(Exprent first) {
		if(first.type == Exprent.EXPRENT_MONITOR) {
			MonitorExprent monexpr = (MonitorExprent)first;
			if(monexpr.getMontype() == MonitorExprent.MONITOR_EXIT && monexpr.getValue().type == Exprent.EXPRENT_VAR 
					&& !((VarExprent)monexpr.getValue()).isStack()) {
				return true;
			}
		}
		
		return false;
	}
	
	private static boolean isQualifiedNewGetClass(Exprent first, Exprent second) {
		
		if(first.type == Exprent.EXPRENT_INVOCATION) {
			InvocationExprent invexpr = (InvocationExprent)first;
			
			if(!invexpr.isStatic() && invexpr.getInstance().type == Exprent.EXPRENT_VAR && invexpr.getName().equals("getClass") && 
					invexpr.getStringDescriptor().equals("()Ljava/lang/Class;")) {
				
				List<Exprent> lstExprents = second.getAllExprents();
				lstExprents.add(second);
				
				for(Exprent expr : lstExprents) {
					if(expr.type == Exprent.EXPRENT_NEW) {
						NewExprent nexpr = (NewExprent)expr;
						if(nexpr.getConstructor() != null && !nexpr.getConstructor().getLstParameters().isEmpty() && 
								nexpr.getConstructor().getLstParameters().get(0).equals(invexpr.getInstance())) {
							
							String classname = nexpr.getNewtype().value;
							ClassNode node = DecompilerContext.getClassprocessor().getMapRootClasses().get(classname); 
							if(node != null && node.type != ClassNode.CLASS_ROOT) {
								return true;
							}
						}
					}
				}
				
			}
		}
	
		return false;
	}

//	private static boolean isConstructorInvocationRemote(List<Exprent> list, int index) {
//
//		Exprent current = list.get(index);
//
//		if(current.type == Exprent.EXPRENT_ASSIGNMENT) {
//			AssignmentExprent as = (AssignmentExprent)current;
//
//			if(as.getLeft().type == Exprent.EXPRENT_VAR && as.getRight().type == Exprent.EXPRENT_NEW) {
//
//				NewExprent newexpr = (NewExprent)as.getRight();
//				VarType newtype = newexpr.getNewtype();	
//				VarVersionPaar leftPaar = new VarVersionPaar((VarExprent)as.getLeft());
//
//				if(newtype.type == CodeConstants.TYPE_OBJECT && newtype.arraydim == 0 &&
//						newexpr.getConstructor() == null) {
//
//					Set<VarVersionPaar> setChangedVars = new HashSet<VarVersionPaar>();
//					
//					for(int i = index + 1; i < list.size(); i++) {
//						Exprent remote = list.get(i);
//
//						if(remote.type == Exprent.EXPRENT_INVOCATION) {
//							InvocationExprent in = (InvocationExprent)remote;
//
//							if(in.getFunctype() == InvocationExprent.TYP_INIT && in.getInstance().type == Exprent.EXPRENT_VAR
//									&& as.getLeft().equals(in.getInstance())) {
//
//								Set<VarVersionPaar>  setVars = remote.getAllVariables();
//								setVars.remove(leftPaar);
//								setVars.retainAll(setChangedVars);
//								
//								if(setVars.isEmpty()) {
//
//									newexpr.setConstructor(in);
//									in.setInstance(null);
//	
//									if(!setChangedVars.isEmpty()) { // some exprents inbetween        
//										list.add(index+1, as.copy());
//										list.remove(i+1);
//									} else {
//										list.set(i, as.copy());
//									}
//	
//									return true;
//								}
//							}
//						}
//
//						boolean isTempAssignment = false;
//						
//						if(remote.type == Exprent.EXPRENT_ASSIGNMENT) {    // ugly solution  
//							AssignmentExprent asremote = (AssignmentExprent)remote;
//							if(asremote.getLeft().type == Exprent.EXPRENT_VAR &&
//									asremote.getRight().type == Exprent.EXPRENT_VAR) {
//								setChangedVars.add(new VarVersionPaar((VarExprent)asremote.getLeft()));
//								isTempAssignment = true;
//							}
//							
//							// FIXME: needs to be rewritten
//							// propagate (var = new X) forward to the <init> invokation and then reduce
//							
////							if(asremote.getLeft().type == Exprent.EXPRENT_VAR) {
////								List<Exprent> lstRightExprents = asremote.getRight().getAllExprents(true);
////								lstRightExprents.add(asremote.getRight());
////								
////								Set<VarVersionPaar> setTempChangedVars = new HashSet<VarVersionPaar>();
////								boolean isTemp = true;
////								
////								for(Exprent expr : lstRightExprents) {
////									if(expr.type != Exprent.EXPRENT_VAR && expr.type != Exprent.EXPRENT_FIELD) {
////										isTemp = false;
////										break;
////									} else if(expr.type == Exprent.EXPRENT_VAR) {
////										setTempChangedVars.add(new VarVersionPaar((VarExprent)expr));
////									}
////								}
////								
////								if(isTemp) {
////									setChangedVars.addAll(setTempChangedVars);
////									isTempAssignment = true;
////								}
////							}
////						} else if(remote.type == Exprent.EXPRENT_FUNCTION) {
////							FunctionExprent fexpr = (FunctionExprent)remote;
////							if(fexpr.getFunctype() == FunctionExprent.FUNCTION_IPP || fexpr.getFunctype() == FunctionExprent.FUNCTION_IMM 
////									|| fexpr.getFunctype() == FunctionExprent.FUNCTION_PPI || fexpr.getFunctype() == FunctionExprent.FUNCTION_MMI) {
////								if(fexpr.getLstOperands().get(0).type == Exprent.EXPRENT_VAR) {
////									setChangedVars.add(new VarVersionPaar((VarExprent)fexpr.getLstOperands().get(0)));
////									isTempAssignment = true;
////								}
////							}
//						}
//						
//						if(!isTempAssignment) {
//							Set<VarVersionPaar> setVars = remote.getAllVariables();
//							if(setVars.contains(leftPaar)) {
//								return false;
//							} else {
//								setChangedVars.addAll(setVars);
//							}
//						}
//					}
//				}
//			}
//		}
//
//		return false;
//	}

	// propagate (var = new X) forward to the <init> invokation
	private static boolean isConstructorInvocationRemote(List<Exprent> list, int index) {

		Exprent current = list.get(index);

		if(current.type == Exprent.EXPRENT_ASSIGNMENT) {
			AssignmentExprent as = (AssignmentExprent)current;

			if(as.getLeft().type == Exprent.EXPRENT_VAR && as.getRight().type == Exprent.EXPRENT_NEW) {

				NewExprent newexpr = (NewExprent)as.getRight();
				VarType newtype = newexpr.getNewtype();	
				VarVersionPaar leftPaar = new VarVersionPaar((VarExprent)as.getLeft());

				if(newtype.type == CodeConstants.TYPE_OBJECT && newtype.arraydim == 0 && newexpr.getConstructor() == null) {

					for(int i = index + 1; i < list.size(); i++) {
						Exprent remote = list.get(i);

						// <init> invocation
						if(remote.type == Exprent.EXPRENT_INVOCATION) {
							InvocationExprent in = (InvocationExprent)remote;

							if(in.getFunctype() == InvocationExprent.TYP_INIT && in.getInstance().type == Exprent.EXPRENT_VAR && as.getLeft().equals(in.getInstance())) {

								newexpr.setConstructor(in);
								in.setInstance(null);

								list.set(i, as.copy());

								return true;
							}
						}

						// check for variable in use
						Set<VarVersionPaar> setVars = remote.getAllVariables();
						if(setVars.contains(leftPaar)) { // variable used somewhere in between -> exit, need a better reduced code
							return false;
						}
					}
				}
			}
		}

		return false;
	}
	
	private static Exprent isSimpleConstructorInvocation(Exprent exprent) {
		
		List<Exprent> lst = exprent.getAllExprents();
		for(Exprent expr: lst) {
			Exprent ret = isSimpleConstructorInvocation(expr);
			if(ret != null) {
				exprent.replaceExprent(expr, ret);
			}
		}
		
		if(exprent.type == Exprent.EXPRENT_INVOCATION) {
			InvocationExprent in = (InvocationExprent)exprent;
			if(in.getFunctype() == InvocationExprent.TYP_INIT && in.getInstance().type == Exprent.EXPRENT_NEW) {
				NewExprent newexp = (NewExprent)in.getInstance();
				newexp.setConstructor(in);
				in.setInstance(null);
				return newexp;
			}
		}
		
		return null;
	}
	
	
	private static boolean buildIff(Statement stat, SSAConstructorSparseEx ssa) {
		
		if(stat.type == Statement.TYPE_IF && stat.getExprents() == null) {
			IfStatement stif = (IfStatement)stat;
			if(stif.iftype == IfStatement.IFTYPE_IFELSE) {
				Statement ifstat = stif.getIfstat();
				Statement elsestat = stif.getElsestat();
				
				if(ifstat.getExprents() != null && ifstat.getExprents().size() == 1 
						&& elsestat.getExprents()!= null && elsestat.getExprents().size() == 1
						&& ifstat.getAllSuccessorEdges().size() == 1 && elsestat.getAllSuccessorEdges().size() == 1
						&& ifstat.getAllSuccessorEdges().get(0).getDestination() == elsestat.getAllSuccessorEdges().get(0).getDestination()) {
					
					Exprent ifexpr = ifstat.getExprents().get(0);
					Exprent elseexpr = elsestat.getExprents().get(0);
					
					if(ifexpr.type == Exprent.EXPRENT_ASSIGNMENT && elseexpr.type == Exprent.EXPRENT_ASSIGNMENT) {
						AssignmentExprent ifas = (AssignmentExprent)ifexpr;
						AssignmentExprent elseas = (AssignmentExprent)elseexpr;
						
						if(ifas.getLeft().type == Exprent.EXPRENT_VAR && elseas.getLeft().type == Exprent.EXPRENT_VAR) {
							VarExprent ifvar = (VarExprent)ifas.getLeft();
							VarExprent elsevar = (VarExprent)elseas.getLeft();
								
							if(ifvar.getIndex() == elsevar.getIndex() && ifvar.isStack()) { // ifvar.getIndex() >= VarExprent.STACK_BASE) {

								boolean found = false;
								
								for(Entry<VarVersionPaar, FastSparseSet<Integer>> ent : ssa.getPhi().entrySet()) {
									if(ent.getKey().var == ifvar.getIndex()) {
										if(ent.getValue().contains(ifvar.getVersion()) && ent.getValue().contains(elsevar.getVersion())) {
											found = true;
											break;
										}
									}
								}
								
								if(found) {
									List<Exprent> data = new ArrayList<Exprent>();
									data.addAll(stif.getFirst().getExprents());

									data.add(new AssignmentExprent(ifvar, new FunctionExprent(FunctionExprent.FUNCTION_IIF, 
											Arrays.asList(new Exprent[] {((IfExprent)stif.getHeadexprent()).getCondition(),
													ifas.getRight(),
													elseas.getRight()}))));
									stif.setExprents(data);

									if(stif.getAllSuccessorEdges().isEmpty()) {
										StatEdge ifedge = ifstat.getAllSuccessorEdges().get(0);
										StatEdge edge = new StatEdge(ifedge.getType(), stif, ifedge.getDestination());

										stif.addSuccessor(edge);
										if(ifedge.closure != null) {
											ifedge.closure.addLabeledEdge(edge);
										}
									}

									SequenceHelper.destroyAndFlattenStatement(stif);

									return true;
								}
							}
						}
					} else if(ifexpr.type == Exprent.EXPRENT_EXIT && elseexpr.type == Exprent.EXPRENT_EXIT) {
						ExitExprent ifex = (ExitExprent)ifexpr;
						ExitExprent elseex = (ExitExprent)elseexpr;
						
						if(ifex.getExittype() == elseex.getExittype() && ifex.getValue() != null && elseex.getValue() != null &&
								ifex.getExittype() == ExitExprent.EXIT_RETURN) { 
							
							// throw is dangerous, because of implicit casting to a common superclass
							// e.g. throws IOException and throw true?new RuntimeException():new IOException(); won't work
							if(ifex.getExittype() == ExitExprent.EXIT_THROW && 
									!ifex.getValue().getExprType().equals(elseex.getValue().getExprType())) {  // note: getExprType unreliable at this point!
								return false;
							}
							
							List<Exprent> data = new ArrayList<Exprent>();
							data.addAll(stif.getFirst().getExprents());
							
							data.add(new ExitExprent(ifex.getExittype(), new FunctionExprent(FunctionExprent.FUNCTION_IIF, 
									Arrays.asList(new Exprent[] {((IfExprent)stif.getHeadexprent()).getCondition(),
																	ifex.getValue(),
																	elseex.getValue()})), ifex.getRettype()));
							stif.setExprents(data);
							
							StatEdge retedge = ifstat.getAllSuccessorEdges().get(0);
							stif.addSuccessor(new StatEdge(StatEdge.TYPE_BREAK, stif, retedge.getDestination(), retedge.closure==stif?stif.getParent():retedge.closure));
							
							SequenceHelper.destroyAndFlattenStatement(stif);
							
							return true;
						}
					}
				}
			}
		}
		
		return false;
	}
		
	
}
