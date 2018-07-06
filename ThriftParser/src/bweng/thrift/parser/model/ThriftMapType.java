/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 * Represents a map type.
 */
public final class ThriftMapType extends ThriftType
{

    public ThriftType key_type_;
    public ThriftType value_type_;


    @Override
    public String toString()
    {
        return "Map<"+key_type_+","+value_type_+">";
    }

   /**
    * Checks if the value- and key-type are valid.
    * @return true if value- and key-type are valid.
    */
    @Override
    final public boolean valid()
    {
        return ( value_type_ != null && value_type_.valid() && key_type_ != null && key_type_.valid() );
    }
}
