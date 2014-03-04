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

package de.fernflower.struct.attr;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.modules.decompiler.exps.AnnotationExprent;
import de.fernflower.modules.decompiler.exps.ConstExprent;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.modules.decompiler.exps.FieldExprent;
import de.fernflower.modules.decompiler.exps.NewExprent;
import de.fernflower.struct.consts.ConstantPool;
import de.fernflower.struct.consts.PrimitiveConstant;
import de.fernflower.struct.gen.FieldDescriptor;
import de.fernflower.struct.gen.VarType;

public class StructAnnotationAttribute extends StructGeneralAttribute {

	private List<AnnotationExprent> annotations;
	
	public void initContent(ConstantPool pool) {

		super.initContent(pool);
		
		annotations = new ArrayList<AnnotationExprent>();
		DataInputStream data = new DataInputStream(new ByteArrayInputStream(info, 2, info.length));
		
		int len = (((info[0] & 0xFF)<<8) | (info[1] & 0xFF));
		for(int i=0;i<len;i++) {
			annotations.add(parseAnnotation(data, pool));
		}
		
	}
	
	public static AnnotationExprent parseAnnotation(DataInputStream data, ConstantPool pool) {

		try {
		
			String classname = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
			VarType cltype = new VarType(classname);
			
			int len = data.readUnsignedShort();
			
			List<String> parnames = new ArrayList<String>();
			List<Exprent> parvalues = new ArrayList<Exprent>();
			
			for(int i=0;i<len;i++) {
				parnames.add(pool.getPrimitiveConstant(data.readUnsignedShort()).getString());
				parvalues.add(parseAnnotationElement(data, pool));
			}
			
			return new AnnotationExprent(cltype.value, parnames, parvalues);
			
		} catch(IOException ex) {
			throw new RuntimeException(ex);
		}
	
	}
	
	public static Exprent parseAnnotationElement(DataInputStream data, ConstantPool pool) {

		try {
			int tag = data.readUnsignedByte();
			
			switch(tag) {
			case 'e': // enum constant
				String classname = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
				String constname = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
				
				FieldDescriptor descr = FieldDescriptor.parseDescriptor(classname);
				return new FieldExprent(constname, descr.type.value, true, null, descr);
			case 'c': // class
				String descriptor = pool.getPrimitiveConstant(data.readUnsignedShort()).getString();
				VarType type = FieldDescriptor.parseDescriptor(descriptor).type;
				
				String value;
				switch(type.type) {  
				case CodeConstants.TYPE_OBJECT:
					value = type.value;
					break;
				case CodeConstants.TYPE_BYTE:
				case CodeConstants.TYPE_CHAR:
				case CodeConstants.TYPE_DOUBLE:
				case CodeConstants.TYPE_FLOAT:
				case CodeConstants.TYPE_INT:
				case CodeConstants.TYPE_LONG:
				case CodeConstants.TYPE_SHORT:
				case CodeConstants.TYPE_BOOLEAN:
				case CodeConstants.TYPE_VOID:
					// FIXME: other classes if applicable, see attribute description
				default:
					throw new RuntimeException("invalid class type!");
				}
				return new ConstExprent(VarType.VARTYPE_CLASS, value);
			case '[': // array
				int len = data.readUnsignedShort();
				List<Exprent> lst = new ArrayList<Exprent>();
				
				for(int i=0;i<len;i++) {
					lst.add(parseAnnotationElement(data, pool));
				}
				
				VarType newtype;
				if(lst.isEmpty()) {
					newtype = new VarType(CodeConstants.TYPE_OBJECT, 1, "java/lang/Object");
				} else {
					VarType eltype = lst.get(0).getExprType();
					newtype = new VarType(eltype.type, 1, eltype.value);
				}
				
				NewExprent newexpr = new NewExprent(newtype, new ArrayList<Exprent>());
				newexpr.setDirectArrayInit(true);
				newexpr.setLstArrayElements(lst);
				return newexpr;
			case '@': // annotation
				return parseAnnotation(data, pool);
			default:
				PrimitiveConstant cn = pool.getPrimitiveConstant(data.readUnsignedShort());
				switch(tag) {
				case 'B':
					return new ConstExprent(VarType.VARTYPE_BYTE, cn.value);
				case 'C': 
					return new ConstExprent(VarType.VARTYPE_CHAR, cn.value);
				case 'D': 
					return new ConstExprent(VarType.VARTYPE_DOUBLE, cn.value);
				case 'F':
					return new ConstExprent(VarType.VARTYPE_FLOAT, cn.value);
				case 'I':
					return new ConstExprent(VarType.VARTYPE_INT, cn.value);
				case 'J':
					return new ConstExprent(VarType.VARTYPE_LONG, cn.value);
				case 'S':
					return new ConstExprent(VarType.VARTYPE_SHORT, cn.value);
				case 'Z':
					return new ConstExprent(VarType.VARTYPE_BOOLEAN, cn.value);
				case 's':
					return new ConstExprent(VarType.VARTYPE_STRING, cn.value);
				default:
					throw new RuntimeException("invalid element type!");
				}
			}
		} catch(IOException ex) {
			throw new RuntimeException(ex);
		}
		
	}
	

	public List<AnnotationExprent> getAnnotations() {
		return annotations;
	}
	
}
