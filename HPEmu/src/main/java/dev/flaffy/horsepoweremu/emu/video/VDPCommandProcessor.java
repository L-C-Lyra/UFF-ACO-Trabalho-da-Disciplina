package dev.flaffy.horsepoweremu.emu.video;

import java.util.function.IntConsumer;

//processador dos comandos aqui TODO
public class VDPCommandProcessor {

    interface Lop { int apply(int dest, int src, int mask); }

    private static final int VRAM_LIMIT = 0x1ffff;
    private static final double COMMAND_PER_PIXEL_DURATION_FACTOR = 1.1;

    private final Lop[] LOGICAL_OPERATIONS = {
        this::lopIMP, this::lopAND, this::lopOR, this::lopXOR, this::lopNOT, this::lopIMP, this::lopIMP, this::lopIMP,
        this::lopTIMP, this::lopTAND, this::lopTOR, this::lopTXOR, this::lopTNOT, this::lopIMP, this::lopIMP, this::lopIMP
    };

    private int turboClockMulti = 0;

    private V9958 vdp;
    private int[] vram, register, status;

    private int CE, TR;
    private int SX, SY, DX, DY, NX, NY, ENY, DIX, DIY, CX, CY, destPos;
    private Lop LOP;
    private boolean writeReady = false;
    private IntConsumer writeHandler = null;
    private Runnable readHandler = null;
    private long finishingCycle = 0;

    private V9958.ModeData modeData;
    private int modePPB, modePPBShift, modePPBMask;
    private int modeWidth;
    private int layoutLineBytes;

    public void connectVDP(V9958 pVDP, int[] pVRAM, int[] pRegister, int[] pStatus) {
        vdp = pVDP; vram = pVRAM; register = pRegister; status = pStatus;
    }

    public void reset() { STOP(); }

    public int getCE() { return CE; }
    public int getTR() { return TR; }

    public void startCommand(int val) {
        switch (val & 0xf0) {
            case 0xf0: HMMC(); break;
            case 0xe0: YMMM(); break;
            case 0xd0: HMMM(); break;
            case 0xc0: HMMV(); break;
            case 0xb0: LMMC(); break;
            case 0xa0: LMCM(); break;
            case 0x90: LMMM(); break;
            case 0x80: LMMV(); break;
            case 0x70: LINE(); break;
            case 0x60: SRCH(); break;
            case 0x50: PSET(); break;
            case 0x40: POINT(); break;
            case 0x00: STOP(); break;
        }
    }

    public void cpuWrite(int val) {
        if (writeHandler != null) writeHandler.accept(val);
        else { writeReady = true; TR = 0; }
    }

    public void cpuRead() {
        if (readHandler != null) readHandler.run();
        else TR = 0;
    }

    public void updateStatus() {
        if (CE != 0 && finishingCycle >= 0 && (finishingCycle == 0 || vdp.getVDPCycles() >= finishingCycle))
            finish();
        status[2] = (status[2] & ~0x81) | (TR << 7) | CE;
    }

    public void setVDPModeData(V9958.ModeData pModeData) {
        modeData = pModeData;
        modeWidth = modeData.width;
        modePPB = modeData.ppb != 0 ? modeData.ppb : 1;
        modePPBShift = modePPB >> 1;
        modePPBMask = ~0 << modePPBShift;
        layoutLineBytes = modeData.layLineBytes != 0 ? modeData.layLineBytes : 256;
    }

    private int getSX() { return ((register[33] & 0x01) << 8) | register[32]; }
    private int getSY() { return ((register[35] & 0x03) << 8) | register[34]; }
    private void setSY(int val) { register[35] = (val >> 8) & 0x03; register[34] = val & 0xff; }
    private int getDX() { return ((register[37] & 0x01) << 8) | register[36]; }
    private int getDY() { return ((register[39] & 0x03) << 8) | register[38]; }
    private void setDY(int val) { register[39] = (val >> 8) & 0x03; register[38] = val & 0xff; }
    private int getNX() { return ((register[41] & 0x01) << 8) | register[40]; }
    private int getNY() { return ((register[43] & 0x03) << 8) | register[42]; }
    private void setNY(int val) { register[43] = (val >> 8) & 0x03; register[42] = val & 0xff; }
    private int getDIX() { return (register[45] & 0x04) != 0 ? -1 : 1; }
    private int getDIY() { return (register[45] & 0x08) != 0 ? -1 : 1; }
    private int getCLR() { return register[44]; }
    private void setCLR(int val) { register[44] = val; }
    private int getMAJ() { return register[45] & 0x01; }
    private boolean getEQ() { return (register[45] & 0x02) == 0; }
    private Lop getLOP() { return LOGICAL_OPERATIONS[register[46] & 0x0f]; }

