/* Copyright (c) 2015 Bernd Wengenroth
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
        return "package "+name_+services_+" "+subpackages_;
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
}
