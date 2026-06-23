namespace HPCompiler;

static class Lexer
{
    public static List<Token> Lex(string text, string file, int line)
    {
        var toks = new List<Token>();
        int i = 0, n = text.Length;
        while (i < n)
        {
            char c = text[i];
            if (c == ';') break;
            if (char.IsWhiteSpace(c)) { i++; continue; }

            if (c == '$' && i + 1 < n && Uri.IsHexDigit(text[i + 1]))
            {
                int j = i + 1;
                while (j < n && Uri.IsHexDigit(text[j])) j++;
                long v = Convert.ToInt64(text.Substring(i + 1, j - i - 1), 16);
                toks.Add(new Token(TokType.Number, text.Substring(i, j - i), v, file, line));
                i = j; continue;
            }
            if (c == '%' && i + 1 < n && (text[i + 1] == '0' || text[i + 1] == '1'))
            {
                int j = i + 1;
                while (j < n && (text[j] == '0' || text[j] == '1')) j++;
                long v = Convert.ToInt64(text.Substring(i + 1, j - i - 1), 2);
                toks.Add(new Token(TokType.Number, text.Substring(i, j - i), v, file, line));
                i = j; continue;
            }
            if (char.IsDigit(c))
            {
                int j = i;
                long v;
                if (c == '0' && i + 1 < n && (text[i + 1] == 'x' || text[i + 1] == 'X'))
                {
                    j = i + 2;
                    while (j < n && Uri.IsHexDigit(text[j])) j++;
                    v = Convert.ToInt64(text.Substring(i + 2, j - i - 2), 16);
                }
                else if (c == '0' && i + 1 < n && (text[i + 1] == 'b' || text[i + 1] == 'B'))
                {
                    j = i + 2;
                    while (j < n && (text[j] == '0' || text[j] == '1')) j++;
                    v = Convert.ToInt64(text.Substring(i + 2, j - i - 2), 2);
                }
                else
                {
                    while (j < n && char.IsDigit(text[j])) j++;
                    v = long.Parse(text.Substring(i, j - i));
                }
                toks.Add(new Token(TokType.Number, text.Substring(i, j - i), v, file, line));
                i = j; continue;
            }
            if (IsIdentStart(c))
            {
                int j = i;
                while (j < n && IsIdentChar(text[j])) j++;
                toks.Add(new Token(TokType.Ident, text.Substring(i, j - i), 0, file, line));
                i = j; continue;
            }
            if (c == '\'')
            {
                i++;
                long ch;
                if (i < n && text[i] == '\\') { i++; ch = Unescape(text[i]); i++; }
                else { ch = text[i]; i++; }
                if (i >= n || text[i] != '\'') throw new AsmException(Where(file, line) + "char literal mal formado");
                i++;
                toks.Add(new Token(TokType.Char, "char", ch, file, line));
                continue;
            }
            if (c == '"')
            {
                i++;
                var sb = new System.Text.StringBuilder();
                while (i < n && text[i] != '"')
                {
                    if (text[i] == '\\') { i++; sb.Append((char)Unescape(text[i])); i++; }
                    else { sb.Append(text[i]); i++; }
                }
                if (i >= n) throw new AsmException(Where(file, line) + "string sem fechamento");
                i++;
                toks.Add(new Token(TokType.String, sb.ToString(), 0, file, line));
                continue;
            }
            if ((c == '<' || c == '>') && i + 1 < n && text[i + 1] == c)
            {
                toks.Add(new Token(TokType.Symbol, text.Substring(i, 2), 0, file, line));
                i += 2; continue;
            }
            toks.Add(new Token(TokType.Symbol, c.ToString(), 0, file, line));
            i++;
        }
        return toks;
    }

    static long Unescape(char c) => c switch
    {
        'n' => '\n', 't' => '\t', 'r' => '\r', '0' => 0, '\\' => '\\',
        '\'' => '\'', '"' => '"', _ => c
    };

    static bool IsIdentStart(char c) => char.IsLetter(c) || c == '_' || c == '.';
    static bool IsIdentChar(char c) => char.IsLetterOrDigit(c) || c == '_' || c == '.';

    static string Where(string file, int line) => $"{file}:{line}: ";
}
