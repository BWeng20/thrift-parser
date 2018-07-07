/* Copyright (c) 2015-2018 Bernd Wengenroth
* Licensed under the MIT License.
* See LICENSE file for details.
*/
package bweng.thrift.parser;

import bweng.thrift.parser.model.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.runtime.ANTLRReaderStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.mozilla.universalchardet.UniversalDetector;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
* Generates our Data model from Antlr parser results.
*/
public final class ThriftModelGenerator
{
    /**
     * Creates a model generator.
     */
    public ThriftModelGenerator()
    {
        this(null);
    }

    /**
     * Creates a model generator with additional include paths.
     * @param incudePaths Additional paths to locate for includes.
     */
    public ThriftModelGenerator( List<String> incudePaths )
    {
        incudePaths_ = incudePaths;
    }

    /**
     * Get a Path object for some native file path.
     */
    public static Path getPath( String ospath )
    {
        URI uri = null;
        try
        {
            uri = new URI(ospath);
        }
        catch (URISyntaxException uriex)
        {
        }

        if ( uri != null && "jar".equalsIgnoreCase( uri.getScheme() ))
        {
            FileSystem fs=null;
            int si = ospath.indexOf("!");
            String arc = ospath.substring(0,si);
            try
            {
                URI fsuri = new URI( arc );
                try
                {
                    fs = FileSystems.getFileSystem(fsuri);
                }
                catch ( FileSystemNotFoundException fsnf)
                {
                    fs =  FileSystems.newFileSystem(fsuri, new HashMap<String, Object>());
                }
                return fs.getPath(ospath.substring(si+1));
            }
            catch (Exception ex2)
            {
            }
        }
        return FileSystems.getDefault().getPath(ospath);
    }

    /**
     * Loads all includes of the document and tries to resolve all types.
     * @param doc              The document with includes.
     * @param bReplaceTypeRefs If true all type-references as Type-Definitions and previous
     *                         unresolved Type-References are replaced by the underlying type.
     */
    public synchronized void loadIncludes( ThriftDocument doc, boolean bReplaceTypeRefs )
    {
        loaded_ = new HashMap<>();
        loadIncludesInternal( doc );
        loaded_.clear();

        global_types_   = new HashMap<>();
        global_services_= new HashMap<>();

        collect_references( doc );
        resolve_all( doc, bReplaceTypeRefs );
    }

    /**
     * Loads all Thrift files from an Zip-Archive, filtered by a regular expression.
     * Types and services are resolved as far as possible.
     * Include directives in the thrift files are ignored.
     * @param regex If null, no filter is applies.
     */
    public ThriftDocument loadZipArchive( Path ospath, Pattern regex ) throws IOException
    {
        ThriftDocument doc = null;

        final String name = getDocumentName( ospath.toString() );

        ZipFile zf = new  ZipFile(ospath.toFile());

        byte[] buffer = new byte[4*1024];
        ByteArrayOutputStream bs = new ByteArrayOutputStream();

        Enumeration<? extends ZipEntry> entries = zf.entries();
        while( entries.hasMoreElements() )
        {
            ZipEntry ze = entries.nextElement();
            if ( regex == null || regex.matcher( ze.getName() ).matches() )
            {
                bs.reset();
                final InputStream is = zf.getInputStream(ze);
                while( true )
                {
                    int r = is.read(buffer);
                    if (r == -1) break;
                    bs.write(buffer, 0, r);
                }
                final byte[] content = bs.toByteArray();
                if ( content != null && content.length > 0)
                {
                    ThriftDocument zd = parseDocument( content, name );
                    if ( zd != null )
                    {
                        if ( doc != null )
                        {
                            merge( doc, zd);
                        }
                        else
                            doc = zd;
                        // Skip all includes.
                        doc.includes_.clear();
                    }
                }
            }
        }
        if ( doc != null )
        {
           global_types_   = new HashMap<>();
           global_services_= new HashMap<>();

           collect_references( doc );
           resolve_all( doc, true );

           doc.ospath_ = ospath;
        }
        return doc;
    }

    /**
     * Loads all Thrift files from an Zip-Archive.
     * Filters entries by extension ".*\.thrift".
     * @param ospath The path to the archive to load.
     * @return The joined Thrift documents. Same as if all thrift documents would be copied in one file.
     */
    public ThriftDocument loadZipArchive( Path ospath ) throws IOException
    {
        return loadZipArchive(ospath, Pattern.compile(".*\\.thrift", Pattern.CASE_INSENSITIVE) );
    }

    /**
     * Loads a thrift document by file path.
     * @param ospath The path to the file to load.
     * @return The parsed Thrift document or null if parser failed.
     */
    public ThriftDocument loadDocument( Path ospath ) throws IOException
    {
        ThriftDocument doc = parseDocument( Files.readAllBytes(ospath), getDocumentName( ospath.toString() ) );
        if ( doc != null )
           doc.ospath_ = ospath;
        return doc;
    }

    private ThriftDocument doc_;
    // Current package [DAI Extension]
    private ThriftPackage  current_package_;

    // All types not resolved so far
    private Map<String, ThriftType> global_types_;

    // All services not resolved so far
    private Map<String, ThriftService> global_services_;

    private ThriftCommentTokenSource tokensource_;
    private ThriftParser   parser_;
    private TokenStream tokens_;

    private Map<String,ThriftDocument> loaded_;
    private List<String> incudePaths_;

    private final static Pattern version_pattern_ = Pattern.compile("@version\\s+([0-9\\.]+)", Pattern.CASE_INSENSITIVE);
    private final static Pattern annotation_pattern_ = Pattern.compile("@(\\w+)\\s*(.*)\\s*[\\r\\n]?", Pattern.CASE_INSENSITIVE);

