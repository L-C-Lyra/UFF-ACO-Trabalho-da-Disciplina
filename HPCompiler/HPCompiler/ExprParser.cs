namespace HPCompiler;

sealed class ExprParser
{
    readonly Token[] _t;
    readonly Assembler _asm;
    int _p;

    public ExprParser(Token[] t, Assembler asm)
    {
        _t = t;
        _asm = asm;
    }

    public long Parse()
    {
        if (_t.Length == 0) throw new AsmException("expressão vazia");
        long v = Or();
        if (_p < _t.Length) throw Err("token inesperado");
        return v;
    }

    long Or()
    {
        long v = Xor();
        while (Sym("|")) { _p++; v |= Xor(); }
        return v;
    }

    long Xor()
    {
        long v = And();
        while (Sym("^")) { _p++; v ^= And(); }
        return v;
    }

    long And()
    {
        long v = Shift();
        while (Sym("&")) { _p++; v &= Shift(); }
        return v;
    }

    long Shift()
    {
        long v = Add();
        while (Sym("<<") || Sym(">>"))
        {
            bool left = _t[_p].Text == "<<";
            _p++;
            long r = Add();
            v = left ? v << (int)r : v >> (int)r;
        }
        return v;
    }

    long Add()
    {
        long v = Mul();
        while (Sym("+") || Sym("-"))
        {
            bool plus = _t[_p].Text == "+";
            _p++;
            long r = Mul();
            v = plus ? v + r : v - r;
        }
        return v;
    }

    long Mul()
    {
        long v = Unary();
        while (Sym("*") || Sym("/") || Sym("%"))
        {
            string o = _t[_p].Text;
            _p++;
            long r = Unary();
            v = o == "*" ? v * r : o == "/" ? v / r : v % r;
        }
        return v;
    }

    long Unary()
    {
        if (Sym("-")) { _p++; return -Unary(); }
        if (Sym("+")) { _p++; return Unary(); }
        if (Sym("~")) { _p++; return ~Unary(); }
        if (Sym("<")) { _p++; return Unary() & 0xFF; }
        if (Sym(">")) { _p++; return (Unary() >> 8) & 0xFF; }
        return Primary();
    }

    long Primary()
    {
        if (_p >= _t.Length) throw Err("fim inesperado da expressão");
        Token tok = _t[_p];
        if (tok.Type == TokType.Number || tok.Type == TokType.Char) { _p++; return tok.Value; }
        if (Sym("("))
        {
            _p++;
            long v = Or();
            if (!Sym(")")) throw Err("')' esperado");
            _p++;
            return v;
        }
        if (Sym("$")) { _p++; return _asm.CurrentAddr; }
        if (tok.Type == TokType.Ident) { _p++; return _asm.ResolveSymbol(tok.Text, tok); }
        throw Err("operando inválido");
    }

    bool Sym(string s) => _p < _t.Length && _t[_p].Type == TokType.Symbol && _t[_p].Text == s;

    AsmException Err(string m)
    {
        Token tok = _p < _t.Length ? _t[_p] : _t[^1];
        return new AsmException($"{tok.File}:{tok.Line}: {m}");
    }
}