    private void HMMC() {
        int dx = getDX();
        DY = getDY(); NX = getNX(); NY = getNY(); DIX = getDIX(); DIY = getDIY();
        dx >>= modePPBShift; NX >>= modePPBShift;
        if (dx >= layoutLineBytes) { dx &= layoutLineBytes - 1; NX = 1; }
        else {
            NX = NX != 0 ? NX : layoutLineBytes;
            NX = DIX == 1 ? min(NX, layoutLineBytes - dx) : min(NX, dx + 1);
        }
        NY = NY != 0 ? NY : 1024;
        ENY = DIY == 1 ? NY : min(NY, DY + 1);
        destPos = DY * layoutLineBytes + dx;
        writeStart(this::HMMCNextWrite);
    }

    private void HMMCNextWrite(int co) {
        vram[destPos & VRAM_LIMIT] = co & 0xff;
        ++CX;
        if (CX >= NX) {
            destPos -= DIX * (NX - 1);
            CX = 0; ++CY;
            if (CY >= ENY) finish();
            else destPos += DIY * layoutLineBytes;
        } else destPos += DIX;
        setDY(DY + DIY * CY);
        setNY(NY - CY);
    }

    private void YMMM() {
        int sy = getSY(), dx = getDX(), dy = getDY(), ny = getNY(), dix = getDIX(), diy = getDIY();
        dx >>= modePPBShift;
        if (dx >= layoutLineBytes) dx &= layoutLineBytes - 1;
        int nx = dix == 1 ? layoutLineBytes - dx : dx + 1;
        ny = ny != 0 ? ny : 1024;
        int eny = diy == 1 ? ny : min(ny, min(sy, dy) + 1);
        int sPos = sy * layoutLineBytes + dx;
        int dPos = dy * layoutLineBytes + dx;
        int yStride = -(dix * nx) + layoutLineBytes * diy;
        for (int cy = eny; cy > 0; --cy) {
            for (int cx = nx; cx > 0; --cx) { vram[dPos & VRAM_LIMIT] = vram[sPos & VRAM_LIMIT]; sPos += dix; dPos += dix; }
            sPos += yStride; dPos += yStride;
        }
        setSY(sy + diy * eny); setDY(dy + diy * eny); setNY(ny - eny);
        start(nx * eny, 40 + 24, eny, 0, false);
    }

    private void HMMM() {
        int sx = getSX(), sy = getSY(), dx = getDX(), dy = getDY(), nx = getNX(), ny = getNY(), dix = getDIX(), diy = getDIY();
        sx >>= modePPBShift; dx >>= modePPBShift; nx >>= modePPBShift;
        if (sx >= layoutLineBytes || dx >= layoutLineBytes) { sx &= layoutLineBytes - 1; dx &= layoutLineBytes - 1; nx = 1; }
        else {
            nx = nx != 0 ? nx : layoutLineBytes;
            nx = dix == 1 ? min(nx, layoutLineBytes - max(sx, dx)) : min(nx, min(sx, dx) + 1);
        }
        ny = ny != 0 ? ny : 1024;
        int eny = diy == 1 ? ny : min(ny, min(sy, dy) + 1);
        int sPos = sy * layoutLineBytes + sx;
        int dPos = dy * layoutLineBytes + dx;
        int yStride = -(dix * nx) + layoutLineBytes * diy;
        for (int cy = eny; cy > 0; --cy) {
            for (int cx = nx; cx > 0; --cx) { vram[dPos & VRAM_LIMIT] = vram[sPos & VRAM_LIMIT]; sPos += dix; dPos += dix; }
            sPos += yStride; dPos += yStride;
        }
        setSY(sy + diy * eny); setDY(dy + diy * eny); setNY(ny - eny);
        start(nx * eny, 64 + 24, eny, 64, false);
    }

