/* Copyright (c) 2015 Bernd Wengenroth
* Licensed under the MIT License.
* See LICENSE file for details.
*/
package bweng.thrift.parser;

import bweng.thrift.parser.model.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

/**
* Generates our Data model from Antlr parser results.
*/
public final class ThriftModelGenerator
{
    ThriftDocument doc_;
    // Current package [DAI Extension]
    ThriftPackage  current_package_;

    // All types not resolved so far
    public Map<String, ThriftType> global_types_;

    // All services not resolved so far
    public Map<String, ThriftService> global_services_;

    ThriftCommentTokenSource tokensource_;
    ThriftParser   parser_;
    TokenStream tokens_;

    Map<String,ThriftDocument> loaded_;

    Pattern version_pattern_ = Pattern.compile("@version\\s+([0-9\\.]+)", Pattern.CASE_INSENSITIVE);


    class UnknownType extends ThriftType
    {
        ThriftPackage used_in_package;
        String name;

        List<Object> usedin;
    }

    public synchronized void loadIncludes( ThriftDocument doc )
    {
        loaded_ = new HashMap<>();
        loadIncludesInternal( doc );
        loaded_.clear();

        global_types_   = new HashMap<>();
        global_services_= new HashMap<>();

        collect_references( doc );
        resolve_all( doc );

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
                    doc.unresolved_services_.put( sv.extended_service_.declaredName_, sv.extended_service_ );
                }
                global_services_.put( sv.name_fully_qualified_, sv);
            }
        }
    }

    private void resolve_all( ThriftDocument doc )
    {
        if ( doc != null )
        {
            for (int i=0 ; i<doc.includes_.size() ; ++i)
                 resolve_all( doc.includes_.get(i).doc_ );

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
            Iterator<ThriftServiceRef> itServ = doc.unresolved_services_.values().iterator();
            while ( itServ.hasNext() )
            {
                ThriftServiceRef svr = itServ.next();

                if ( null == svr.resolvedService_ )
                {
                    svr.resolvedService_ = global_services_.get(svr.declaredName_ );
                    if ( null != svr.resolvedService_)
                    {
                        itServ.remove();
                    }
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
                try {
                    Path bf = docFile.getParent();

                    while ( null != bf)
                    {
                        Path f = bf.resolve( ic.path_ );
                        if ( Files.exists(f) )
                        {
                            ic.ospath_ = f;
                            final String uriS = ic.ospath_.toUri().toString();
                            ic.doc_ = loaded_.get(uriS);
                            if ( ic.doc_ == null )
                            {
                                ic.doc_ = loadDocument( ic.ospath_ );
                                loaded_.put(uriS, ic.doc_ );
                            }
                            break;
                        }
                        bf = bf.getParent();
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
    public static String getDocumentName( String ospath )
    {
       File f = new File(ospath);
       String name = f.getName();
       int sepidx = name.indexOf('.');
       if ( sepidx >= 0 )
         name = name.substring(0, sepidx );
       return name;
    }

    static public Path getPath( String ospath )
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

    public synchronized ThriftDocument loadDocument( Path ospath ) throws IOException
    {
        ThriftDocument doc = null;

        String name = getDocumentName( ospath.toString() );
        doc = generateModel(name, new ThriftLexer(new ANTLRReaderStream(Files.newBufferedReader(ospath))));
        if ( doc != null )
        {
           doc.ospath_ = ospath;
        }
        return doc;
    }

    public synchronized ThriftDocument generateModel( String name, ThriftLexer lex )
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
        d.unresolved_services_= new HashMap<>();
        d.types_ = new ArrayList<>();
        d.all_types_ = new HashMap<String, ThriftType>();
        // Add all default types to list
        d.all_types_.put(ThriftType.VOID  .name_fully_qualified_, ThriftType.VOID );
        d.all_types_.put(ThriftType.BOOL  .name_fully_qualified_, ThriftType.BOOL );
        d.all_types_.put(ThriftType.INT8  .name_fully_qualified_, ThriftType.INT8 );
        d.all_types_.put(ThriftType.INT16 .name_fully_qualified_, ThriftType.INT16 );
        d.all_types_.put(ThriftType.INT32 .name_fully_qualified_, ThriftType.INT32 );
        d.all_types_.put(ThriftType.INT64 .name_fully_qualified_, ThriftType.INT64 );
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
        Iterator<ThriftServiceRef> itSv = doc_.unresolved_services_.values().iterator();
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

    private ThriftType find_type( String name )
    {
        ThriftType tp = resolve_type(name);
        if ( null == tp )
        {
            tp = doc_.unresolved_types_.get( name );
            if ( null == tp )
            {
                ThriftTypeRef tpr = new ThriftTypeRef();
                tpr.setDocument(doc_);
                tpr.declaredName_ = name;
                tpr.package_ = current_package_;
                doc_.unresolved_types_.put( name , tpr);
                tp = tpr;
            }
        }
        return tp;
    }

    private int get_integer( CommonTree dt )
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
                        return Integer.parseInt( hx , 16 );
                }
            }
            catch (NumberFormatException nfe )
            {
            }
        }
        return Integer.MIN_VALUE;
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


    private void add_typeheaderinfo( CommonTree dt, ThriftType tp )
    {
        tp.name_ = get_identifier(dt);
       tp.name_fully_qualified_ = get_fully_qualifiedname( tp.name_ );
        tp.package_ = current_package_;
        tp.line_  = dt.getLine() - 1;
        tp.column_= dt.getCharPositionInLine();

        add_comment(dt, tp);
    }

    private ThriftListType gen_listtype( CommonTree dt )
    {
        ThriftListType lt = new ThriftListType();
        lt.setDocument(doc_);
        if ( 0 < dt.getChildCount() )
            lt.value_type_ = gen_fieldtype( (CommonTree)dt.getChild(0) );
       return lt;
    }


    private ThriftMapType gen_maptype( CommonTree dt )
    {
        ThriftMapType lt = new ThriftMapType();
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
        lt.setDocument(doc_);
        if ( 0 < dt.getChildCount() )
        {
            lt.value_type_ = gen_fieldtype( (CommonTree)dt.getChild(1) );
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
                         int vi = get_integer((CommonTree)ct.getChild(0));
                         if ( vi != Integer.MIN_VALUE)
                            autoVal = vi;
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
        f.id_ = get_integer( (CommonTree)dt.getFirstChildWithType( ThriftParser.FIELD_ID_ ) );
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
            case ThriftParser.SERVICE_PTR_TYPE: return ThriftType.SERVICE;
            case ThriftParser.LIST:          return gen_listtype(dt);
            case ThriftParser.MAP:           return gen_maptype(dt);
            case ThriftParser.SET:           return gen_settype(dt);
            case ThriftParser.IDENTIFIER:    return find_type( dt.getText() );

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