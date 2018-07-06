/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

/**
 *
 * @author Bernd Wengenroth
 */
public final class ThriftSetType extends ThriftType
{
    public ThriftType value_type_;

    @Override
    public String toString()
    {
        return "Set<"+value_type_+">";
    }

   /**
    * Checks if the value-type is valid.
    * @return true if value-type is valid.
    */
    @Override
    final public boolean valid()
    {
        return ( value_type_ != null && value_type_.valid() );
    }
}
