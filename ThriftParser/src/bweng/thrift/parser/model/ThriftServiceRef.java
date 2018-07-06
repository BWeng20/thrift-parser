/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Representing a (possibly unresolved) service reference.
 */
public class ThriftServiceRef extends ThriftParserInfo
{
    public String        declaredName_;
    public ThriftPackage declarationPackage_;
    
    public ThriftService resolvedService_;
    
    @Override
    public String toString()
    {
        return (resolvedService_ != null) ? resolvedService_.toString() : ("UNRESOLVED:"+declaredName_);
    }
    
}
