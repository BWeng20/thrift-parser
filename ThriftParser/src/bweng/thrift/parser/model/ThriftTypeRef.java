/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Part of the data model, representing a (possibly unresolved) type reference.
 */
public class ThriftTypeRef extends ThriftType
{
    public String        declaredName_;
    public ThriftType    resolvedType_;
    
    @Override
    public String toString()
    {
        return (resolvedType_ != null) ? resolvedType_.toString() : ("UNRESOLVED:"+declaredName_);
    }
    
}
