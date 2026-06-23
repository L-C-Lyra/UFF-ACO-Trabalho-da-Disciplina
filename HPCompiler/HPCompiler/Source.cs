namespace HPCompiler;

static class Source
{
    public static List<(string file, int line, List<Token> toks)> Load(string path)
    {
        var result = new List<(string, int, List<Token>)>();
        Load(Path.GetFullPath(path), result, new HashSet<string>());
        return result;
    }

    static void Load(string path, List<(string, int, List<Token>)> result, HashSet<string> stack)
    {
        if (!stack.Add(path)) throw new AsmException($"inclusão cíclica: {path}");
        string[] rawLines = File.ReadAllText(path).Replace("\r\n", "\n").Split('\n');
        string name = path;
        string dir = Path.GetDirectoryName(path) ?? ".";

        for (int n = 0; n < rawLines.Length; n++)
        {
            var toks = Lexer.Lex(rawLines[n], name, n + 1);
            if (toks.Count == 0) continue;

            if (toks[0].Type == TokType.Ident &&
                string.Equals(toks[0].Text, ".include", StringComparison.OrdinalIgnoreCase))
            {
                if (toks.Count != 2 || toks[1].Type != TokType.String)
                    throw new AsmException($"{name}:{n + 1}: uso: .include \"arquivo\"");
                string inc = toks[1].Text;
                string full = Path.IsPathRooted(inc) ? inc : Path.GetFullPath(Path.Combine(dir, inc));
                Load(full, result, stack);
                continue;
            }
            result.Add((name, n + 1, toks));
        }
        stack.Remove(path);
    }
}
