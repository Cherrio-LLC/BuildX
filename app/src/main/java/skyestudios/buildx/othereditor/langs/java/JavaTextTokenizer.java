package skyestudios.buildx.othereditor.langs.java;


import skyestudios.buildx.othereditor.langs.internal.MyCharacter;
import skyestudios.buildx.othereditor.langs.internal.TrieTree;

import static skyestudios.buildx.othereditor.langs.java.Tokens.*;

/**
 * @author Rose
 */
public class JavaTextTokenizer {

    private static TrieTree<Tokens> keywords;

    static {
        doStaticInit();
    }

    public static TrieTree getTree(){
        return keywords;
    }

    private CharSequence source;
    protected int bufferLen;
    private int line;
    private int column;
    private int index;
    protected int offset;
    protected int length;
    private Tokens currToken;
    private boolean lcCal;
    private boolean skipWS;
    private boolean skipComment;

    public JavaTextTokenizer(CharSequence src) {
        if(src == null) {
            throw new IllegalArgumentException("src can not be null");
        }
        this.source = src;
        init();
    }

    private void init() {
        line = 0;
        column = 0;
        length = 0;
        index = 0;
        currToken = WHITESPACE;
        lcCal = false;
        skipWS = false;
        skipComment = false;
        this.bufferLen = source.length();
    }

    public void setCalculateLineColumn(boolean cal) {
        this.lcCal = cal;
    }

    public void setSkipWhitespace(boolean skip) {
        this.skipWS = skip;
    }

    public void setSkipComment(boolean skip) {
        this.skipComment = skip;
    }

    public void pushBack(int length) {
        if (length > getTokenLength()) {
            throw new IllegalArgumentException("pushBack length too large");
        }
        this.length -= length;
    }

    private boolean isIdentifierPart(char ch) {
        return MyCharacter.isJavaIdentifierPart(ch);
    }

    private boolean isIdentifierStart(char ch) {
        return MyCharacter.isJavaIdentifierStart(ch);
    }

    public CharSequence getTokenString() {
        return source.subSequence(offset, offset + length);
    }

