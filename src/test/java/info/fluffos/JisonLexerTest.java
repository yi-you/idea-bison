package info.fluffos;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import generated.GeneratedTypes;
import generated.lexer._BisonLexer;
import org.junit.Test;

import java.io.IOException;
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
