using System.IO.Compression;

namespace HPCompiler;

//nao faz exatamente parte do compilador, mas é uma ferramenta para ajudar na manipulação dos VGMs
static class Vgm
{
    const int SAMPLES_PER_FRAME = 735;

    public static int Run(string[] args)
    {
        string? input = null, outBase = null;
        int maxFrames = int.MaxValue;
        bool blob = false;

        for (int i = 0; i < args.Length; i++)
        {
            string a = args[i];
            switch (a)
            {
                case "-o": case "--out":
                    if (++i >= args.Length) return Err("falta caminho apos " + a);
                    outBase = args[i];
                    break;
                case "--blob":
                    blob = true;
                    break;
                case "--frames":
                    if (++i >= args.Length || !int.TryParse(args[i], out maxFrames)) return Err("--frames precisa de um numero");
                    break;
                default:
                    if (a.StartsWith('-')) return Err("opcao desconhecida: " + a);
                    input = a;
                    break;
            }
        }
        if (input == null) { Console.WriteLine("uso: hpcasm vgm <entrada.vgm|.vgz> [-o base] [--frames N]"); return 1; }
        outBase ??= Path.ChangeExtension(input, null);

        byte[] d;
        try { d = ReadMaybeGz(input); }
        catch (FileNotFoundException) { return Err("arquivo nao encontrado: " + input); }

        if (d.Length < 0x40 || d[0] != 'V' || d[1] != 'g' || d[2] != 'm' || d[3] != ' ')
            return Err("nao e um arquivo VGM");

        uint dataOff = U32(d, 0x34);
        int dataStart = dataOff != 0 ? (int)(0x34 + dataOff) : 0x40;
        uint loopOff = U32(d, 0x1C);
        int loopBase = loopOff != 0 ? (int)(0x1C + loopOff) : -1;
        uint ym2608 = d.Length >= 0x4C ? U32(d, 0x48) : 0;
        if (ym2608 == 0) Console.Error.WriteLine("aviso: clock YM2608 = 0 no header (pode nao ser YM2608)");

        var stream = new List<ushort>();
        var adpcm = new List<byte>();
        long pendingFrames = 0;
        long sampleAcc = 0;
        long framesEmitted = 0;
        int loopWord = -1;
        bool truncated = false;

        void FlushWait()
        {
            while (pendingFrames > 0)
            {
                int chunk = (int)Math.Min(pendingFrames, 0x7FFF);
                stream.Add((ushort)chunk);
                pendingFrames -= chunk;
            }
        }
        void AddWait(int samples)
        {
            sampleAcc += samples;
            while (sampleAcc >= SAMPLES_PER_FRAME)
            {
                sampleAcc -= SAMPLES_PER_FRAME;
                pendingFrames++;
                framesEmitted++;
            }
        }
        void Write(int port, int reg, int val)
        {
            FlushWait();
            stream.Add((ushort)(0x8000 | (port << 14) | (reg & 0xFF)));
            stream.Add((ushort)(val & 0xFF));
        }

        int i2 = dataStart;
        bool done = false;
        while (i2 < d.Length && !done)
        {
            if (loopBase >= 0 && i2 == loopBase && loopWord < 0) { FlushWait(); loopWord = stream.Count; }
            if (framesEmitted >= maxFrames) { truncated = true; break; }

            byte c = d[i2];
            switch (c)
            {
                case 0x56: Write(0, d[i2 + 1], d[i2 + 2]); i2 += 3; break;
                case 0x57: Write(1, d[i2 + 1], d[i2 + 2]); i2 += 3; break;
                case 0x61: AddWait(d[i2 + 1] | (d[i2 + 2] << 8)); i2 += 3; break;
                case 0x62: AddWait(735); i2 += 1; break;
                case 0x63: AddWait(882); i2 += 1; break;
                case 0x66: done = true; break;
                case 0x67:
                {
                    byte type = d[i2 + 2];
                    int size = (int)U32(d, i2 + 3);
                    if (type == 0x81)
                        for (int k = 0; k < size; k++) adpcm.Add(d[i2 + 7 + k]);
                    i2 += 7 + size;
                    break;
                }
                case 0xE0: i2 += 5; break;
                case >= 0x70 and <= 0x7F: AddWait((c & 0x0F) + 1); i2 += 1; break;
                case >= 0x80 and <= 0x8F: AddWait(c & 0x0F); i2 += 1; break;
                default:
                    return Err($"comando VGM nao suportado 0x{c:X2} em offset 0x{i2:X}");
            }
        }

        FlushWait();
        stream.Add(0x0000);

        if ((adpcm.Count & 1) != 0) adpcm.Add(0);

        if (blob)
        {
            const int BANK = 0x4000;
            int adpcmWords = adpcm.Count / 2;
            var words = new List<ushort>();
            long loopBlob = loopWord >= 0 ? (8L + adpcmWords + loopWord) : 0xFFFFFFFFL;
            words.Add((ushort)(adpcm.Count >> 16)); words.Add((ushort)(adpcm.Count & 0xFFFF));
            words.Add((ushort)(loopBlob >> 16)); words.Add((ushort)(loopBlob & 0xFFFF));
            words.Add((ushort)(framesEmitted >> 16)); words.Add((ushort)(framesEmitted & 0xFFFF));
            words.Add(0); words.Add(0);
            for (int k = 0; k < adpcm.Count; k += 2) words.Add((ushort)((adpcm[k] << 8) | adpcm[k + 1]));
            words.AddRange(stream);
            while (words.Count % BANK != 0) words.Add(0);
            int banks = words.Count / BANK;
            var bb = new byte[words.Count * 2];
            for (int k = 0; k < words.Count; k++) { bb[k * 2] = (byte)(words[k] >> 8); bb[k * 2 + 1] = (byte)(words[k] & 0xFF); }
            string blobPath = outBase + ".blob";
            File.WriteAllBytes(blobPath, bb);
            Console.WriteLine($"OK: {blobPath} ({words.Count} words = {banks} banks)  adpcm={adpcm.Count}B stream={stream.Count}w frames={framesEmitted} (~{framesEmitted / 60}s) loopBlobWord={(loopWord >= 0 ? loopBlob.ToString() : "-")}" + (truncated ? "  [TRUNCADO]" : ""));
            return 0;
        }

        string streamPath = outBase + ".stream.bin";
        string adpcmPath = outBase + ".adpcm.bin";
        string incPath = outBase + ".inc.hasm";

        var sb = new byte[stream.Count * 2];
        for (int k = 0; k < stream.Count; k++) { sb[k * 2] = (byte)(stream[k] >> 8); sb[k * 2 + 1] = (byte)(stream[k] & 0xFF); }
        File.WriteAllBytes(streamPath, sb);
        File.WriteAllBytes(adpcmPath, adpcm.ToArray());

        string name = Path.GetFileNameWithoutExtension(outBase).ToUpperInvariant().Replace('.', '_').Replace('-', '_').Replace(' ', '_');
        File.WriteAllText(incPath,
            $"SONG_ADPCM_BYTES = {adpcm.Count}\n" +
            $"SONG_STREAM_WORDS = {stream.Count}\n" +
            $"SONG_LOOP_WORD = {(loopWord >= 0 ? loopWord : 0xFFFF)}\n");

        Console.WriteLine($"OK: {streamPath} ({stream.Count} words), {adpcmPath} ({adpcm.Count} bytes), {incPath}");
        Console.WriteLine($"    frames={framesEmitted} (~{framesEmitted / 60}s) loopWord={(loopWord >= 0 ? loopWord.ToString() : "-")}" + (truncated ? "  [TRUNCADO]" : ""));
        return 0;
    }

    static byte[] ReadMaybeGz(string path)
    {
        byte[] raw = File.ReadAllBytes(path);
        if (raw.Length >= 2 && raw[0] == 0x1F && raw[1] == 0x8B)
        {
            using var ms = new MemoryStream(raw);
            using var gz = new GZipStream(ms, CompressionMode.Decompress);
            using var outMs = new MemoryStream();
            gz.CopyTo(outMs);
            return outMs.ToArray();
        }
        return raw;
    }

    static uint U32(byte[] d, int o) => (uint)(d[o] | (d[o + 1] << 8) | (d[o + 2] << 16) | (d[o + 3] << 24));

    static int Err(string m) { Console.Error.WriteLine("erro: " + m); return 1; }
}
