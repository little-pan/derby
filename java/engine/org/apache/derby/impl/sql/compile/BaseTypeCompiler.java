/*

   Derby - Class org.apache.derby.impl.sql.compile.BaseTypeCompiler

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.impl.sql.compile;

import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.loader.ClassFactory;

import org.apache.derby.iapi.services.sanity.SanityManager;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.sql.compile.TypeCompiler;

import org.apache.derby.iapi.types.StringDataValue;
import org.apache.derby.iapi.types.TypeId;

import org.apache.derby.iapi.types.DataTypeDescriptor;

import org.apache.derby.iapi.reference.ClassName;

import org.apache.derby.iapi.services.compiler.LocalField;
import org.apache.derby.iapi.services.compiler.MethodBuilder;

import org.apache.derby.iapi.services.classfile.VMOpcode;

/**
 * This is the base implementation of TypeCompiler
 *
 */

abstract class BaseTypeCompiler implements TypeCompiler
{
	private TypeId correspondingTypeId;

	/**
	 * Get the method name for getting out the corresponding primitive
	 * Java type.
	 *
	 * @return String		The method call name for getting the
	 *						corresponding primitive Java type.
	 */
	public String getPrimitiveMethodName()
	{
		if (SanityManager.DEBUG)
		{
			SanityManager.THROWASSERT("getPrimitiveMethodName not applicable for " +
									  getClass().toString());
		}
		return null;
	}

	/** @see TypeCompiler#getMatchingNationalCharTypeName */
	public String getMatchingNationalCharTypeName()
	{
		return TypeId.NATIONAL_CHAR_NAME;
	}


	/**
	 * @see TypeCompiler#resolveArithmeticOperation
	 *
	 * @exception StandardException		Thrown on error
	 */
	public DataTypeDescriptor
	resolveArithmeticOperation(DataTypeDescriptor leftType,
								DataTypeDescriptor rightType,
								String operator)
							throws StandardException
	{
		throw StandardException.newException(SQLState.LANG_BINARY_OPERATOR_NOT_SUPPORTED, 
										operator,
										leftType.getTypeId().getSQLTypeName(),
										rightType.getTypeId().getSQLTypeName()
										);
	}

	/** @see TypeCompiler#generateNull(MethodBuilder, int, String) */
	public void generateNull(MethodBuilder mb, int collationType, 
			String className)
	{
		mb.callMethod(VMOpcode.INVOKEINTERFACE, (String) null,
									nullMethodName(),
									interfaceName(),
									1);
	}

	/** @see TypeCompiler#generateDataValue(MethodBuilder, int, String, LocalField) */
	public void generateDataValue(MethodBuilder mb, int collationType,
			String className, LocalField field)
	{
		String				interfaceName = interfaceName();

		// push the second argument

		/* If fieldName is null, then there is no
		 * reusable wrapper (null), else we
		 * reuse the field.
		 */
		if (field == null)
		{
			mb.pushNull(interfaceName);
		}
		else
		{
			mb.getField(field);
		}

		mb.callMethod(VMOpcode.INVOKEINTERFACE, (String) null,
							dataValueMethodName(),
							interfaceName,
							2);

		if (field != null)
		{
			/* Store the result of the method call in the field,
			 * so we can re-use the wrapper.
			 */
			mb.putField(field);
		}
	}

	/**
	 * If the collation type is UCS_BASIC, then we have already generated the
	 * code for the correct DVD and hence simply return from this method. 
	 * 
	 * But if the collation type is territory based and we are generating DVDs
	 * for character types, then we need to generate CollatorSQLxxx type of 
	 * DVD. This CollatorSQLxxx DVD will be provided by generating following 
	 * code which works on top of the DVD that has been generated with 
	 * UCS_BASIC collation.
	 * DVDwithUCS_BASIC.getValue(DVF.getCharacterCollator(collationType));
	 * 
	 * This method will be called only by CharTypeCompiler and ClobTypeCompiler 
	 * because those are the only type compilers who generate DVDs which are 
	 * impacted by the collation. Rest of the TypeCompilers generate DVDs which
	 * are collation in-sensitive.
	 * 
	 * @param mb The method to put the expression in
	 * @param collationType For character DVDs, this will be used to determine
	 *   what Collator should be associated with the DVD which in turn will 
	 *   decide whether to generate CollatorSQLcharDVDs or SQLcharDVDs. For 
	 *   other types of DVDs, this parameter will be ignored.
	 * @param className name of the base class of the activation's hierarchy
	 */
	protected void generateCollationSensitiveDataValue(MethodBuilder mb, 
			int collationType, String className){		
		if (collationType == StringDataValue.COLLATION_TYPE_UCS_BASIC)
			return; 
		//In case of character DVDs, for territory based collation, we need to 
		//generate DVDs with territory based RuleBasedCollator and hence we 
		//need to generate CollatorSQLChar/CollatorSQLVarchar/
		//CollatorSQLLongvarchar/CollatorSQLClob 
		pushDataValueFactory(mb, className);
		mb.push(collationType);
		mb.callMethod(VMOpcode.INVOKEINTERFACE, null, "getCharacterCollator",
				"java.text.RuleBasedCollator", 1);
		mb.callMethod(VMOpcode.INVOKEINTERFACE, null, "getValue", interfaceName(), 1);
	}
	