    public int getTokenLength() {
        return length;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getIndex() {
        return index;
    }

    public Tokens getToken() {
        return currToken;
    }

    public String yyDesc() {
        return " ??????" + line + " ??????" + column;
    }

    private char charAt(int i) {
        return source.charAt(i);
    }

    private char charAt() {
        return source.charAt(offset + length);
    }

    public Tokens nextToken() {
        Tokens token;
        do {
            token = directNextToken();
        } while ((skipWS && token == WHITESPACE) || (skipComment && (token == LINE_COMMENT || token == LONG_COMMENT)));
        currToken = token;
        return token;
    }

    public Tokens directNextToken() {
        if (lcCal) {
            boolean r = false;
            for (int i = offset; i < offset + length; i++) {
                char ch = charAt(i);
                if (ch == '\r') {
                    r = true;
                    line++;
                    column = 0;
                } else if (ch == '\n') {
                    if (r) {
                        r = false;
                        continue;
                    }
                    line++;
                    column = 0;
                } else {
                    r = false;
                    column++;
                }
            }
        }
        index = index + length;
        offset = offset + length;
        if (offset == bufferLen) {
            return EOF;
        }
        char ch = source.charAt(offset);
        length = 1;
        if (ch == '\n') {
            return NEWLINE;
        } else if (ch == '\r') {
            scanNewline();
            return NEWLINE;
        } else if (isWhitespace(ch)) {
            char chLocal;
            while (offset + length < bufferLen && isWhitespace(chLocal = charAt(offset + length)) ) {
                if (chLocal == '\r' || chLocal == '\n') {
                    break;
                }
                length++;
            }
            return WHITESPACE;
        } else {
            if (isIdentifierStart(ch)) {
                return scanIdentifier(ch);
            }
            if (isPrimeDigit(ch)) {
                return scanNumber();
            }
            /* Scan usual symbols first */
            if(ch == ';') {
                return SEMICOLON;
            }else if(ch == '(') {
                return LPAREN;
            }else if(ch == ')') {
                return RPAREN;
            }else if(ch == ':') {
                return COLON;
            }else if(ch == '<') {
                return scanLT();
            }else if(ch == '>') {
                return scanGT();
            }
            /* Scan secondly symbols */
            switch (ch) {
                case '=':
                    return scanOperatorTwo('=', EQ, EQEQ);
                case '.':
                    return DOT;
                case '{':
                    return LBRACE;
                case '}':
                    return RBRACE;
                case '/':
                    return scanDIV();
                case '*':
                    return scanOperatorTwo('=', MULT, MULTEQ);
                case '-':
                    return scanOperatorTwo('=', '-', MINUS, MINUSEQ, MINUSMINUS);
                case '+':
                    return scanOperatorTwo('=', '+', PLUS, PLUSEQ, PLUSPLUS);
                case '[':
                    return LBRACK;
                case ']':
                    return RBRACK;
                case ',':
                    return COMMA;
                case '!':
                    return NOT;
                case '~':
                    return COMP;
                case '?':
                    return QUESTION;
                case '&':
                    return scanOperatorTwo('&', '=', AND, ANDAND, ANDEQ);
                case '|':
                    return scanOperatorTwo('|', '=', OR, OROR, OREQ);
                case '^':
                    return scanOperatorTwo('=', XOR, XOREQ);
                case '%':
                    return scanOperatorTwo('=', MOD, MODEQ);
                case '\'':
                    scanCharLiteral();
                    return CHARACTER_LITERAL;
                case '\"':
                    scanStringLiteral();
                    return STRING;
                default:
                    //error("???????????????Token : '" + ch + " '", new StringAdvice("??????????????????????????????????????????????????????????????????????????????????????????"));
                    return UNKNOWN;
            }
        }
    }

    protected final void throwIfNeeded() {
        if(offset + length == bufferLen) {
            throw new RuntimeException("Token too long");
        }
    }

    protected void scanNewline() {
        if (offset + length < bufferLen && charAt(offset + length) == '\n') {
            length++;
        }
    }

    protected Tokens scanIdentifier(char ch) {
        TrieTree.Node<Tokens> n = keywords.root.map.get(ch);
        while (offset + length < bufferLen && isIdentifierPart(ch = charAt(offset + length))) {
            length++;
            n = n == null ? null : n.map.get(ch);
        }
        return n == null ? IDENTIFIER : (n.token == null ? IDENTIFIER : n.token);
    }

    protected void scanTrans() {
        throwIfNeeded();
        char ch = charAt(offset + length);
        if (ch == '\\' || ch == 't' || ch == 'f' || ch == 'n' || ch == 'r' || ch == '0' || ch == '\"' || ch == '\''
                || ch == 'b') {
            length++;
        } else if (ch == 'u') {
            length++;
            for (int i = 0; i < 4; i++) {
                throwIfNeeded();
                if (!isDigit(charAt(offset + length))) {
                    //error("????????????????????????????????????????????? : '" + charAt(offset + length) + "'",
                     //       new StringAdvice("???????????????0-9,a-f,A-F????????????"));
                    return;
                }
                length++;
            }
        } else {
           // error("??????????????????,??????????????????????????? : ' " + charAt(offset + length) + " '",
                   // new StringAdvice("???????????????\\ ???t???f???n???r???0???\"???' ???b???"));
        }
    }

    protected void scanStringLiteral() {
        throwIfNeeded();
        char ch;
        while (offset + length < bufferLen && (ch = charAt(offset + length)) != '\"') {
            if (ch == '\\') {
                length++;
                scanTrans();
            } else {
                if (ch == '\n') {

                    return;
                }
                length++;
                throwIfNeeded();
            }
        }
        if (offset + length == bufferLen) {
           // error("??????????????????????????????:???????????????????????????????????????", new StringAdvice("???????????????' \" '"));
        } else {
            length++;
        }
    }

    protected void scanCharLiteral() {
        throwIfNeeded();
        char ch = charAt();
        if (ch == '\\') {
            length++;
            scanTrans();
        } else if (ch == '\'') {
           // error("???????????????????????????:????????????", new StringAdvice("??????????????????????????????????????????????????????????????????"));
        } else {
            if (ch == '\n') {
                //error("???????????????????????????:??????????????????????????????????????????", new StringAdvice("??????????????? ' ' '"));
                return;
            }
            length++;
        }
        throwIfNeeded();
        if (charAt() != '\'') {
           // error("???????????????????????????:???????????????,????????????' ' ',???????????????' " + charAt() + " '",
               //     new StringAdvice("?????????????????????' ' '"));
        } else {
            length++;
        }
    }

    protected Tokens scanNumber() {
        if(offset + length == bufferLen) {
            return INTEGER_LITERAL;
        }
        boolean flag = false;
        char ch = charAt(offset);
        if (ch == '0') {
            if(charAt() == 'x') {
                length++;
            }
            flag = true;
        }
        while (offset + length < bufferLen && isDigit(charAt())) {
            length++;
        }
        if(offset + length == bufferLen) {
            return INTEGER_LITERAL;
        }
        ch = charAt();
        if (ch == '.') {
            if (flag) {
               // error("???????????????????????????????????????", new StringAdvice("???????????????????????????"));
                return INTEGER_LITERAL;
            }
            if(offset + length + 1 == bufferLen) {
                return INTEGER_LITERAL;
            }
            length++;
            throwIfNeeded();
            while (offset + length < bufferLen && isDigit(charAt())) {
                length++;
            }
            if(offset + length == bufferLen) {
                return FLOATING_POINT_LITERAL;
            }
            ch = charAt();
            if (ch == 'e' || ch == 'E') {
                length++;
                throwIfNeeded();
                if (charAt() == '-' || charAt() == '+') {
                    length++;
                    throwIfNeeded();
                }
                while (offset + length < bufferLen && isPrimeDigit(charAt())) {
                    length++;
                }
                if(offset + length == bufferLen) {
                    return FLOATING_POINT_LITERAL;
                }
                ch = charAt();
                if (ch == 'f' || ch == 'F' || ch == 'D'
                        || ch == 'd') {
                    length++;
                }
            } else if (ch == 'f' || ch == 'F'
                    || ch == 'D' || ch == 'd') {
                length++;
            }
            return FLOATING_POINT_LITERAL;
        } else if (ch == 'l' || ch == 'L') {
            length++;
            return INTEGER_LITERAL;
        } else if (ch == 'F' || ch == 'f' || ch == 'D'
                || ch == 'd') {
            length++;
            return FLOATING_POINT_LITERAL;
        } else {
            return INTEGER_LITERAL;
        }
    }

    /* The following methods have been simplified for syntax high light */

    protected Tokens scanDIV() {
        if (offset + 1 == bufferLen) {
            return DIV;
        }
        char ch = charAt();
        if (ch == '/') {
            length++;
            while (offset + length < bufferLen && charAt() != '\n') {
                length++;
            }
            return LINE_COMMENT;
        } else if (ch == '*') {
            length++;
            char pre, curr = '?';
            boolean breakFromLoop = false;
            while (offset + length < bufferLen) {
                pre = curr;
                curr = charAt();
                if (curr == '/' && pre == '*') {
                    length++;
                    breakFromLoop = true;
                    break;
                }
                length++;
            }
            if (!breakFromLoop) {
               // error("??????????????????????????????", new StringAdvice("?????????????????????' */'"));
            }
            return LONG_COMMENT;
        } else {
            return DIV;
        }
    }

    protected Tokens scanLT() {
        return LT;
    }

    protected Tokens scanGT() {
        return GT;
    }

    protected Tokens scanOperatorTwo(char ex1, char ex2, Tokens ifWrong, Tokens ifRight1, Tokens ifRight2) {
        return ifWrong;
    }

    protected Tokens scanOperatorTwo(char expected, Tokens ifWrong, Tokens ifRight) {
        return ifWrong;
    }

    public void reset(CharSequence src) {
        if(src == null) {
            throw new IllegalArgumentException();
        }
        this.source = src;
        line = 0;
        column = 0;
        length = 0;
        index = 0;
        offset = 0;
        currToken = WHITESPACE;
        bufferLen = src.length();
    }
    /*
    protected void error(String msg, Advice advice) {
        TokenizeError e = new TokenizeError(msg, advice);
        e.makePositionDesc();
        log.addMessage(e);
    }

    public class TokenizeError extends Message {

        public TokenizeError(String msg) {
            this(msg, null);
        }

        public TokenizeError(String msg, Advice adv) {
            super(Message.LEVEL_ERROR, msg, adv);
        }

        private void makePositionDesc() {
        }

    }
    */
    protected static String[] sKeywords;

    private static Tokens[] sTokens;

    protected static void doStaticInit() {
        sKeywords = new String[]{
            "assert","abstract","boolean","byte","char","class","do",
            "double","final","float","for","if","int","long","new",
            "public","private","protected","package","return","static",
            "short","super","switch","else","volatile","synchronized","strictfp",
            "goto","continue","break","transient","void","try","catch",
            "finally","while","case","default","const","enum","extends",
            "implements","import","instanceof","interface","native",
            "this","throw","throws"
        };
        sTokens = new Tokens[]{
            ABSTRACT, ASSERT, BOOLEAN, BYTE, CHAR, CLASS, DO,
            DOUBLE, FINAL, FLOAT, FOR, IF, INT, LONG, NEW,
            PUBLIC, PRIVATE, PROTECTED, PACKAGE, RETURN, STATIC,
            SHORT, SUPER, SWITCH, ELSE, VOLATILE, SYNCHRONIZED, STRICTFP,
            GOTO, CONTINUE, BREAK, TRANSIENT, VOID, TRY, CATCH,
            FINALLY, WHILE, CASE, DEFAULT, CONST, ENUM, EXTENDS,
            IMPLEMENTS, IMPORT, INSTANCEOF, INTERFACE, NATIVE,
            THIS, THROW, THROWS
        };
        // ?????????????????????
        keywords = new TrieTree<>();
        for(int i = 0;i < sKeywords.length;i++) {
            keywords.put(sKeywords[i],sTokens[i]);
        }
        MyCharacter.initMap();
    }

    protected static boolean isDigit(char c) {
        return ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'));
    }

    protected static boolean isPrimeDigit(char c) {
        return (c >= '0' && c <= '9');
    }

    protected static boolean isWhitespace(char c) {
        return (c == '\t' || c == ' ' || c == '\f' || c == '\n' || c == '\r');
    }
}
