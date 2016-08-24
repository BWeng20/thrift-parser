/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Part of the data model, representing a Thrift Enum value.
 */
public class ThriftEnumValue extends ThriftParserInfo
{
    public String name_;
    public int value_;
    
    @Override
    public String toString()
    {
        return name_;
    }
}
