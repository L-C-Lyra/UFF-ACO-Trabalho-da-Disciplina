namespace HPCompiler;

enum TokType { Ident, Number, String, Char, Symbol }

readonly struct Token
{
    public readonly TokType Type;
    public readonly string Text;
    public readonly long Value;
    public readonly string File;
    public readonly int Line;

    public Token(TokType type, string text, long value, string file, int line)
    {
        Type = type;
        Text = text;
        Value = value;
        File = file;
        Line = line;
    }
}

sealed class AsmException : Exception
{
    public AsmException(string message) : base(message) { }
}
