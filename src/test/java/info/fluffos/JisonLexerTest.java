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