    private void HMMV() {
        int dx = getDX(), dy = getDY(), nx = getNX(), ny = getNY(), co = getCLR(), dix = getDIX(), diy = getDIY();
        dx >>= modePPBShift; nx >>= modePPBShift;
        if (dx >= layoutLineBytes) { dx &= layoutLineBytes - 1; nx = 1; }
        else {
            nx = nx != 0 ? nx : layoutLineBytes;
            nx = dix == 1 ? min(nx, layoutLineBytes - dx) : min(nx, dx + 1);
        }
        ny = ny != 0 ? ny : 1024;
        int eny = diy == 1 ? ny : min(ny, dy + 1);
        int pos = dy * layoutLineBytes + dx;
        int yStride = -(dix * nx) + layoutLineBytes * diy;
        for (int cy = eny; cy > 0; --cy) {
            for (int cx = nx; cx > 0; --cx) { vram[pos & VRAM_LIMIT] = co & 0xff; pos += dix; }
            pos += yStride;
        }
        setDY(dy + diy * eny); setNY(ny - eny);
        start(nx * eny, 48, eny, 56, false);
    }

    private void LMMC() {
        DX = getDX(); DY = getDY(); NX = getNX(); NY = getNY(); DIX = getDIX(); DIY = getDIY(); LOP = getLOP();
        if (DX >= modeWidth) { DX &= modeWidth - 1; NX = 1; }
        else {
            NX = NX != 0 ? NX : modeWidth;
            NX = DIX == 1 ? min(NX, modeWidth - DX) : min(NX, DX + 1);
        }
        NY = NY != 0 ? NY : 1024;
        ENY = DIY == 1 ? NY : min(NY, DY + 1);
        writeStart(this::LMMCNextWrite);
    }

    private void LMMCNextWrite(int co) {
        logicalPSET(DX, DY, co, LOP);
        ++CX;
        if (CX >= NX) {
            DX -= DIX * (NX - 1);
            CX = 0; ++CY; DY += DIY;
            if (CY >= ENY) finish();
        } else DX += DIX;
        setDY(DY); setNY(NY - CY);
    }

    private void LMCM() {
        SX = getSX(); SY = getSY(); NX = getNX(); NY = getNY(); DIX = getDIX(); DIY = getDIY();
        if (SX >= modeWidth) { SX &= modeWidth - 1; NX = 1; }
        else {
            NX = NX != 0 ? NX : modeWidth;
            NX = DIX == 1 ? min(NX, modeWidth - SX) : min(NX, SX + 1);
        }
        NY = NY != 0 ? NY : 1024;
        ENY = DIY == 1 ? NY : min(NY, SY + 1);
        readStart(this::LMCMNextRead);
    }

    private void LMCMNextRead() {
        status[7] = normalPGET(SX, SY);
        ++CX;
        if (CX >= NX) {
            SX -= DIX * (NX - 1);
            CX = 0; ++CY; SY += DIY;
            if (CY >= ENY) finish();
        } else SX += DIX;
        setSY(SY); setNY(NY - CY);
    }

    private void LMMM() {
        int sx = getSX(), sy = getSY(), dx = getDX(), dy = getDY(), nx = getNX(), ny = getNY(), dix = getDIX(), diy = getDIY();
        Lop op = getLOP();
        if (sx >= modeWidth || dx >= modeWidth) { sx &= modeWidth - 1; dx &= modeWidth - 1; nx = 1; }
        else {
            nx = nx != 0 ? nx : modeWidth;
            nx = dix == 1 ? min(nx, modeWidth - max(sx, dx)) : min(nx, min(sx, dx) + 1);
        }
        ny = ny != 0 ? ny : 1024;
        int eny = diy == 1 ? ny : min(ny, min(sy, dy) + 1);
        for (int cy = eny; cy > 0; --cy) {
            for (int cx = nx; cx > 0; --cx) { logicalPCOPY(dx, dy, sx, sy, op); sx += dix; dx += dix; }
            sx -= dix * nx; dx -= dix * nx; sy += diy; dy += diy;
        }
        setSY(sy); setDY(dy); setNY(ny - eny);
        start(nx * eny, 64 + 32 + 24, eny, 64, false);
    }

