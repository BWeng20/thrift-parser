/* Copyright (c) 2015-2018 Bernd Wengenroth
 * Licensed under the MIT License.
 * See LICENSE file for details.
 */
package bweng.thrift.parser.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Part of the data model, representing a Thrift Document.
 */
public class ThriftDocument extends ThriftScope
{
    /** Operating system dependent path where the document was loaded from or null. */
    public Path ospath_;

    /** All includes. */
    public List<ThriftInclude> includes_;

    /** All packages (also all sub-packages) [DAI Extension]. */
    public List<ThriftPackage> all_packages_;

    /** All services (also from all sub-packages). */
    public List<ThriftService> all_services_;

    /** All services (also from all sub-packages) by fully qualified name. */
    public Map<String,ThriftService> all_services_byname_;

    /** All types defined in this document. */
    public Map<String, ThriftType> all_types_;

    /** All yet unresolved types in this document. */
    public Map<String, ThriftTypeRef> unresolved_types_;

    /** All yet unresolved services in this document. */
    public List<ThriftServiceRef> unresolved_services_;

    /**
     * Gets a human readable description of all packages/services.
     * @return Textual representation.
     */
    @Override
    public String toString()
    {
        return ""+(0<all_packages_.size()?all_packages_:all_services_);
    }

    /**
     * Checks all services for validity.<p>
     * Packages are not checked as their validity is already covered by the global service-list.
     * @return true if all services are valid.
     */
    @Override
    public boolean valid()
    {
        if ( all_services_ != null )
            for ( ThriftService s : all_services_ ) if (!s.valid()) return false;
        return true;
    }

}