    static class UnknownType extends ThriftType
    {
        ThriftPackage used_in_package;
        String name;

        List<Object> usedin;

        @Override
        public boolean valid()
        {
            return false;
        }
    }

    private ThriftType tryGetRealType( ThriftType t  )
    {
        if ( t != null )
        {
            ThriftType rt = t.getRealType();
            if ( rt != null )
                return rt;
        }
        return t;
    }

    private void removeReferenceTypes( ThriftField field )
    {
        field.type_ = tryGetRealType( field.type_ );
    }

    private void removeReferenceTypes( List<ThriftField> fields )
    {
        if ( fields != null )
            for ( ThriftField f : fields ) removeReferenceTypes(f);
    }

    /**
     * Removes any TypeRef or TypeDef types and exchange the references in all structures.
     */
    private void removeReferenceTypes( ThriftDocument doc )
    {
        Iterator<Entry<String, ThriftType> > typeMapIt = doc.all_types_.entrySet().iterator();
        while ( typeMapIt.hasNext() )
        {
            ThriftType t = typeMapIt.next().getValue();
            if ( t instanceof ThriftTypeRef || t instanceof ThriftTypedef)
            {
                typeMapIt.remove();
            }
            else if ( t instanceof ThriftListType )
            {
                ((ThriftListType)t).value_type_ = tryGetRealType(((ThriftListType)t).value_type_);
            }
            else if ( t instanceof ThriftMapType )
            {
                ThriftMapType mt = (ThriftMapType)t;
                mt.key_type_   = tryGetRealType(mt.key_type_);
                mt.value_type_ = tryGetRealType(mt.value_type_);
            }
            else if ( t instanceof ThriftSetType )
            {
                ((ThriftSetType)t).value_type_ = tryGetRealType(((ThriftSetType)t).value_type_);
            }
            else if ( t instanceof ThriftUnionType )
            {
                removeReferenceTypes( ((ThriftUnionType)t).fields_ );
            }
            else if ( t instanceof ThriftStructType )
            {
                removeReferenceTypes( ((ThriftStructType)t).fields_ );
            }
        }

        for ( ThriftPackage pk : doc.all_packages_ )
        {
            Iterator<ThriftType> typeIt = pk.types_.iterator();
            while ( typeIt.hasNext() )
            {
                ThriftType t = typeIt.next();
                if ( t instanceof ThriftTypeRef || t instanceof ThriftTypedef)
                {
                    typeIt.remove();
                }
            }
        }

        for ( ThriftService s : doc.all_services_ )
        {
            for ( ThriftFunction f : s.functions_)
            {
                removeReferenceTypes( f.exceptions_ );
                removeReferenceTypes( f.parameters_ );
                f.return_type_ = tryGetRealType(f.return_type_);
            }
        }
    }

    private  void collect_references( ThriftDocument doc )
    {
        if ( doc != null )
        {
            for (int i=0 ; i<doc.includes_.size() ; ++i)
                 collect_references( doc.includes_.get(i).doc_ );

            for ( ThriftType tp : doc.all_types_.values() )
            {
                if ( tp instanceof ThriftTypeRef )
                {
                    ThriftTypeRef tpr = (ThriftTypeRef)tp;
                    if ( null == tpr.resolvedType_ )
                    {
                        doc.unresolved_types_.put( tpr.declaredName_, tpr);
                    }
                }
                else
                {
                    global_types_.put( tp.name_fully_qualified_, tp);
                }
            }

            for ( ThriftService sv : doc.all_services_ )
            {
                if ( sv.extended_service_ != null && sv.extended_service_.resolvedService_ == null )
                {
                    doc.unresolved_services_.add( sv.extended_service_ );
                }
                global_services_.put( sv.name_fully_qualified_, sv);
            }
        }
    }

    private void resolve_all( ThriftDocument doc, boolean bExchangeTypeReferences )
    {
        if ( doc != null )
        {
            for (int i=0 ; i<doc.includes_.size() ; ++i)
                 resolve_all( doc.includes_.get(i).doc_, bExchangeTypeReferences );

            Iterator<ThriftTypeRef> it = doc.unresolved_types_.values().iterator();
            while ( it.hasNext() )
            {
                ThriftTypeRef tpr = it.next();

                if ( null == tpr.resolvedType_ )
                {
                    // Initial scope where the type was used.
                    ThriftPackage scopePackage = tpr.package_;
                    // Go up the hierarchy and try to find the type in global registry.
                    while ( tpr.resolvedType_ == null && scopePackage != null )
                    {
                      tpr.resolvedType_ = global_types_.get( scopePackage.name_fully_qualified_+"."+tpr.declaredName_ );
                      scopePackage = scopePackage.parent_;
                    }
                    // if still not found, try as fully qualified type
                    if ( null == tpr.resolvedType_)
                       tpr.resolvedType_ = global_types_.get( tpr.declaredName_ );

                    if ( null != tpr.resolvedType_)
                    {
                        tpr.package_ = tpr.resolvedType_.package_;
                        tpr.name_ = tpr.resolvedType_.name_;
                        tpr.name_fully_qualified_ = tpr.resolvedType_.name_fully_qualified_;
                        it.remove();
                    }
                }
            }
            if ( bExchangeTypeReferences )
                removeReferenceTypes(doc);

            Iterator<ThriftServiceRef> itServ = doc.unresolved_services_.iterator();
            while ( itServ.hasNext() )
            {
                ThriftServiceRef svr = itServ.next();

                if ( null == svr.resolvedService_ )
                    svr.resolvedService_ = global_services_.get(svr.declaredName_ );
                if ( null != svr.resolvedService_)
                {
                    itServ.remove();
                }
            }
        }
    }

