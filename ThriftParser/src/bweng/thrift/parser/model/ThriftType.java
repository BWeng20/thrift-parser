/* Copyright (c) 2015 Bernd Wengenroth
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
    public final static ThriftType DOUBLE;
    public final static ThriftType STRING;
    public final static ThriftType BINARY;
    public final static ThriftType VOID;
    
    public final static ThriftType SERVICE;    

    static
    {
        BOOL   = new ThriftType(); BOOL.name_   = BOOL.name_fully_qualified_    = "bool";
        INT8   = new ThriftType(); INT8.name_   = INT8.name_fully_qualified_    = "int8";
        INT16  = new ThriftType(); INT16.name_  = INT16.name_fully_qualified_   = "int16";
        INT32  = new ThriftType(); INT32.name_  = INT32.name_fully_qualified_   = "int32";
        INT64  = new ThriftType(); INT64.name_  = INT64.name_fully_qualified_   = "int64";
        DOUBLE = new ThriftType(); DOUBLE.name_ = DOUBLE.name_fully_qualified_  = "double";
        STRING = new ThriftType(); STRING.name_ = STRING.name_fully_qualified_  = "string";
        BINARY = new ThriftType(); BINARY.name_ = BINARY.name_fully_qualified_  = "binary";
        VOID   = new ThriftType(); VOID.name_   = VOID.name_fully_qualified_  = "void";      
        SERVICE= new ThriftType(); SERVICE.name_= SERVICE.name_fully_qualified_ = "service*";
    }
    
}
