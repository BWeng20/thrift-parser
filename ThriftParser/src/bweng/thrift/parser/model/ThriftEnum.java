/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.List;

/**
 * Part of the data model, representing a Thrift Enum declaration.
 */
public class ThriftEnum extends ThriftType 
{
    public List<ThriftEnumValue> values_;
    
    @Override
    public String toString()
    {
        return "enum "+name_fully_qualified_+values_.toString();
    }
    
}
