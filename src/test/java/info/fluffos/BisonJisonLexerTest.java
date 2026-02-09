package info.fluffos;

import com.intellij.lexer.Lexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import generated.GeneratedTypes;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BisonJisonLexerTest {

    @Test
    public void lexesJisonLexBlockAsPrologue() {
        String input = "%lex\n%%\n\\s+ /* skip */\n<<EOF>> return 'EOF';\n/lex\n%%\nrule: ID;\n%%\n";
        Lexer lexer = new BisonLexerAdapter();
        lexer.start(input);

        assertEquals(GeneratedTypes.PROLOGUE_LITERAL, lexer.getTokenType());
        String prologueText = input.substring(lexer.getTokenStart(), lexer.getTokenEnd());
        assertTrue(prologueText.startsWith("%lex"));
        assertTrue(prologueText.trim().endsWith("/lex"));

        advanceToNextToken(lexer);
        assertEquals(BisonTokenType.token("%%"), lexer.getTokenType());
    }

    @Test
    public void lexesEbnfTokens() {
        String input = "%%\nrule: (ID | STRING)+ ID? ;\n%%\n";
        List<IElementType> tokens = lexTokens(input);

        assertTrue(tokens.contains(BisonTokenType.token("(")));
        assertTrue(tokens.contains(BisonTokenType.token(")")));
        assertTrue(tokens.contains(BisonTokenType.token("+")));
        assertTrue(tokens.contains(BisonTokenType.token("?")));
    }

    private List<IElementType> lexTokens(String input) {
        List<IElementType> tokens = new ArrayList<>();
        Lexer lexer = new BisonLexerAdapter();
        lexer.start(input);
        while (lexer.getTokenType() != null) {
            tokens.add(lexer.getTokenType());
            lexer.advance();
        }
        return tokens;
    }

    private void advanceToNextToken(Lexer lexer) {
        lexer.advance();
        while (lexer.getTokenType() == TokenType.WHITE_SPACE) {
            lexer.advance();
        }
    }
}
