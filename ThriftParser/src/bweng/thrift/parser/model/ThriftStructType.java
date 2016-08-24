/* Copyright (c) 2015 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.List;

/**
 * Part of the data model, representing a Thrift Struct.
 */
public final class ThriftStructType extends ThriftType
{
    public List<ThriftField> fields_;
    
    @Override
    public String toString()
    {
        return "struct " + name_fully_qualified_ + fields_.toString();
    }
    
}