    private void loadIncludesInternal( ThriftDocument doc )
    {
        Path docFile = doc.ospath_;

        for (int i=0 ; i<doc.includes_.size() ; ++i)
        {
            ThriftInclude ic = doc.includes_.get(i);
            if ( null == ic.doc_ )
            {
                Path found = null;
                try {
                    Path bf = docFile.getParent();
                    final String icSubPath = ic.path_.replace('\\', File.separatorChar);

                    while ( null != bf)
                    {
                        Path p  = bf.resolve(  icSubPath );
                        if ( Files.exists(p) )
                        {
                            found = p;
                            break;
                        }
                        bf = bf.getParent();
                    }
                    if ( found == null && incudePaths_ != null )
                    {
                        for ( String incPath : incudePaths_ )
                        {
                            if ( !incPath.isEmpty() )
                            {
                                if ( incPath.charAt(incPath.length()-1) != File.separatorChar)
                                {
                                    incPath = incPath + File.separatorChar;
                                }
                                incPath += icSubPath;
                                Path p = getPath(incPath);
                                if ( Files.exists(p) )
                                {
                                    found = p;
                                    break;
                                }
                            }
                        }
                    }
                    if ( found != null )
                    {
                        ic.ospath_ = found;
                        final String uriS = ic.ospath_.toUri().toString();
                        ic.doc_ = loaded_.get(uriS);
                        if ( ic.doc_ == null )
                        {
                            ic.doc_ = loadDocument( ic.ospath_ );
                            loaded_.put(uriS, ic.doc_ );
                        }
                    }
                }
                catch (IOException ex)
                {
                  ex.printStackTrace();
                }
            }
        }
        for (int i=0 ; i<doc.includes_.size() ; ++i)
        {
            ThriftInclude ic = doc.includes_.get(i);
            if ( null != ic.doc_ )
            {
                loadIncludesInternal( ic.doc_ );
            }
        }

    }

    // Gets the name of the document from the file path.
    private static String getDocumentName( String ospath )
    {
       File f = new File(ospath);
       String name = f.getName();
       int sepidx = name.indexOf('.');
       if ( sepidx >= 0 )
         name = name.substring(0, sepidx );
       return name;
    }

    /**
     * Merge two list of types.
     * Existing resolves types are not overwritten.
     */
    private static void mergeTypes( Collection<ThriftType> dst, Collection<ThriftType> src)
    {
        HashMap<String, ThriftType> types = new HashMap<>(dst.size()+src.size());
        for ( ThriftType t : dst)
            types.put(t.name_fully_qualified_, t);
        for ( ThriftType t : src)
        {
            ThriftType tt = types.get(t.name_fully_qualified_);
            if ( tt != null)
            {
                if ( tt instanceof ThriftTypedef )
                {
                    ThriftTypedef tdef = (ThriftTypedef)tt;
                    if (tdef.reftype_ == null )
                    {
                        tdef.reftype_ = t.getRealType();
                        dst.add(t);
                    }
                }
                else if ( tt instanceof ThriftTypeRef )
                {
                    ThriftTypeRef tref = (ThriftTypeRef)tt;
                    if (tref.resolvedType_ == null )
                    {
                        tref.resolvedType_ = t.getRealType();
                        dst.add(t);
                    }
                }
            }
            else
            {
                dst.add(t);
                types.put(t.name_fully_qualified_, t);
            }
        }
    }

    /**
     * Returns true if both scopes are the same name-space.
     */
    private static boolean inSameScope(ThriftScope sp1, ThriftScope sp2)
    {
        return (sp1.name_fully_qualified_ == null || sp1.name_fully_qualified_.isEmpty())
               ? (sp2.name_fully_qualified_ == null || sp2.name_fully_qualified_.isEmpty())
               : sp1.name_fully_qualified_.equals(sp2.name_fully_qualified_);
    }


   /**
    * Merges scopes if both represent the same name-scope.
    */
    private static boolean merge( ThriftScope dst, ThriftScope src )
    {
        if ( dst == null || src == null || src == dst || !inSameScope(dst,src))
            return false;

        if ( src.services_ != null && !src.services_.isEmpty())
        {
            if ( dst.services_ != null && !dst.services_.isEmpty())
            {
                HashMap<String, ThriftService> services = new HashMap<>(dst.services_.size()+src.services_.size());
                for ( ThriftService s : dst.services_)
                    services.put(s.name_fully_qualified_, s);

                for ( ThriftService s : src.services_)
                {
                    if ( !services.containsKey(s.name_fully_qualified_))
                    {
                        dst.services_.add(s);
                        services.put(s.name_fully_qualified_, s);
                    }
                }
            }
            else
                dst.services_ = src.services_;
        }

        if ( src.types_ != null && !src.types_.isEmpty())
        {
            if ( dst.types_ != null && !dst.types_.isEmpty())
            {
                mergeTypes( dst.types_, src.types_ );
            }
            else
                dst.types_ = src.types_;
        }
        return true;
    }

    /**
     * Merges an other package into this one if both represent the same name-scope.
     */
    private static boolean merge( ThriftPackage dst, ThriftPackage src  )
    {
        if ( !merge((ThriftScope)dst,(ThriftScope)src) ) return false;

        if ( src.subpackages_ != null )
        {
            if( dst.subpackages_ != null)
            {
                HashMap<String,ThriftPackage> ip = new HashMap<>();
                for ( ThriftPackage p : dst.subpackages_)
                    ip.put(p.name_fully_qualified_, p);

                for ( ThriftPackage p :  src.subpackages_)
                {
                    ThriftPackage tp = ip.get(p.name_fully_qualified_);
                    if ( tp == null )
                        dst.subpackages_.add(p);
                    else
                    {
                        merge( tp, p );
                    }
                }
            }
            else
                dst.subpackages_=src.subpackages_;
        }

        return true;
    }