    private void LMMV() {
        int dx = getDX(), dy = getDY(), nx = getNX(), ny = getNY(), co = getCLR(), dix = getDIX(), diy = getDIY();
        Lop op = getLOP();
        if (dx >= modeWidth) { dx &= modeWidth - 1; nx = 1; }
        else {
            nx = nx != 0 ? nx : modeWidth;
            nx = dix == 1 ? min(nx, modeWidth - dx) : min(nx, dx + 1);
        }
        ny = ny != 0 ? ny : 1024;
        int eny = diy == 1 ? ny : min(ny, dy + 1);
        for (int cy = eny; cy > 0; --cy) {
            for (int cx = nx; cx > 0; --cx) { logicalPSET(dx, dy, co, op); dx += dix; }
            dx -= dix * nx; dy += diy;
        }
        setDY(dy); setNY(ny - eny);
        start(nx * eny, 72 + 24, eny, 64, false);
    }

    private void LINE() {
        int dx = getDX(), dy = getDY(), nx = getNX(), ny = getNY(), co = getCLR(), dix = getDIX(), diy = getDIY(), maj = getMAJ();
        Lop op = getLOP();
        int maxX = modeWidth - 1;
        dx &= maxX;
        int nMinor = 0;
        int e = 0;
        int n = 0;
        if (maj == 0) {
            for (n = 0; n <= nx; ++n) {
                logicalPSET(dx, dy, co, op);
                dx += dix;
                if (ny > 0) { e += ny; if ((e << 1) >= nx) { dy += diy; e -= nx; ++nMinor; } }
                if (dx > maxX || dx < 0 || dy < 0) break;
            }
        } else {
            for (n = 0; n <= nx; ++n) {
                logicalPSET(dx, dy, co, op);
                dy += diy;
                if (ny > 0) { e += ny; if ((e << 1) >= nx) { dx += dix; e -= nx; ++nMinor; } }
                if (dx > maxX || dx < 0 || dy < 0) break;
            }
        }
        setDY(dy);
        start(n, 88 + 24, nMinor, 32, false);
    }

    private void SRCH() {
        int sx = getSX(), sy = getSY(), co = getCLR(), dix = getDIX();
        boolean eq = getEQ();
        if (sx >= modeWidth) sx &= modeWidth - 1;
        int stopX = dix == 1 ? modeWidth : -1;
        int x = sx;
        boolean found = false;
        if (eq) {
            do { if (normalPGET(x, sy) == co) { found = true; break; } x = x + dix; } while (x != stopX);
        } else {
            do { if (normalPGET(x, sy) != co) { found = true; break; } x = x + dix; } while (x != stopX);
        }
        status[2] = (status[2] & ~0x10) | (found ? 0x10 : 0);
        status[8] = x & 255;
        status[9] = (x >> 8) & 1;
        start(Math.abs(x - sx) + 1, 86, 1, 50, false);
    }

    private void PSET() {
        int dx = getDX(), dy = getDY(), co = getCLR();
        Lop op = getLOP();
        if (dx >= modeWidth) dx &= modeWidth - 1;
        logicalPSET(dx, dy, co, op);
        start(0, 0, 1, 40, false);
    }

    private void POINT() {
        int sx = getSX(), sy = getSY();
        if (sx >= modeWidth) sx &= modeWidth - 1;
        int co = normalPGET(sx, sy);
        setCLR(co);
        status[7] = co;
        start(0, 0, 1, 40, false);
    }

    private void STOP() { finish(); }

    private int normalPGET(int x, int y) {
        int shift, mask;
        switch (modePPB) {
            case 2: shift = (x & 0x1) != 0 ? 0 : 4; x >>>= 1; mask = 0x0f << shift; break;
            case 4: shift = (3 - (x & 0x3)) * 2; x >>>= 2; mask = 0x03 << shift; break;
            default: shift = 0; mask = 0xff;
        }
        int pos = y * layoutLineBytes + x;
        return (vram[pos & VRAM_LIMIT] & mask) >> shift;
    }

