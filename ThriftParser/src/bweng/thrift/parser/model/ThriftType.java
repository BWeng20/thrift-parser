/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Part of the data model, representing a Thrift Type.
 */
public class ThriftType extends ThriftObject
{
    public String name_fully_qualified_;
    public String name_;
    public ThriftPackage package_;

    @Override
    public String toString()
    {
        return name_;
    }

    public final static ThriftType BOOL;
    public final static ThriftType INT8;
    public final static ThriftType INT16;
    public final static ThriftType INT32;
    public final static ThriftType INT64;
    public final static ThriftType UINT8;
    public final static ThriftType UINT16;
    public final static ThriftType UINT32;
    public final static ThriftType UINT64;
    public final static ThriftType DOUBLE;
    public final static ThriftType STRING;
    public final static ThriftType BINARY;
    public final static ThriftType VOID;

    public final static ThriftType SERVICE;

    /**
     * Resolves Type-references and ThriftTypeDef, returning the inner real type.
     * @return The real type or null.
     */
    public ThriftType getRealType()
    {
        ThriftType type = this;
        while (true)
        {
            if (type instanceof ThriftTypeRef)
                type = ((ThriftTypeRef) type).resolvedType_;
            else if ( type instanceof ThriftTypedef)
                type = ((ThriftTypedef)type).reftype_;
            else
                break;
        }
        return type;
    }

    static
    {
        BOOL   = new ThriftType(); BOOL.name_   = BOOL.name_fully_qualified_    = "bool";
        INT8   = new ThriftType(); INT8.name_   = INT8.name_fully_qualified_    = "int8";
        INT16  = new ThriftType(); INT16.name_  = INT16.name_fully_qualified_   = "int16";
        INT32  = new ThriftType(); INT32.name_  = INT32.name_fully_qualified_   = "int32";
        INT64  = new ThriftType(); INT64.name_  = INT64.name_fully_qualified_   = "int64";
        UINT8  = new ThriftType(); UINT8.name_  = UINT8.name_fully_qualified_   = "uint8";
        UINT16 = new ThriftType(); UINT16.name_ = UINT16.name_fully_qualified_  = "uint16";
        UINT32 = new ThriftType(); UINT32.name_ = UINT32.name_fully_qualified_  = "uint32";
        UINT64 = new ThriftType(); UINT64.name_ = UINT64.name_fully_qualified_  = "uint64";
        DOUBLE = new ThriftType(); DOUBLE.name_ = DOUBLE.name_fully_qualified_  = "double";
        STRING = new ThriftType(); STRING.name_ = STRING.name_fully_qualified_  = "string";
        BINARY = new ThriftType(); BINARY.name_ = BINARY.name_fully_qualified_  = "binary";
        VOID   = new ThriftType(); VOID.name_   = VOID.name_fully_qualified_    = "void";
        SERVICE= new ThriftType(); SERVICE.name_= SERVICE.name_fully_qualified_ = "service*";
    }

   /**
    * Checks if this type is valid.
    * @return true if type is valid.
    */
    @Override
    public boolean valid()
    {
        return true;
    }
}