   /**
    * Merges an other document into this one.
    */
    private static boolean merge( ThriftDocument dst, ThriftDocument src )
    {
        if ( !merge((ThriftScope)dst, (ThriftScope)src)) return false;

        if ( src.includes_ != null )
        {
            if ( dst.includes_ != null )
            {
                // Merge includes:
                //  try to idententify identical includes and take over loaded documents.

                HashMap<String,ThriftInclude> ibypath = new HashMap<>();
                HashMap<String,ThriftInclude> ibyval = new HashMap<>();
                for ( ThriftInclude i : dst.includes_ )
                {
                    if ( i.ospath_ != null ) ibypath.put(i.ospath_.toString(), i);
                    if ( i.path_ != null ) ibyval.put(i.path_, i);
                }

                for ( ThriftInclude i : src.includes_ )
                {
                    if ( i != null )
                    {
                        ThriftInclude ti = null;
                        if (i.ospath_ != null)
                        {
                            ti = ibypath.get( i.ospath_.toString() );
                        }
                        if ( ti == null && i.path_ != null )
                        {
                            ti = ibyval.get( i.path_.toString() );
                        }

                        if ( ti != null )
                        {
                           if ( ti.doc_ == null ) ti.doc_ = i.doc_;
                           if ( ti.ospath_ != null ) ti.ospath_ = i.ospath_;
                        }
                        else
                        {
                            dst.includes_.add( i );
                            if ( i.ospath_ != null ) ibypath.put(i.ospath_.toString(), i);
                            if ( i.path_ != null ) ibyval.put(i.path_, i);
                        }
                    }
                }
            }
            else
                dst.includes_ = src.includes_;
        }

        if ( src.all_packages_ != null )
        {
            if ( dst.all_packages_ != null )
            {
                // try to idententify identical packages and merge data.
                HashMap<String,ThriftPackage> ip = new HashMap<>();
                for ( ThriftPackage p : dst.all_packages_)
                    ip.put(p.name_fully_qualified_, p);

                for ( ThriftPackage p :  src.all_packages_)
                {
                    ThriftPackage tp = ip.get(p.name_fully_qualified_);
                    if ( tp == null )
                    {
                        p.setDocument( dst );
                        dst.all_packages_.add(p);
                    }
                    else
                    {
                        merge( tp, p );
                    }
                }
            }
            else
            {
                dst.all_packages_ = src.all_packages_;
                for ( ThriftPackage p : dst.all_packages_ )
                    p.setDocument(dst);
            }
        }

        if ( src.all_services_byname_ != null )
        {
            // all_services_ and all_services_byname_ should always be in sync.
            // Handle them as one:

            if ( dst.all_services_byname_ != null )
            {
                if (dst.all_services_== null) dst.all_services_= new ArrayList<>();

                for ( Map.Entry<String,ThriftService> k : src.all_services_byname_.entrySet())
                {
                    if (!dst.all_services_byname_.containsKey(k.getKey()))
                    {
                        final ThriftService s = k.getValue();
                        s.setDocument(dst);
                        dst.all_services_byname_.put(k.getKey(), s );
                        dst.all_services_.add(s);
                    }
                }
            }
            else
            {
                // Take over.
                dst.all_services_byname_ = src.all_services_byname_;
                dst.all_services_ = src.all_services_;
                for ( ThriftService s : dst.all_services_ )
                    s.setDocument(dst);
            }

        }

        if (src.all_types_ != null )
        {
            ArrayList<ThriftType> alltypes = new ArrayList<>(dst.all_types_.size());
            alltypes.addAll( dst.all_types_.values() );

            ArrayList<ThriftType> doc_alltypes = new ArrayList<>(src.all_types_.size());
            doc_alltypes.addAll( src.all_types_.values() );

            mergeTypes( alltypes, doc_alltypes);

            for ( ThriftType t : alltypes )
            {
                t.setDocument(dst);
                dst.all_types_.put(t.name_fully_qualified_, t);
            }
        }

        if (src.unresolved_types_ != null )
        {
            // A simple merge will not work if same types are in both list.
            // We need to link the type-refs.
            if ( dst.unresolved_types_ != null )
            {
                for ( Entry<String,ThriftTypeRef> entry : src.unresolved_types_.entrySet() )
                {
                    ThriftTypeRef org = dst.unresolved_types_.get(entry.getKey());
                    if ( org == null )
                    {
                        dst.unresolved_types_.put(entry.getKey(),entry.getValue());
                        entry.getValue().setDocument(dst);
                    }
                    else
                    {
                        entry.getValue().resolvedType_ = org;
                    }
                }
            }
            else
            {
                dst.unresolved_types_ = src.unresolved_types_;
                for ( ThriftTypeRef t : dst.unresolved_types_.values() )
                    t.setDocument(dst);
            }
        }

        if (src.unresolved_services_ != null )
        {
            if ( dst.unresolved_services_ != null )
                dst.unresolved_services_.addAll(src.unresolved_services_ );
            else
                dst.unresolved_services_ = src.unresolved_services_;
        }
        return true;
    }