    private void logicalPSET(int x, int y, int co, Lop op) {
        int shift, mask;
        switch (modePPB) {
            case 2: shift = (x & 0x1) != 0 ? 0 : 4; x >>>= 1; co = (co & 0x0f) << shift; mask = 0x0f << shift; break;
            case 4: shift = (3 - (x & 0x3)) * 2; x >>>= 2; co = (co & 0x03) << shift; mask = 0x03 << shift; break;
            default: mask = 0xff;
        }
        int pos = y * layoutLineBytes + x;
        vram[pos & VRAM_LIMIT] = op.apply(vram[pos & VRAM_LIMIT], co, mask) & 0xff;
    }

    private void logicalPCOPY(int dx, int dy, int sx, int sy, Lop op) {
        int sShift, dShift, mask;
        switch (modePPB) {
            case 2: sShift = (sx & 0x1) != 0 ? 0 : 4; dShift = (dx & 0x1) != 0 ? 0 : 4; sx >>>= 1; dx >>>= 1; mask = 0x0f; break;
            case 4: sShift = (3 - (sx & 0x3)) * 2; dShift = (3 - (dx & 0x3)) * 2; sx >>>= 2; dx >>>= 2; mask = 0x03; break;
            default: sShift = dShift = 0; mask = 0xff;
        }
        int sPos = sy * layoutLineBytes + sx;
        int dPos = dy * layoutLineBytes + dx;
        int co = ((vram[sPos & VRAM_LIMIT] >> sShift) & mask) << dShift;
        vram[dPos & VRAM_LIMIT] = op.apply(vram[dPos & VRAM_LIMIT], co, mask << dShift) & 0xff;
    }

    private int lopIMP(int dest, int src, int mask)  { return (dest & ~mask) | src; }
    private int lopAND(int dest, int src, int mask)  { return dest & (src | ~mask); }
    private int lopOR(int dest, int src, int mask)   { return dest | src; }
    private int lopXOR(int dest, int src, int mask)  { return dest ^ src; }
    private int lopNOT(int dest, int src, int mask)  { return (dest & ~mask) | (~src & mask); }
    private int lopTIMP(int dest, int src, int mask) { return src == 0 ? dest : (dest & ~mask) | src; }
    private int lopTAND(int dest, int src, int mask) { return src == 0 ? dest : dest & (src | ~mask); }
    private int lopTOR(int dest, int src, int mask)  { return src == 0 ? dest : dest | src; }
    private int lopTXOR(int dest, int src, int mask) { return dest ^ src; }
    private int lopTNOT(int dest, int src, int mask) { return src == 0 ? dest : (dest & ~mask) | (~src & mask); }

    private int min(int a, int b) { return a < b ? a : b; }
    private int max(int a, int b) { return a > b ? a : b; }

    private void start(int pixels, int cyclesPerPixel, int lines, int cyclesPerLine, boolean infinite) {
        CE = 1;
        writeHandler = null;
        readHandler = null;
        estimateDuration(pixels, cyclesPerPixel, lines, cyclesPerLine, infinite);
    }

    private void estimateDuration(int pixels, int cyclesPerPixel, int lines, int cyclesPerLine, boolean infinite) {
        if (infinite) finishingCycle = -1;
        else if (turboClockMulti == 0) finishingCycle = 0;
        else {
            long duration = (long) ((pixels * cyclesPerPixel * COMMAND_PER_PIXEL_DURATION_FACTOR + lines * cyclesPerLine) / turboClockMulti);
            finishingCycle = vdp.getVDPCycles() + duration;
        }
    }

    private void writeStart(IntConsumer handler) {
        start(0, 0, 0, 0, true);
        CX = 0; CY = 0;
        writeHandler = handler;
        TR = 1;
        if (writeReady) { writeHandler.accept(getCLR()); writeReady = false; }
    }

    private void readStart(Runnable handler) {
        start(0, 0, 0, 0, true);
        CX = 0; CY = 0;
        readHandler = handler;
        TR = 1;
        readHandler.run();
    }

    private void finish() {
        CE = 0;
        writeHandler = null;
        writeReady = false;
        readHandler = null;
        register[46] &= ~0xf0;
    }
}
