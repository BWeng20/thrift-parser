/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.List;

/**
 * Part of the data model, representing a Thrift Function inside a service.
 */
public class ThriftFunction extends ThriftObject
{
    public String name_;
    public List<ThriftField> parameters_;
    public ThriftType return_type_;
    public ThriftFunctionMode mode_ = ThriftFunctionMode.NONE;

    public List<ThriftField> exceptions_;

    public ThriftService service_;

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(100);
        sb.append(return_type_).append(' ').append(name_).append(parameters_);
        if ( !exceptions_.isEmpty() )
            sb.append(" throws ").append(exceptions_);
        return sb.toString();
    }

    /**
     * Checks if parameters, exceptions and the return type are valid.
     * @return true if all used types are valid.
     */
    @Override
    final public boolean valid()
    {
        if ( parameters_ != null ) for ( ThriftField p : parameters_ ) if (!p.valid()) return false;
        if ( exceptions_ != null ) for ( ThriftField p : exceptions_ ) if (!p.valid()) return false;

        return ( return_type_ != null && return_type_.valid() );
    }
}