    /**
     * Parses a document from byte buffer.
     * @param content Textual thrift-document.
     * @param name Name of document.
     */
    private ThriftDocument parseDocument( byte[] content, String name )
    {
        ThriftDocument doc = null;

        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData( content, 0, content.length);
        detector.dataEnd();
        String charsetName = detector.getDetectedCharset();

        Charset charset;
        if (charsetName != null && Charset.isSupported(charsetName))
            charset = Charset.forName(charsetName);
        else
            charset = StandardCharsets.UTF_8;

        if (charset != null)
        {
           try
           {
               doc = generateModel(name, new ThriftLexer(new ANTLRReaderStream(
                   new InputStreamReader( new ByteArrayInputStream(content),charset ))));
           } catch ( IOException e )
           {
               System.err.println( name+": internal i/o error: "+e.getMessage());
           }
        }
        else
        {
           System.err.println( name+": failed to detect encoding.");
        }

        return doc;
    }

    private synchronized ThriftDocument generateModel( String name, ThriftLexer lex )
    {
        tokensource_ = new ThriftCommentTokenSource( lex, ThriftLexer.DEFAULT_TOKEN_CHANNEL, ThriftLexer.COMMENT );
        tokens_ = new CommonTokenStream(tokensource_);
        parser_ = new ThriftParser(tokens_);

        ThriftDocument d = null;
        doc_ = null;
        try
        {
            d = gen_document( name, (CommonTree)parser_.document().getTree() );
        }
        catch (RecognitionException ex)
        {

          ex.printStackTrace();
        }

        tokens_ = null;
        tokensource_  = null;
        return d;
    }

    private ThriftDocument gen_document( String name, CommonTree dt )
    {
        ThriftDocument d = new ThriftDocument();

        current_package_ = null;
        doc_ = d;
        d.column_ = 0;
        d.line_   = 0;
        d.name_ = name;
        d.name_fully_qualified_ = name;
        d.services_     = new ArrayList<>();
        d.all_services_ = new ArrayList<>();
        d.all_services_byname_= new HashMap<>();
        d.all_packages_ = new ArrayList<>();
        d.includes_ = new ArrayList<>();
        d.unresolved_types_= new HashMap<>();
        d.unresolved_services_= new ArrayList<>();
        d.types_ = new ArrayList<>();
        d.all_types_ = new HashMap<String, ThriftType>();
        // Add all default types to list
        d.all_types_.put(ThriftType.VOID  .name_fully_qualified_, ThriftType.VOID );
        d.all_types_.put(ThriftType.BOOL  .name_fully_qualified_, ThriftType.BOOL );
        d.all_types_.put(ThriftType.INT8  .name_fully_qualified_, ThriftType.INT8 );
        d.all_types_.put(ThriftType.INT16 .name_fully_qualified_, ThriftType.INT16 );
        d.all_types_.put(ThriftType.INT32 .name_fully_qualified_, ThriftType.INT32 );
        d.all_types_.put(ThriftType.INT64 .name_fully_qualified_, ThriftType.INT64 );
        d.all_types_.put(ThriftType.UINT8 .name_fully_qualified_, ThriftType.UINT8 );
        d.all_types_.put(ThriftType.UINT16.name_fully_qualified_, ThriftType.UINT16 );
        d.all_types_.put(ThriftType.UINT32.name_fully_qualified_, ThriftType.UINT32 );
        d.all_types_.put(ThriftType.UINT64.name_fully_qualified_, ThriftType.UINT64 );
        d.all_types_.put(ThriftType.DOUBLE.name_fully_qualified_, ThriftType.DOUBLE );
        d.all_types_.put(ThriftType.STRING.name_fully_qualified_, ThriftType.STRING );
        d.all_types_.put(ThriftType.BINARY.name_fully_qualified_, ThriftType.BINARY );

        parse_body( dt, 0 );

        // Local reference resolution
        //   Types
        Iterator<ThriftTypeRef> it = doc_.unresolved_types_.values().iterator();
        while ( it.hasNext() )
        {
            ThriftTypeRef tpr = it.next();

            if ( null == tpr.resolvedType_ )
            {
                // Try to find it in qualified names
                tpr.resolvedType_ = resolve_type(tpr.declaredName_ );
                if ( null == tpr.resolvedType_ && null != tpr.package_)
                {
                    // Try to find it in original scope
                    tpr.resolvedType_ = tpr.package_.findTypeInPackage( tpr.declaredName_ );
                }
                if ( null != tpr.resolvedType_)
                {
                    tpr.package_ = tpr.resolvedType_.package_;
                    tpr.name_ = tpr.resolvedType_.name_;
                    tpr.name_fully_qualified_ = tpr.resolvedType_.name_fully_qualified_;

                    it.remove();
                }
            }
        }
        //   Service references
        Iterator<ThriftServiceRef> itSv = doc_.unresolved_services_.iterator();
        while ( itSv.hasNext() )
        {
            ThriftServiceRef svr = itSv.next();

            if ( null == svr.resolvedService_ )
            {
                // Try to find it in qualified names
                svr.resolvedService_ = doc_.all_services_byname_.get( svr.declaredName_ );
                if ( null == svr.resolvedService_ && null != svr.declarationPackage_)
                {
                    // Try to find it in original scope
                    svr.resolvedService_ = svr.declarationPackage_.findServiceInPackage( svr.declaredName_ );
                }
                if ( null != svr.resolvedService_)
                {
                    itSv.remove();
                }
            }
        }

        doc_ = null;
        return d;
    }

    private void add_type_to_scope( ThriftType typ )
    {
        typ.setDocument(doc_);
        doc_.all_types_.put( typ.name_fully_qualified_, typ );
        if ( null != current_package_)
           current_package_.types_.add(typ);
        else
           doc_.types_.add(typ);

    }