	private Object getDVF;
	/**
	 * This method will push a DVF on the stack. This DVF is required to get
	 * the territory based collator using the collation type. In other words,
	 * this DVF will be used to generate something like following
	 * DVF.getCharacterCollator(collationType)
	 * 
	 * @param mb The method to put the expression in
	 * @param className name of the base class of the activation's hierarchy
	 */
	private void pushDataValueFactory(MethodBuilder mb, String className)
	{
		// generates:
		//	   getDataValueFactory()
		//

		if (getDVF == null) {
			getDVF = mb.describeMethod(VMOpcode.INVOKEVIRTUAL,
										className,
										"getDataValueFactory",
										ClassName.DataValueFactory);
		}

		mb.pushThis();
		mb.callMethod(getDVF);
	}

	protected abstract String nullMethodName();

	/**
		Return the method name to get a Derby DataValueDescriptor
		object of the correct type. This implementation returns "getDataValue".
	*/
	protected String dataValueMethodName()
	{
		return "getDataValue";
	}

	
	/**
	 * Determine whether thisType is storable in otherType due to otherType
	 * being a user type.
	 *
	 * @param thisType	The TypeId of the value to be stored
	 * @param otherType	The TypeId of the value to be stored in
	 *
	 * @return	true if thisType is storable in otherType
	 */
	protected boolean userTypeStorable(TypeId thisType,
							TypeId otherType,
							ClassFactory cf)
	{
		/*
		** If the other type is user-defined, use the java types to determine
		** assignability.
		*/
		if (otherType.userType())
		{
			return cf.getClassInspector().assignableTo(
					thisType.getCorrespondingJavaTypeName(),
					otherType.getCorrespondingJavaTypeName());
		}

		return false;
	}
	
	/**
	 * Tell whether this numeric type can be converted to the given type.
	 *
	 * @param otherType	The TypeId of the other type.
	 * @param forDataTypeFunction  was this called from a scalarFunction like
	 *                             CHAR() or DOUBLE()
	 */
	public boolean numberConvertible(TypeId otherType, 
									 boolean forDataTypeFunction)
	{

		// Can't convert numbers to long types
		if (otherType.isLongConcatableTypeId())
			return false;

		// Numbers can only be converted to other numbers, 
		// and CHAR, (not VARCHARS or LONGVARCHAR). 
		// Only with the CHAR() or VARCHAR()function can they be converted.
		boolean retval =((otherType.isNumericTypeId()) ||
						 (otherType.isBooleanTypeId()) ||
						 (otherType.userType()));

		// For CHAR  Conversions, function can convert 
		// Floating types
		if (forDataTypeFunction)
			retval = retval || 
				(otherType.isFixedStringTypeId() &&
				(getTypeId().isFloatingPointTypeId()));
	   
		retval = retval ||
			(otherType.isFixedStringTypeId() && 					  
			 (!getTypeId().isFloatingPointTypeId()));
		
		return retval;

	}

	/**
	 * Tell whether this numeric type can be stored into from the given type.
	 *
	 * @param thisType	The TypeId of this type
	 * @param otherType	The TypeId of the other type.
	 * @param cf		A ClassFactory
	 */

	public boolean numberStorable(TypeId thisType,
									TypeId otherType,
									ClassFactory cf)
	{
		/*
		** Numbers can be stored into from other number types.
		** Also, user types with compatible classes can be stored into numbers.
		*/
		if ((otherType.isNumericTypeId())	||
			(otherType.isBooleanTypeId()))
			return true;

		/*
		** If the other type is user-defined, use the java types to determine
		** assignability.
		*/
		return userTypeStorable(thisType, otherType, cf);
	}


	/**
	 * Get the TypeId that corresponds to this TypeCompiler.
	 */
	protected TypeId getTypeId()
	{
		return correspondingTypeId;
	}

	/**
	 * Get the TypeCompiler that corresponds to the given TypeId.
	 */
	protected TypeCompiler getTypeCompiler(TypeId typeId)
	{
		return TypeCompilerFactoryImpl.staticGetTypeCompiler(typeId);
	}

	/**
	 * Set the TypeCompiler that corresponds to the given TypeId.
	 */
	void setTypeId(TypeId typeId)
	{
		correspondingTypeId = typeId;
	}

	/**
	 * Get the StoredFormatId from the corresponding
	 * TypeId.
	 *
	 * @return The StoredFormatId from the corresponding
	 * TypeId.
	 */
	protected int getStoredFormatIdFromTypeId()
	{
		return getTypeId().getTypeFormatId();
	}


}





