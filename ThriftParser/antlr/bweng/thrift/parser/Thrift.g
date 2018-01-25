grammar Thrift;

options {
 language=Java;
 output=AST;
 ASTLabelType=CommonTree;
}

tokens {
    DOCUMENT_;
    EXTENDS_;
    DEFAULT_NAMESPACE_;
    CPP_INCLUDE_;
    FIELD_ID_;
    FIELD_;
    ENTRY_;
    REQUIREDNESS_;
    METHOD_;
    ARGS_;
    TYPES_;
    TYPE_;
    CPP_TYPE_;
}

@header {
package bweng.thrift.parser;
}

@lexer::header {
package bweng.thrift.parser;
}

@lexer::members {
    public static final int COMMENTS = 2;
}

INCLUDE : 'include' ;
PACKAGE : 'package' ;
SERVICE : 'service' ;
VOID    : 'void' ;
ENUM    : 'enum' ;
TYPEDEF : 'typedef' ;
EXTENDS : 'extends' ;
STRUCT  : 'struct' ;
UNION   : 'union' ;
EXCEPTION : 'exception' ;
DEFERRED: 'deferred' ;
EVENT   : 'event' ;
PROP_GSC: 'propertygsc' ;
PROP_GS : 'propertygs' ;
PROP_GC : 'propertygc' ;
ONEWAY  : 'oneway' ;
ASYNC   : 'async' ;
LIST    : 'list' ;
MAP     : 'map' ;
SET     : 'set' ;
THROWS  : 'throws' ;
REQUIRED: 'required' ; 
OPTIONAL: 'optional' ;
SENUM   : 'senum' ;
CONST   : 'const' ;
NAMESPACE: 'namespace' ;
SERVICE_PTR_TYPE: 'service_ptr' ;

LCURLY  : '{' ;
RCURLY  : '}' ;
ASSIGN  : '=' ;
COLON   : ':' ;

document
    : header* definition* EOF -> ^(DOCUMENT_ header* definition*)
    ;


header
    : include | namespace | cpp_include
    ;

include
    : INCLUDE LITERAL -> ^(INCLUDE LITERAL)
    ;

dpackage
    : PACKAGE k=IDENTIFIER LCURLY definition* RCURLY list_separator? -> ^(PACKAGE $k definition*) 
    ;

namespace
    : NAMESPACE '*' (v=IDENTIFIER | v=LITERAL) -> ^(DEFAULT_NAMESPACE_ $v)
    | NAMESPACE k=IDENTIFIER (v=IDENTIFIER | v=LITERAL) -> ^(NAMESPACE $k $v)
    | 'cpp_namespace' IDENTIFIER -> ^(NAMESPACE IDENTIFIER["cpp"] IDENTIFIER)
    | 'php_namespace' IDENTIFIER -> ^(NAMESPACE IDENTIFIER["php"] IDENTIFIER)
    ;

cpp_include
    : 'cpp_include' LITERAL -> ^(CPP_INCLUDE_ LITERAL)
    ;


definition
    : dpackage | const_rule | typedef | enum_rule | senum | struct | union | exception | service
    ;

const_rule
    : CONST field_type IDENTIFIER ASSIGN const_value list_separator?
        -> ^(CONST IDENTIFIER field_type const_value)
    ;

typedef
    : TYPEDEF field_type IDENTIFIER list_separator? -> ^(TYPEDEF IDENTIFIER field_type)
    ;

enum_rule
    : ENUM IDENTIFIER LCURLY enum_field* RCURLY list_separator? -> ^(ENUM IDENTIFIER enum_field*)
    ;

enum_field
    : IDENTIFIER ('=' integer)? list_separator? -> ^(IDENTIFIER integer?)
    ;

senum
    : SENUM IDENTIFIER LCURLY (LITERAL list_separator?)* RCURLY -> ^(SENUM IDENTIFIER LITERAL*)
    ;

struct
    : STRUCT IDENTIFIER LCURLY field* RCURLY type_annotations? list_separator? -> ^(STRUCT IDENTIFIER field* type_annotations?)
    ;

union
    : UNION IDENTIFIER LCURLY field* RCURLY type_annotations? -> ^(UNION IDENTIFIER field* type_annotations?)
    ;

exception
    : EXCEPTION IDENTIFIER LCURLY field* RCURLY type_annotations? -> ^(EXCEPTION IDENTIFIER field* type_annotations?)
    ;

service
    : SERVICE s=IDENTIFIER (EXTENDS e=IDENTIFIER)? LCURLY f=function* RCURLY type_annotations? list_separator? -> ^(SERVICE $s ^(EXTENDS_ $e?) function* type_annotations?)
    ;


