/* Copyright (c) 2015 Bernd Wengenroth
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
}
