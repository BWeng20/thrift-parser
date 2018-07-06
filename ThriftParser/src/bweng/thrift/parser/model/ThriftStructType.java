/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.List;

/**
 * Part of the data model, representing a Thrift Struct.
 */
public class ThriftStructType extends ThriftType
{
    public List<ThriftField> fields_;

    @Override
    public String toString()
    {
        return "struct " + name_fully_qualified_ + fields_.toString();
    }

   /**
    * Checks if all used types are valid.
    * @return true if all types are valid.
    */
    @Override
    final public boolean valid()
    {
        if ( fields_ == null ) return false;
        for (ThriftField f : fields_) if ( !f.valid() ) return false;
        return true;
    }
}