field_id
    : integer COLON -> ^(FIELD_ID_ integer)
    ;

field
    : field_id? field_req? field_type IDENTIFIER ('=' const_value)? type_annotations? list_separator?
        -> ^(FIELD_ IDENTIFIER field_type field_id? ^(REQUIREDNESS_ field_req?) const_value? type_annotations?)
    ;

field_req
    : REQUIRED
    | OPTIONAL
    ;


function
    : function_mode? function_type IDENTIFIER '(' field* ')' throws_list? type_annotations? list_separator?
        -> ^(METHOD_ IDENTIFIER function_type ^(ARGS_ field*) function_mode? throws_list? type_annotations?)
    ;

function_mode
    : EVENT | ONEWAY | ASYNC | DEFERRED | PROP_GSC | PROP_GS | PROP_GC ;
	
function_type
    : field_type
    | VOID
    ;

throws_list
    : THROWS '(' field* ')' -> ^(THROWS field*)
    ;


type_annotations
    : '(' type_annotation* ')' -> ^(TYPES_ type_annotation*)
    ;

type_annotation
    : IDENTIFIER ('=' annotation_value)? list_separator? -> ^(TYPE_ IDENTIFIER annotation_value?)
    ;

annotation_value
    : integer | LITERAL
    ;


field_type
    : base_type | IDENTIFIER | container_type | service_ptr
    ;

base_type
    : real_base_type type_annotations?
    ;

service_ptr
    : IDENTIFIER '*' type_annotations? -> ^(SERVICE_PTR_TYPE IDENTIFIER type_annotations?)
    ;

container_type
    : (map_type | set_type | list_type) type_annotations?
    ;

map_type
    : MAP cpp_type? '<' field_type COMMA field_type '>' -> ^(MAP field_type field_type cpp_type?)
    ;

set_type
    : SET cpp_type? '<' field_type '>' -> ^(SET field_type cpp_type?)
    ;

list_type
    : LIST '<' field_type '>' cpp_type? -> ^(LIST field_type cpp_type?)
    ;

cpp_type
    : 'cpp_type' LITERAL -> ^(CPP_TYPE_ LITERAL)
    ;


const_value
    : integer | DOUBLE | LITERAL | IDENTIFIER | const_list | const_map
    ;

integer
    : INTEGER | HEX_INTEGER
	;

INTEGER
    : ('+' | '-')? DIGIT+
        { setText(getText().substring(0, getText().length())); }
    ;

HEX_INTEGER
    : '0x' HEX_DIGIT+
        { setText(getText().substring(0, getText().length())); }
    ;

DOUBLE
    : ('+' | '-')? DIGIT* ('.' DIGIT+)? (('E' | 'e') INTEGER)?
    ;

const_list
    : '[' (const_value list_separator?)* ']' -> ^(LIST const_value*)
    ;

const_map_entry
    : k=const_value ':' v=const_value list_separator? -> ^(ENTRY_ $k $v)
    ;

const_map
    : LCURLY const_map_entry* RCURLY -> ^(MAP const_map_entry*)
    ;

list_separator
    : COMMA | SEMICOLON
    ;

real_base_type
    :  TYPE_BOOL | TYPE_BYTE | TYPE_I16 | TYPE_I32 | TYPE_I64 | TYPE_DOUBLE | TYPE_STRING | TYPE_BINARY
    ;

TYPE_BOOL: 'bool';
TYPE_BYTE: 'byte' | 'int8' ;
TYPE_I16: 'i16' | 'int16' ;
TYPE_I32: 'i32' | 'int32' ;
TYPE_I64: 'i64' | 'int64' ;
TYPE_DOUBLE: 'double';
TYPE_STRING: 'string';
TYPE_BINARY: 'binary';

LITERAL
    : (('"' ~'"'* '"') | ('\'' ~'\''* '\''))
        { if (getText().length()>2) setText(getText().substring(1, getText().length() - 1)); }
    ;

IDENTIFIER
    : (LETTER | '_') (LETTER | DIGIT | '.' | '_')*
        { setText(getText().substring(0, getText().length())); }
    ;

COMMA : ',' ;
SEMICOLON : ';' ;

fragment LETTER
    : 'A'..'Z' | 'a'..'z'
    ;

fragment DIGIT
    : '0'..'9'
    ;

fragment HEX_DIGIT
    : DIGIT | 'A'..'F' | 'a'..'f'
    ;

WS
    : (' ' | '\t' | '\r' '\n' | '\n')+ { $channel = HIDDEN; }
    ;

COMMENT
    : '/*' (options {greedy=false;} : .)* '*/' { $channel = COMMENTS; }
    | ('//' | '#') (~'\n')* { $channel = COMMENTS; }
    ;