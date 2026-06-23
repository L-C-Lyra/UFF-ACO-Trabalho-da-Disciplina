package dev.flaffy.horsepoweremu.emu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ROM {

    public static final int FIXED_WORDS  = 0x4000;
    public static final int BANK_WORDS   = 0x4000;
    public static final int HEADER_BYTES = 16;

    private int[] data = new int[0x8000];
    private int   mapperId;
    private int   numBanks = 1;

    private SystemIO io;

    public void setIO(SystemIO s) { this.io = s; }

    public void load(File file) throws IOException {
        byte[] raw = Files.readAllBytes(file.toPath());
        if (raw.length < HEADER_BYTES)
            throw new IOException("File too small — not a valid HROM");
        if (raw[0] != 'H' || raw[1] != 'R' || raw[2] != 'O' || raw[3] != 'M')
            throw new IOException("Invalid magic — not a HorsePower ROM");

        mapperId = raw[5] & 0xFF;
        numBanks = ((raw[6] & 0xFF) << 8) | (raw[7] & 0xFF);
        if (numBanks < 1) numBanks = 1;

        int totalWords = (raw.length - HEADER_BYTES) / 2;
        if (totalWords < 0x8000) totalWords = 0x8000;
        data = new int[totalWords];
        for (int i = 0; i < (raw.length - HEADER_BYTES) / 2; i++) {
            int off = HEADER_BYTES + i * 2;
            data[i] = ((raw[off] & 0xFF) << 8) | (raw[off + 1] & 0xFF);
        }
    }

    public int read(int addr) {
        if (addr < FIXED_WORDS) {
            return addr < data.length ? data[addr] : 0;
        }
        int bank = bankFor(addr);
        int idx = FIXED_WORDS + bank * BANK_WORDS + (addr - FIXED_WORDS);
        return idx < data.length ? data[idx] : 0;
    }

    private int bankFor(int addr) {
        if (mapperId == 0 || numBanks <= 1) return 0;
        int b = io != null ? io.getRomBank() : 0;
        return b % numBanks;
    }

    public int getMapperId()    { return mapperId; }
    public int getNumBanks()    { return numBanks; }
    public int getCurrentBank() { return bankFor(FIXED_WORDS); }
}