    private void add_comment( CommonTree dt, ThriftObject obj )
    {
        obj.comment_ = tokensource_.collectComment( dt.getLine()-1 );

        // Try to locate version information
        if ( obj.comment_ != null )
        {
           Matcher m = version_pattern_.matcher(obj.comment_);
           if ( m.find() && m.groupCount()>0 )
           {
              obj.version_ = m.group(1);
           }
           // Try to locate @deprecated
           if ( obj.comment_.contains( "@deprecated" ) )
           {
               obj.deprecated_ = true;
           }

           Matcher ma = annotation_pattern_.matcher(obj.comment_);
           while ( ma.find() )
           {
               if ( ma.groupCount()>=2 )
               {
                   if ( obj.annotations_ == null ) obj.annotations_ = new HashMap<>();
                   obj.annotations_.put( ma.group(1), ma.group(2).trim() );
               }
           }
        }
    }

    private ThriftType resolve_type( String name )
    {
        ThriftType tp = doc_.all_types_.get( name );
        if ( null != tp ) return tp;

        // Go up the package hierachy
        ThriftPackage p = current_package_;
        if ( p != null )
        {
            while( p != null )
            {
                final String fqname = get_fully_qualifiedname(p, name);
                tp = doc_.all_types_.get( fqname );
                if ( null != tp ) return tp;
                p = p.parent_;
            }
            return null;
        }
        else
        {
            final String fqname = get_fully_qualifiedname(p, name);
            return doc_.all_types_.get( fqname );
        }

    }

    private ThriftType find_type( CommonTree dt )
    {
        final String name = dt.getText();
        ThriftType tp = resolve_type(name);
        if ( null == tp )
        {
            tp = doc_.unresolved_types_.get( name );
            if ( null == tp )
            {
                ThriftTypeRef tpr = new ThriftTypeRef();
                add_typeheaderinfo(dt, tpr);
                tpr.setDocument(doc_);
                tpr.declaredName_ = name;
                tpr.package_ = current_package_;
                doc_.unresolved_types_.put( name , tpr);
                tp = tpr;
            }
        }
        return tp;
    }

    private long get_integer( CommonTree dt )
    {
        if ( null != dt )
        {
            try
            {
                switch (dt.getType() )
                {
                    case ThriftParser.INTEGER: return Integer.parseInt( dt.getText() );
                    case ThriftParser.FIELD_ID_:
                        if ( dt.getChildCount() > 0)
                           return get_integer( (CommonTree)dt.getChild(0) );
                        break;
                    case ThriftParser.HEX_INTEGER:
                        String hx = dt.getText();
                        if ( hx.startsWith( "0x" )) hx = hx.substring(2);
                        return Long.parseLong( hx , 16 );
                }
            }
            catch (NumberFormatException nfe )
            {
            }
        }
        return Long.MIN_VALUE;
    }

    private ThriftFunctionMode get_function_mode( CommonTree dt)
    {
       if ( dt.getChildCount() > 3 )
       {
          // If a function mode was declared, it was re-written to child #3
          CommonTree ct = (CommonTree)dt.getChild(3);
          switch (ct.getType())
          {
             case ThriftParser.EVENT:    return ThriftFunctionMode.EVENT;
             case ThriftParser.ONEWAY:   return ThriftFunctionMode.ONEWAY;
             case ThriftParser.ASYNC:    return ThriftFunctionMode.ASYNC;
             case ThriftParser.DEFERRED: return ThriftFunctionMode.DEFERRED;
             case ThriftParser.PROP_GSC: return ThriftFunctionMode.PROPERTY_GET_SET_CHANGED;
             case ThriftParser.PROP_GS:  return ThriftFunctionMode.PROPERTY_GET_SET;
             case ThriftParser.PROP_GC:  return ThriftFunctionMode.PROPERTY_GET_CHANGED;
             default:                    return ThriftFunctionMode.NONE;
          }
       }
       return ThriftFunctionMode.NONE;
    }


    private String get_identifier( CommonTree dt )
    {
        CommonTree idT = (CommonTree)dt.getFirstChildWithType(ThriftParser.IDENTIFIER);
        return ( null != idT ) ? idT.getText() : "";

    }

    private ThriftInclude gen_include( CommonTree dt )
    {
        ThriftInclude i = new ThriftInclude();
        CommonTree lt = (CommonTree)dt.getFirstChildWithType(ThriftParser.LITERAL);
        if ( null != lt )
            i.path_ = lt.getText();
        else
            i.path_ = "";
        return i;
    }


    private ThriftPackage gen_package( CommonTree dt )
    {
        ThriftPackage p = new ThriftPackage();
        p.setDocument(doc_);
        p.parent_ = current_package_;
        p.name_ = get_identifier( dt );
        p.name_fully_qualified_ = ((null != current_package_)?current_package_.name_fully_qualified_+"." : "") +p.name_;
        p.line_  = dt.getLine() -1 ;
        p.column_= dt.getCharPositionInLine();

        add_comment( dt, p );

        current_package_ = p;

        parse_body(dt,1);
        current_package_ = current_package_.parent_;
        return p;
    }
    private void parse_body( CommonTree dt, int startIndex )
    {
        for (int i = startIndex ; i<dt.getChildCount() ; ++i )
        {
            CommonTree ct = (CommonTree)dt.getChild(i);
            switch ( ct.getType() )
            {
                case ThriftParser.PACKAGE:
                    ThriftPackage np = gen_package(ct);
                    if ( null != current_package_ )
                       current_package_.subpackages_.add( np );
                    doc_.all_packages_.add( np );
                    break;
                case ThriftParser.SERVICE:
                    ThriftService serv = gen_service( ct );
                    if ( null != current_package_ )
                       current_package_.services_.add(serv);
                    else
                       doc_.services_.add(serv);
                    doc_.all_services_.add(serv);
                    doc_.all_services_byname_.put(serv.name_fully_qualified_, serv);
                    break;
                case ThriftParser.ENUM:
                    gen_enum( ct );
                    break;
                case ThriftParser.STRUCT:
                    gen_struct( ct );
                    break;
                case ThriftParser.UNION:
                    gen_union( ct );
                    break;
                case ThriftParser.TYPEDEF:
                    gen_typedef( ct );
                    break;
                case ThriftParser.INCLUDE:
                    doc_.includes_.add(gen_include( ct ));
                    break;
                case ThriftParser.EXCEPTION:
                    gen_exception( ct );
                    break;
            }

        }
    }

