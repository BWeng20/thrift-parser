/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Part of the data model, 
 * base class for Thrift components that possibly have version informations.
 */
public class ThriftObject extends ThriftParserInfo
{
    public String  comment_;
    public String  version_;
    
    // True if the object is marked with @deprecated
    public boolean deprecated_ = false;
    
    private ThriftDocument document_;
    
    public final ThriftDocument getDocument()
    {
       return document_;
    }
    
    public final void setDocument(ThriftDocument doc)
    {
       document_ = doc;
    }
}
