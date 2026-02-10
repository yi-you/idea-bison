package info.fluffos;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import generated.GeneratedTypes;
import generated.lexer._BisonLexer;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JisonLexerTest {
    private static final Set<String> EBNF_OPERATORS = Set.of("(", ")", "*", "+", "?");

    @Test
    public void testJisonEbnfOperatorsAndLexBlock() throws IOException {
        String baseInput = """
                %lex
                %%
                \\s+ return 'WS'
                /lex
                %start spec
                %%
                spec : (ID | STRING)+ ID? ID* ;
                """;

        assertJisonInputTokenizesCorrectly(baseInput, 1);
        assertJisonInputTokenizesCorrectly(baseInput + "%%\n", 2);
    }

    @Test
    public void testJisonOptionsDirective() throws IOException {
        String input = """
                %options flex
                %%
                spec : ID ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected %options to be tokenized as a directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "options".equals(t.name())));
    }

    @Test
    public void testJisonOptionsMultipleArgs() throws IOException {
        String input = """
                %options flex token-stacking
                %%
                spec : ID ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected %options directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "options".equals(t.name())));
        assertTrue("Expected 'flex' as ID token.",
                tokens.stream().anyMatch(t ->
                        GeneratedTypes.ID.equals(t.type) && "flex".equals(t.text)));
    }

    @Test
    public void testJisonEbnfDirective() throws IOException {
        String input = """
                %ebnf
                %%
                spec : ID ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected %ebnf to be tokenized as a directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "ebnf".equals(t.name())));
    }

    @Test
    public void testJisonIncludeDirective() throws IOException {
        String input = """
                %include "common.jison"
                %%
                spec : ID ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected %include to be tokenized as a directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "include".equals(t.name())));
    }

    @Test
    public void testJisonCalculatorGrammar() throws IOException {
        // Based on the classic Jison calculator example
        String input = """
                %lex
                %%
                \\s+                   /* skip whitespace */
                [0-9]+("."[0-9]+)?\\b  return 'NUMBER'
                "*"                   return '*'
                "/"                   return '/'
                "-"                   return '-'
                "+"                   return '+'
                "("                   return '('
                ")"                   return ')'
                <<EOF>>               return 'EOF'
                /lex

                %left '+' '-'
                %left '*' '/'

                %start expressions

                %%

                expressions
                    : e EOF
                    ;

                e
                    : e '+' e
                    | e '-' e
                    | e '*' e
                    | e '/' e
                    | '(' e ')'
                    | NUMBER
                    ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        // Verify lex block is treated as prologue
        assertTrue("Expected lex block to be tokenized as prologue.",
                tokens.stream().anyMatch(t -> GeneratedTypes.PROLOGUE_LITERAL.equals(t.type)));
        // Verify %left is recognized
        assertTrue("Expected %left directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "LEFT".equals(t.name())));
        // Verify %start is recognized
        assertTrue("Expected %start directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "start".equals(t.name())));
        // Verify %% is found (only one outside lex block)
        long percentCount = tokens.stream().filter(t -> "%%".equals(t.text)).count();
        assertEquals("Expected exactly 1 %% token outside lex block.", 1, percentCount);
    }

    @Test
    public void testJisonWithPrologueCode() throws IOException {
        // Jison supports %{ ... %} blocks just like Bison
        String input = """
                %{
                    var util = require('util');
                %}

                %left '+' '-'
                %%
                e : e '+' e | NUMBER ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected prologue literal from %{...%} block.",
                tokens.stream().anyMatch(t -> GeneratedTypes.PROLOGUE_LITERAL.equals(t.type)));
    }

    @Test
    public void testJisonWithActionCode() throws IOException {
        // Jison rules with JavaScript action code in braces
        String input = """
                %%
                e
                    : e '+' e
                        {$$ = $1 + $3;}
                    | NUMBER
                        {$$ = Number(yytext);}
                    ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected braced code literal for action blocks.",
                tokens.stream().anyMatch(t -> GeneratedTypes.BRACED_CODE_LITERAL.equals(t.type)));
    }

    @Test
    public void testJisonWithEpilogue() throws IOException {
        String input = """
                %%
                spec : ID ;
                %%
                // epilogue code
                module.exports = parser;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected epilogue literal.",
                tokens.stream().anyMatch(t -> GeneratedTypes.EPILOGUE_LITERAL.equals(t.type)));
    }

    @Test
    public void testJisonLexBlockWithMultipleRules() throws IOException {
        String input = """
                %lex
                %%
                \\s+                /* skip */
                [a-zA-Z_][a-zA-Z0-9_]* return 'ID'
                [0-9]+             return 'NUMBER'
                ";"                return ';'
                /lex
                %%
                program : statement ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Expected lex block as prologue.",
                tokens.stream().anyMatch(t -> GeneratedTypes.PROLOGUE_LITERAL.equals(t.type)));
        // No BAD_CHARACTER should be produced
        assertTrue("No BAD_CHARACTER tokens should be present.",
                tokens.stream().noneMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testJisonFullExample() throws IOException {
        // A comprehensive Jison grammar using multiple features
        String input = """
                %lex
                %%
                \\s+          /* skip */
                [a-z]+        return 'ID'
                /lex

                %ebnf
                %options flex
                %left '+' '-'
                %right '!'
                %start program

                %%

                program
                    : statement+ EOF
                    ;

                statement
                    : ID '=' expr ';'
                    ;

                expr
                    : expr '+' expr
                    | expr '-' expr
                    | '!' expr
                    | '(' expr ')'
                    | ID
                    ;
                """;
        List<TokenInfo> tokens = tokenize(input);
        // Verify no BAD_CHARACTER
        assertTrue("No BAD_CHARACTER tokens should be present.",
                tokens.stream().noneMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
        // Verify Jison-specific directives
        assertTrue("Expected %ebnf directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "ebnf".equals(t.name())));
        assertTrue("Expected %options directive.",
                tokens.stream().anyMatch(t ->
                        t.type instanceof BisonTokenType.BisonDirective && "options".equals(t.name())));
        // Verify lex block as prologue
        assertTrue("Expected lex block.",
                tokens.stream().anyMatch(t -> GeneratedTypes.PROLOGUE_LITERAL.equals(t.type)));
        // Verify EBNF operators
        assertTrue("Expected EBNF '+' operator as ID.",
                tokens.stream().anyMatch(t ->
                        GeneratedTypes.ID.equals(t.type) && "+".equals(t.text)));
    }

    @Test
    public void testLexerDoesNotThrowOnEofInString() throws IOException {
        // Unterminated string - should not throw, should return BAD_CHARACTER
        String input = "%%\nfoo : \"unterminated";
        List<TokenInfo> tokens = tokenize(input);
        // Should complete without exception
        assertTrue("Unterminated string should produce BAD_CHARACTER.",
                tokens.stream().anyMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testLexerDoesNotThrowOnEofInCharLiteral() throws IOException {
        // Unterminated character literal
        String input = "%%\nfoo : 'x";
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Unterminated char literal should produce BAD_CHARACTER.",
                tokens.stream().anyMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testLexerDoesNotThrowOnEofInBracedCode() throws IOException {
        // Unterminated braced code
        String input = "%%\nfoo : bar { unclosed";
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Unterminated braced code should produce BAD_CHARACTER.",
                tokens.stream().anyMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testLexerDoesNotThrowOnEofInLexBlock() throws IOException {
        // Unterminated %lex block (no /lex)
        String input = "%lex\n%%\nsome lex content";
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Unterminated lex block should produce BAD_CHARACTER.",
                tokens.stream().anyMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testLexerDoesNotThrowOnEofInComment() throws IOException {
        // Unterminated block comment
        String input = "%%\nfoo : bar /* unterminated comment";
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Unterminated comment should produce BAD_CHARACTER.",
                tokens.stream().anyMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testLexerDoesNotThrowOnEofInPrologue() throws IOException {
        // Unterminated prologue (%{ without %})
        String input = "%{\n  some prologue code";
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Unterminated prologue should produce BAD_CHARACTER.",
                tokens.stream().anyMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testLexerDoesNotThrowOnEofInTag() throws IOException {
        // Unterminated tag (<type without >)
        String input = "%%\nfoo : <incomplete_type";
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Unterminated tag should produce BAD_CHARACTER.",
                tokens.stream().anyMatch(t -> TokenType.BAD_CHARACTER.equals(t.type)));
    }

    @Test
    public void testTokenPositionsCoverEntireInput() throws IOException {
        // Verify that token positions cover the entire input (no gaps, end matches length)
        String input = """
                %left '+' '-'
                %%
                e : e '+' e | NUMBER ;
                """;
        _BisonLexer lexer = new _BisonLexer();
        lexer.reset(input, 0, input.length(), _BisonLexer.YYINITIAL);

        int lastEnd = 0;
        IElementType token;
        while ((token = lexer.advance()) != null) {
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            assertTrue("Token start should be >= lastEnd. start=" + start + " lastEnd=" + lastEnd,
                    start >= lastEnd);
            assertTrue("Token end should be > start. end=" + end + " start=" + start,
                    end > start);
            lastEnd = end;
        }
        assertEquals("Last token end should equal input length.", input.length(), lastEnd);
    }

    @Test
    public void testTokenPositionsCoverEntireInputWithEpilogue() throws IOException {
        String input = """
                %%
                e : NUMBER ;
                %%
                some epilogue code
                """;
        _BisonLexer lexer = new _BisonLexer();
        lexer.reset(input, 0, input.length(), _BisonLexer.YYINITIAL);

        int lastEnd = 0;
        IElementType token;
        while ((token = lexer.advance()) != null) {
            lastEnd = lexer.getTokenEnd();
        }
        assertEquals("Last token end should equal input length.", input.length(), lastEnd);
    }

    @Test
    public void testSemwhitespaceLexJison() throws IOException {
        // Content from zaach/jison examples/semwhitespace_lex.jison
        // This is a Jison lex file that should not crash the lexer
        String input = """
                /* Demonstrates semantic whitespace pseudo-tokens, INDENT/DEDENT. */

                id\t\t\t[a-zA-Z][a-zA-Z0-9]*
                spc\t\t\t[\\t \\u00a0]

                %s EXPR

                %%
                "if"\t\t\t\treturn 'IF';
                "else"\t\t\t\treturn 'ELSE';
                "print"\t\t\t\treturn 'PRINT';
                ":"\t\t\t\treturn 'COLON';
                "("\t\t\t\tthis.begin('EXPR'); return 'LPAREN';
                ")"\t\t\t\tthis.popState(); return 'RPAREN';
                {id}\t\t\t\treturn 'ID';
                <<EOF>>\t\t\t\treturn "ENDOFFILE";
                <INITIAL>\\s*<<EOF>>\t\t%{
                \t\t\t\t\tvar tokens = [];
                \t\t\t\t\twhile (0 < _iemitstack[0]) {
                \t\t\t\t\t\tthis.popState();
                \t\t\t\t\t\ttokens.unshift("DEDENT");
                \t\t\t\t\t\t_iemitstack.shift();
                \t\t\t\t\t}
                \t\t\t\t\tif (tokens.length) return tokens;
                \t\t\t\t%}
                {spc}+\t\t\t\t/* ignore all other whitespace */

                %%
                /* initialize the pseudo-token stack with 0 indents */
                _iemitstack = [0];
                """;
        // Should not throw any exceptions
        List<TokenInfo> tokens = tokenize(input);
        assertTrue("Should produce some tokens.", !tokens.isEmpty());
    }

    @Test
    public void testLexerResetClearsState() throws IOException {
        // First, lex something that sets internal state
        String input1 = "%left '+'\n%%\ne : NUMBER ;";
        _BisonLexer lexer = new _BisonLexer();
        lexer.reset(input1, 0, input1.length(), _BisonLexer.YYINITIAL);
        while (lexer.advance() != null) { /* consume all */ }

        // Reset with new input - should work correctly
        String input2 = "%right '-'\n%%\nf : ID ;";
        lexer.reset(input2, 0, input2.length(), _BisonLexer.YYINITIAL);
        int lastEnd = 0;
        IElementType token;
        while ((token = lexer.advance()) != null) {
            lastEnd = lexer.getTokenEnd();
        }
        assertEquals("After reset, last token end should equal new input length.",
                input2.length(), lastEnd);
    }

    @Test
    public void testLambdaCalculusJisonGrammar() throws IOException {
        // Lambda calculus grammar by Zach Carter - from jison examples
        String input = "/* Lambda calculus grammar by Zach Carter */\n" +
                "\n" +
                "%lex\n" +
                "%%\n" +
                "\n" +
                "\\s*\\n\\s*  {/* ignore */}\n" +
                "\"(\"       { return '('; }\n" +
                "\")\"       { return ')'; }\n" +
                "\"^\"|\"\u03BB\"   { return 'LAMBDA'; }\n" +
                "\".\"\\s?    { return '.'; }\n" +
                "[a-zA-Z]  { return 'VAR'; }\n" +
                "\\s+       { return 'SEP'; }\n" +
                "<<EOF>>   { return 'EOF'; }\n" +
                "\n" +
                "/lex\n" +
                "\n" +
                "%%\n" +
                "\n" +
                "file\n" +
                "    : EOF\n" +
                "    | e EOF\n" +
                "    ;\n" +
                "\n" +
                "e\n" +
                "    : LAMBDA VAR '.' e\n" +
                "    | e e\n" +
                "    | '(' e ')'\n" +
                "    | VAR\n" +
                "    ;\n";

        _BisonLexer lexer = new _BisonLexer();
        lexer.reset(input, 0, input.length(), _BisonLexer.YYINITIAL);

        int lastEnd = 0;
        IElementType token;
        while ((token = lexer.advance()) != null) {
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            // Check for gaps
            assertEquals("Gap detected at position " + lastEnd + ": chars '" +
                    input.substring(lastEnd, Math.min(start, input.length())).replace("\n", "\\n") + "' not covered by any token",
                    lastEnd, start);
            assertTrue("Token end should be > start", end > start);
            lastEnd = end;
        }
        assertEquals("Last token end should equal input length.", input.length(), lastEnd);
    }

    private record TokenInfo(IElementType type, String text) {
        String name() {
            // Extract directive/token name from toString: "BisonTokenType.NAME"
            String s = type.toString();
            int dot = s.lastIndexOf('.');
            return dot >= 0 ? s.substring(dot + 1) : s;
        }
    }

    private static List<TokenInfo> tokenize(String input) throws IOException {
        _BisonLexer lexer = new _BisonLexer();
        lexer.reset(input, 0, input.length(), _BisonLexer.YYINITIAL);

        List<TokenInfo> tokens = new ArrayList<>();
        IElementType token;
        while ((token = lexer.advance()) != null) {
            String text = input.substring(lexer.getTokenStart(), lexer.getTokenEnd());
            tokens.add(new TokenInfo(token, text));
        }
        return tokens;
    }

    private static void assertJisonInputTokenizesCorrectly(String input, int expectedPercentCount) throws IOException {
        _BisonLexer lexer = new _BisonLexer();
        lexer.reset(input, 0, input.length(), _BisonLexer.YYINITIAL);

        int percentCount = 0;
        boolean sawPrologue = false;
        boolean sawEbnfOperator = false;
        IElementType token;
        while ((token = lexer.advance()) != null) {
            if (TokenType.BAD_CHARACTER.equals(token)) {
                throw new AssertionError("Unexpected BAD_CHARACTER token");
            }
            String text = input.substring(lexer.getTokenStart(), lexer.getTokenEnd());
            if ("%%".equals(text)) {
                percentCount++;
            }
            if (EBNF_OPERATORS.contains(text)) {
                assertEquals(GeneratedTypes.ID, token);
                sawEbnfOperator = true;
            }
            if (GeneratedTypes.PROLOGUE_LITERAL.equals(token)) {
                sawPrologue = true;
            }
        }

        assertTrue("Expected lex block to be tokenized as a prologue literal.", sawPrologue);
        assertTrue("Expected EBNF operator tokens to be returned as IDs.", sawEbnfOperator);
        assertEquals("Unexpected number of %% tokens.", expectedPercentCount, percentCount);
    }
}