    private String get_fully_qualifiedname( ThriftPackage p, String name )
    {
        StringBuilder sb = new StringBuilder(100);
        if ( null != p)
           // [DAI Extension]: "packages" are used as parent namespace
           sb.append( p.name_fully_qualified_);
        else
           // All content is identified by document name.
           sb.append( doc_.name_ );
        if ( 0 < sb.length() ) sb.append('.');
        sb.append(name);
        return sb.toString();
    }

    private String get_fully_qualifiedname( String name )
    {
        return get_fully_qualifiedname( current_package_, name );
    }

    private void add_headerinfo( CommonTree dt, ThriftType tp )
    {
        tp.package_ = current_package_;
        tp.line_  = dt.getLine() - 1;
        tp.column_= dt.getCharPositionInLine();

        add_comment(dt, tp);
    }

    private void add_typeheaderinfo( CommonTree dt, ThriftType tp )
    {
        tp.name_ = get_identifier(dt);
        tp.name_fully_qualified_ = tp.name_.isEmpty() ? tp.name_ : get_fully_qualifiedname( tp.name_ );
        add_headerinfo( dt, tp );
    }

    private ThriftListType gen_listtype( CommonTree dt )
    {
        ThriftListType lt = new ThriftListType();
        lt.name_ = lt.name_fully_qualified_ = "";
        add_headerinfo(dt, lt);
        lt.setDocument(doc_);
        if ( 0 < dt.getChildCount() )
            lt.value_type_ = gen_fieldtype( (CommonTree)dt.getChild(0) );
       return lt;
    }


    private ThriftMapType gen_maptype( CommonTree dt )
    {
        ThriftMapType lt = new ThriftMapType();
        lt.name_ = lt.name_fully_qualified_ = "";
        add_headerinfo(dt, lt);
        lt.setDocument(doc_);
        if ( 1 < dt.getChildCount() )
        {
            lt.key_type_ = gen_fieldtype( (CommonTree)dt.getChild(0) );
            lt.value_type_ = gen_fieldtype( (CommonTree)dt.getChild(1) );
        }
        return lt;
    }

    private ThriftSetType gen_settype( CommonTree dt )
    {
        ThriftSetType lt = new ThriftSetType();
        lt.name_ = lt.name_fully_qualified_ = "";
        add_headerinfo(dt, lt);
        lt.setDocument(doc_);
        if ( 0 < dt.getChildCount() )
        {
            lt.value_type_ = gen_fieldtype( (CommonTree)dt.getChild(0) );
        }
        return lt;
    }

    private void gen_typedef(CommonTree dt )
    {
        ThriftTypedef td = new ThriftTypedef();
        add_typeheaderinfo(dt, td);
        add_type_to_scope( td );
        if ( 1 < dt.getChildCount() )
            td.reftype_ = gen_fieldtype( (CommonTree)dt.getChild(1));
    }

    private void gen_enum( CommonTree dt )
    {
        ThriftEnum en = new ThriftEnum();
        en.values_ = new ArrayList<>();
        add_typeheaderinfo( dt, en );

        int autoVal = 0;

        for (int i = 1 ; i<dt.getChildCount() ; ++i )
        {
            CommonTree ct = (CommonTree)dt.getChild(i);
            switch ( ct.getType() )
            {
                case ThriftParser.IDENTIFIER:
                    ThriftEnumValue env = new ThriftEnumValue();
                    env.name_ = ct.getText();
                    if ( 0 < ct.getChildCount() )
                    {
                         long vi = get_integer((CommonTree)ct.getChild(0));
                         if ( vi >= Integer.MIN_VALUE || vi <= Integer.MAX_VALUE)
                             autoVal = (int)vi;
                    }
                    env.value_ = autoVal++;
                    en.values_.add(env);
                    break;
            }
        }
        add_type_to_scope( en );
    }

    private void gen_exception( CommonTree dt )
    {
        ThriftExceptionType e = new ThriftExceptionType();
        add_typeheaderinfo( dt, e );

        e.fields_ = new ArrayList<>();
        add_type_to_scope(e);

        for (int i = 1 ; i<dt.getChildCount() ; ++i )
        {
            CommonTree ct = (CommonTree)dt.getChild(i);
            switch ( ct.getType() )
            {
                case ThriftParser.FIELD_:
                    e.fields_.add( gen_field( ct ));
                    break;
            }
        }
    }

    private void gen_union( CommonTree dt )
    {
        ThriftUnionType u = new ThriftUnionType();
        add_typeheaderinfo( dt, u );

        u.fields_ = new ArrayList<>();
        add_type_to_scope(u);

        for (int i = 1 ; i<dt.getChildCount() ; ++i )
        {
            CommonTree ct = (CommonTree)dt.getChild(i);
            switch ( ct.getType() )
            {
                case ThriftParser.FIELD_:
                    u.fields_.add( gen_field( ct ));
                    break;
            }
        }
    }

