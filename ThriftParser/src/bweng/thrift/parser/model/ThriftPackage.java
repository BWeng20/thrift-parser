/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Part of the data model, representing a Thrift Package [DAI Extension].
 */
public class ThriftPackage extends ThriftScope
{
    // Direct sub packages
    public List<ThriftPackage> subpackages_ = new ArrayList<>();
    // types
    public ThriftPackage parent_;

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(1000);
        sb.append("package ").append( name_ );
        if ( !services_.isEmpty() ) sb.append(services_);
        if ( !subpackages_.isEmpty() ) sb.append('{').append(subpackages_).append("}");
        return sb.toString();
    }

    public ThriftType findTypeInPackage( String name )
    {
        if ( name.startsWith( name_fully_qualified_+'.' ))
            name = name.substring( name_fully_qualified_.length()+1 );

        if ( 0 <= name.indexOf('.' ))
        {
            // Search in sub packages
            for (int pi=0 ; pi<subpackages_.size() ; ++pi )
            {
                ThriftPackage subp = subpackages_.get(pi);
                if ( name.startsWith( subp.name_+'.' ) )
                {
                    ThriftType t = subp.findTypeInPackage( name.substring(subp.name_.length()+1 ) );
                    if ( t != null ) return t;
                }
            }
        }
        else
        {
            for (int ti=0 ; ti<types_.size() ; ++ti )
                if ( types_.get(ti).name_.equals(name))
                    return types_.get(ti);
        }
        return null;
    }

    public ThriftService findServiceInPackage( String name )
    {
        if ( name.startsWith( name_fully_qualified_+'.' ))
            name = name.substring( name_fully_qualified_.length()+1 );

        if ( 0 <= name.indexOf('.' ))
        {
            // Search in sub packages
            for (int pi=0 ; pi<subpackages_.size() ; ++pi )
            {
                ThriftPackage subp = subpackages_.get(pi);
                if ( name.startsWith( subp.name_+'.' ) )
                {
                    ThriftService t = subp.findServiceInPackage( name.substring(subp.name_.length()+1 ) );
                    if ( t != null ) return t;
                }
            }
        }
        else
        {
            for (int ti=0 ; ti<services_.size() ; ++ti )
                if ( services_.get(ti).name_.equals(name))
                    return services_.get(ti);
        }
        return null;
    }

    /**
     * Checks all services and sub-packages for validity.
     * @return true if all services and packages are valid.
     */
    @Override
    public boolean valid()
    {
        if ( !super.valid() ) return false;
        for ( ThriftPackage p : subpackages_ ) if (!p.valid()) return false;
        return true;
    }

}