    private void gen_struct( CommonTree dt )
    {
        ThriftStructType s = new ThriftStructType();
        add_typeheaderinfo( dt, s );

        s.fields_ = new ArrayList<>();
        add_type_to_scope(s);

        for (int i = 1 ; i<dt.getChildCount() ; ++i )
        {
            CommonTree ct = (CommonTree)dt.getChild(i);
            switch ( ct.getType() )
            {
                case ThriftParser.FIELD_:
                    s.fields_.add( gen_field( ct ));
                    break;
            }
        }
    }
    private ThriftField gen_field( CommonTree dt )
    {
        ThriftField f = new ThriftField();
        f.setDocument(doc_);
        f.name_ = get_identifier( dt );
        add_comment( dt, f );

        if ( 2 <= dt.getChildCount() )
            f.type_ = gen_fieldtype( (CommonTree)dt.getChild(1) );
        f.id_ = (int)get_integer( (CommonTree)dt.getFirstChildWithType( ThriftParser.FIELD_ID_ ) );
        return f;
    }

    private ThriftType gen_fieldtype( CommonTree dt )
    {
        ThriftType type = null;

        switch ( dt.getType() )
        {
            case ThriftParser.VOID:          return ThriftType.VOID;
            case ThriftParser.TYPE_BOOL:     return ThriftType.BOOL;
            case ThriftParser.TYPE_BYTE:     return ThriftType.INT8;
            case ThriftParser.TYPE_I16:      return ThriftType.INT16;
            case ThriftParser.TYPE_I32:      return ThriftType.INT32;
            case ThriftParser.TYPE_I64:      return ThriftType.INT64;
            case ThriftParser.TYPE_DOUBLE:   return ThriftType.DOUBLE;
            case ThriftParser.TYPE_STRING:   return ThriftType.STRING;
            case ThriftParser.TYPE_BINARY:   return ThriftType.BINARY;
            case ThriftParser.TYPE_U8:       return ThriftType.UINT8;
            case ThriftParser.TYPE_U16:      return ThriftType.UINT16;
            case ThriftParser.TYPE_U32:      return ThriftType.UINT32;
            case ThriftParser.TYPE_U64:      return ThriftType.UINT64;
            case ThriftParser.SERVICE_PTR_TYPE: return ThriftType.SERVICE;
            case ThriftParser.LIST:          return gen_listtype(dt);
            case ThriftParser.MAP:           return gen_maptype(dt);
            case ThriftParser.SET:           return gen_settype(dt);
            case ThriftParser.IDENTIFIER:    return find_type( dt );

        }

        return type;
    }


    private ThriftService gen_service( CommonTree dt )
    {
        ThriftService s = new ThriftService();
        s.setDocument(doc_);
        s.name_     = get_identifier( dt );
        s.name_fully_qualified_ = get_fully_qualifiedname( s.name_ ) ;
        s.package_  = current_package_;
        s.line_     = dt.getLine() - 1 ;
        s.column_   = dt.getCharPositionInLine();
        add_comment( dt, s );

        CommonTree dtExtends = (CommonTree)dt.getChild(1);
        if ( dtExtends.getChildCount() > 0 )
        {
            ThriftServiceRef sref = new ThriftServiceRef();
            sref.line_ = dtExtends.getLine() - 1 ;
            sref.column_ = dtExtends.getCharPositionInLine();
            sref.declaredName_ = get_identifier(dtExtends);
            sref.declarationPackage_ = current_package_;
            s.extended_service_ = sref;
        }

        for (int i = 2 ; i<dt.getChildCount() ; ++i )
        {
            CommonTree dtF = (CommonTree)dt.getChild(i);
            switch ( dtF.getType() )
            {
                case ThriftParser.METHOD_:
                    ThriftFunction f = gen_function(dtF);
                    s.functions_.add(f);
                    f.service_ = s;
                    break;
            }
        }

        return s;
    }

    private List<ThriftField> gen_throws(CommonTree dt)
    {
        ArrayList<ThriftField> p = new ArrayList<>();
        if ( dt != null )
        {
            for (int i = 0 ; i<dt.getChildCount() ; ++i )
            {
                CommonTree dtP = (CommonTree)dt.getChild(i);
                switch ( dtP.getType() )
                {
                    case ThriftParser.FIELD_:
                        p.add(gen_field(dtP));
                        break;
                }
            }
        }
        return p;
    }

    private List<ThriftField> gen_parameters(CommonTree dt )
    {
        ArrayList<ThriftField> p = new ArrayList<>();

        for (int i = 0 ; i<dt.getChildCount() ; ++i )
        {
            CommonTree dtP = (CommonTree)dt.getChild(i);
            switch ( dtP.getType() )
            {
                case ThriftParser.FIELD_:
                    p.add(gen_field(dtP));
                    break;
            }
        }
        return p;
    }

    private ThriftFunction gen_function( CommonTree dt )
    {
        ThriftFunction f = new ThriftFunction();
        f.setDocument(doc_);
        f.mode_ = get_function_mode(dt);
        f.name_ = get_identifier(dt);
        f.parameters_ = new ArrayList<>();
        f.line_  = dt.getLine() - 1;
        f.column_= dt.getCharPositionInLine();
        if ( 1 < dt.getChildCount() )
            f.return_type_ = gen_fieldtype((CommonTree)dt.getChild(1));

        add_comment( dt, f );
        f.parameters_ = gen_parameters((CommonTree)dt.getFirstChildWithType(ThriftParser.ARGS_));

        f.exceptions_ = gen_throws((CommonTree)dt.getFirstChildWithType(ThriftParser.THROWS));

        return f;
    }
}
