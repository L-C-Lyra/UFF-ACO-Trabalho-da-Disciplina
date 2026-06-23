package dev.flaffy.horsepoweremu.emu.video;

//VDP Yamaha V9958 emulador baseado no NAME
public class V9958 {

    public static final int VRAM_LIMIT = 0x1ffff;
    public static final int VRAM_SIZE = 0x20000;

    public static final int LINE_WIDTH = 544;
    public static final int RENDER_HEIGHT = 228;
    private static final long SPRITE_MAX_PRIORITY = 9000000000000000L;

    public static class ModeData {
        String name; boolean isV9938;
        int layTBase, colorTBase, patTBase, sprAttrTBase;
        int width, layLineBytes, evenPageMask, blinkPageMask;
        int renderer; int ppb; int spriteMode;
        boolean tiled; Boolean vramInter; boolean bdPaletted; int textCols;
        ModeData(String name, boolean isV9938, int layTBase, int colorTBase, int patTBase, int sprAttrTBase,
                 int width, int layLineBytes, int evenPageMask, int blinkPageMask, int renderer, int ppb,
                 int spriteMode, boolean tiled, Boolean vramInter, boolean bdPaletted, int textCols) {
            this.name=name; this.isV9938=isV9938; this.layTBase=layTBase; this.colorTBase=colorTBase;
            this.patTBase=patTBase; this.sprAttrTBase=sprAttrTBase; this.width=width; this.layLineBytes=layLineBytes;
            this.evenPageMask=evenPageMask; this.blinkPageMask=blinkPageMask; this.renderer=renderer; this.ppb=ppb;
            this.spriteMode=spriteMode; this.tiled=tiled; this.vramInter=vramInter; this.bdPaletted=bdPaletted; this.textCols=textCols;
        }
    }

    private static final int R_NUL=0, R_T1=1, R_T2=2, R_MC=3, R_G1=4, R_G2=5, R_G3=6,
                             R_G4=7, R_G5=8, R_G6=9, R_G7=10, R_YJK=11, R_YAE=12;

    private final ModeData[] modes = new ModeData[0x24];

    private final int[] vram = new int[VRAM_SIZE];
    private final int[] register = new int[64];
    private final int[] status = new int[16];
    private final int[] paletteRegister = new int[16];

    private final int[] frameBackBuffer = new int[LINE_WIDTH * (RENDER_HEIGHT + 2)];
    private final int[] frontBuffer = new int[LINE_WIDTH * (RENDER_HEIGHT + 2)];
    private volatile int frontWidth = 272, frontHeight = RENDER_HEIGHT;
    private final int[] backdropLineCache = new int[LINE_WIDTH];

    private final boolean isV9918 = false, isV9938 = false, isV9958 = true;

    private VDPCommandProcessor commandProcessor;
    private ModeData modeData;

    private boolean videoDisplayed = true;
    private boolean vramInterleaving;
    private long frame;
    private long vdpCycles;

    private boolean blinkEvenPage, blinkPerLine; private int blinkPageDuration;

    private int bufferPosition, bufferLineAdvance, currentScanline;
    private int signalActiveHeight, finishingScanline, startingActiveScanline, frameStartingActiveScanline;
    private int startingVisibleTopBorderScanline, startingInvisibleScanline;

    private int horizontalIntLine;
    private int F, FH, VR, HR, EO;
    private boolean vdpInterrupt;

    private int renderWidth = 272, renderHeight = RENDER_HEIGHT;
    private boolean backdropCacheUpdatePending = true;

    private boolean spritesEnabled; private int spritesSize, spritesMag;
    private boolean spritesCollided; private int spritesInvalid, spritesMaxComputed, spritesCollisionX, spritesCollisionY;
    private final long[] spritesLinePriorities = new long[256];
    private final int[] spritesLineColors = new int[256];
    private long spritesGlobalPriority;

    private int vramPointer = 0;
    private Integer paletteFirstWrite;
    private Integer dataFirstWrite; private int dataPreRead = 0;

    private int backdropColor, backdropValue, backdropTileOdd, backdropTileEven;
    private int verticalAdjust, horizontalAdjust;
    private boolean leftMask, leftScroll2Pages; private int leftScrollChars, leftScrollCharsInPage, rightScrollPixels;

    private int layoutTableAddress, colorTableAddress, patternTableAddress, spriteAttrTableAddress, spritePatternTableAddress;
    private int layoutTableAddressMask, layoutTableAddressMaskSetValue, colorTableAddressMask, patternTableAddressMask;

    private static final int layoutTableAddressMaskBase = ~(-1 << 10);
    private static final int colorTableAddressMaskBase = ~(-1 << 6);
    private static final int patternTableAddressMaskBase = ~(-1 << 11);

    private boolean color0Solid = false;
    private final int[] colorPalette = new int[16];
    private final int[] colorPaletteSolid = new int[16];
    private final int[] colorPaletteReal = new int[16];

    private static final int[] color2to8bits9938 = { 0, 73, 146, 255 };
    private static final int[] color3to8bits9938 = { 0, 36, 73, 109, 146, 182, 219, 255 };
    private static final int[] color5to8bits = { 0, 8, 16, 24, 32, 41, 49, 57, 65, 74, 82, 90, 98, 106, 115, 123, 131, 139, 148, 156, 164, 172, 180, 189, 197, 205, 213, 222, 230, 238, 246, 255 };

    private static final int[] colors9bitValues = new int[512];
    private static final int[] colors8bitValues = new int[256];
    private static int[] colorsYJKValues = null;

    private static final int[] spritePaletteG7 = new int[16];

    private static final int[] paletteRegisterInitialValuesV9938 = {
        0x000, 0x000, 0x611, 0x733, 0x117, 0x327, 0x151, 0x627, 0x121, 0x373, 0x661, 0x664, 0x411, 0x265, 0x365, 0x777 };

    static {
        for (int c = 0; c < 512; c++) {
            int r = color3to8bits9938[(c >> 3) & 0x7], g = color3to8bits9938[c >> 6], b = color3to8bits9938[c & 0x7];
            colors9bitValues[c] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
        for (int c = 0; c < 256; c++) {
            int r = color3to8bits9938[(c >> 2) & 0x7], g = color3to8bits9938[c >> 5], b = color2to8bits9938[c & 0x3];
            colors8bitValues[c] = 0xff000000 | (r << 16) | (g << 8) | b;
        }
        int[] g7 = { 0xff000000, 0xff490000, 0xff00006d, 0xff49006d, 0xff006d00, 0xff496d00, 0xff006d6d, 0xff496d6d,
                     0xff4992ff, 0xffff0000, 0xff0000ff, 0xffff00ff, 0xff00ff00, 0xffffff00, 0xff00ffff, 0xffffffff };
        for (int i = 0; i < 16; i++) {
            int v = g7[i];
            int r = v & 0xff, gg = (v >> 8) & 0xff, b = (v >> 16) & 0xff;
            spritePaletteG7[i] = 0xff000000 | (r << 16) | (gg << 8) | b;
        }
    }

    private static int[] getColorsYJKValues() {
        if (colorsYJKValues == null) {
            colorsYJKValues = new int[131072];
            for (int c = 0; c < 131072; c++) {
                int y = c >> 12, j = signed((c >> 6) & 0x3f), k = signed(c & 0x3f);
                int r = trunc(y + j), g = trunc(y + k), b = trunc((y * 5 - (j << 1) - k) >> 2);
                colorsYJKValues[c] = 0xff000000 | (color5to8bits[r] << 16) | (color5to8bits[g] << 8) | color5to8bits[b];
            }
        }
        return colorsYJKValues;
    }

    private static int signed(int x) { return x > 31 ? x - 64 : x; }
    private static int trunc(int x)  { return x <= 0 ? 0 : x >= 31 ? 31 : x; }

    public V9958() {
        initModes();
        modeData = modes[0];
        commandProcessor = new VDPCommandProcessor();
        commandProcessor.connectVDP(this, vram, register, status);
        commandProcessor.setVDPModeData(modeData);
        reset();
    }

    public int[] getFrameBuffer()  { return frameBackBuffer; }
    public int[] getFrontBuffer()  { return frontBuffer; }
    public int getFrontWidth()     { return frontWidth; }
    public int getFrontHeight()    { return frontHeight; }
    public int getRenderWidth()    { return renderWidth; }
    public int getRenderHeight()   { return renderHeight; }
    public int getLineStride()     { return LINE_WIDTH; }
    public boolean isIRQ()         { return vdpInterrupt; }
    public long getVDPCycles()     { return vdpCycles; }
    public int[] getVRAM()         { return vram; }
    public int[] getRegisters()    { return register; }
    public int[] getPaletteARGB()  { return colorPaletteReal.clone(); }
    public int color8(int v)       { return colors8bitValues[v & 0xff]; }

    public void reset() {
        frame = 0; vdpCycles = 0;
        dataFirstWrite = null; dataPreRead = 0; vramPointer = 0; paletteFirstWrite = null;
        verticalAdjust = horizontalAdjust = 0;
        leftMask = leftScroll2Pages = false; leftScrollChars = leftScrollCharsInPage = rightScrollPixels = 0;
        backdropColor = backdropValue = 0;
        spritesCollided = false; spritesCollisionX = spritesCollisionY = spritesInvalid = -1; spritesMaxComputed = 0;
        horizontalIntLine = 0;
        vramInterleaving = false;
        currentScanline = -1;
        F = FH = VR = HR = EO = 0;
        vdpInterrupt = false;
        java.util.Arrays.fill(spritesLinePriorities, SPRITE_MAX_PRIORITY);
        java.util.Arrays.fill(spritesLineColors, 0);
        java.util.Arrays.fill(vram, 0);
        spritesGlobalPriority = SPRITE_MAX_PRIORITY;
        initRegisters();
        initColorPalette();
        commandProcessor.reset();
        updateSignalMetrics(true);
        updateIRQ();
        updateMode();
        updateSpritesConfig();
        updateBackdropColor();
        updateTransparency();
        updateBlinking();
        beginFrame();
    }

    public int readData() {
        dataFirstWrite = null;
        int res = dataPreRead;
        dataPreRead = vram[vramPointer & VRAM_LIMIT];
        ++vramPointer;
        checkVRAMPointerWrap();
        return res & 0xff;
    }

    public void writeData(int val) {
        val &= 0xff;
        dataFirstWrite = null;
        vram[vramPointer & VRAM_LIMIT] = dataPreRead = val;
        ++vramPointer;
        checkVRAMPointerWrap();
    }

    public int readStatus() {
        dataFirstWrite = null;
        int reg = register[15];
        int res;
        switch (reg) {
            case 0: res = getStatus0(); break;
            case 1:
                res = status[1] | FH;
                if ((register[0] & 0x10) != 0 && FH != 0) { FH = 0; updateIRQ(); }
                break;
            case 2:
                commandProcessor.updateStatus();
                res = status[2] | (VR << 6) | (HR << 5) | (EO << 1);
                break;
            case 3: case 4: case 6:
                res = status[reg]; break;
            case 5:
                res = status[5];
                spritesCollisionX = spritesCollisionY = -1;
                status[3] = status[4] = status[5] = status[6] = 0;
                break;
            case 7:
                res = status[7];
                commandProcessor.cpuRead();
                break;
            case 8: case 9:
                res = status[reg]; break;
            default: res = 0xff;
        }
        return res & 0xff;
    }

    public void writeCtrl(int val) {
        val &= 0xff;
        if (dataFirstWrite == null) {
            dataFirstWrite = val;
        } else {
            if ((val & 0x80) != 0) {
                if ((val & 0x40) == 0) registerWrite(val & 0x3f, dataFirstWrite);
            } else {
                vramPointer = (vramPointer & 0x1c000) | ((val & 0x3f) << 8) | dataFirstWrite;
                if ((val & 0x40) == 0) {
                    dataPreRead = vram[vramPointer & VRAM_LIMIT];
                    ++vramPointer;
                    checkVRAMPointerWrap();
                }
            }
            dataFirstWrite = null;
        }
    }

    public void writePalette(int val) {
        val &= 0xff;
        if (paletteFirstWrite == null) {
            paletteFirstWrite = val;
        } else {
            paletteRegisterWrite(register[16], (val << 8) | paletteFirstWrite, false);
            if (++register[16] > 15) register[16] = 0;
            paletteFirstWrite = null;
        }
    }

    public void writeRegIndirect(int val) {
        val &= 0xff;
        int reg = register[17] & 0x3f;
        if (reg != 17) registerWrite(reg, val);
        if ((register[17] & 0x80) == 0) register[17] = (reg + 1) & 0x3f;
    }

    private void registerWrite(int reg, int val) {
        if (reg > 46) return;
        val &= 0xff;
        int add;
        int mod = register[reg] ^ val;
        register[reg] = val;
        switch (reg) {
            case 0:
                if ((mod & 0x10) != 0) updateIRQ();
                if ((mod & 0x0e) != 0) updateMode();
                break;
            case 1:
                if ((mod & 0x20) != 0) updateIRQ();
                if ((mod & 0x18) != 0) updateMode();
                if ((mod & 0x04) != 0) updateBlinking();
                if ((mod & 0x03) != 0) updateSpritesConfig();
                break;
            case 2:
                if ((mod & 0x7f) != 0) updateLayoutTableAddress();
                break;
            case 10:
                if ((mod & 0x07) == 0) break;
            case 3:
                add = ((register[10] << 14) | (register[3] << 6)) & 0x1ffff;
                colorTableAddress = add & modeData.colorTBase;
                colorTableAddressMask = add | colorTableAddressMaskBase;
                break;
            case 4:
                if ((mod & 0x3f) == 0) break;
                add = (val << 11) & 0x1ffff;
                patternTableAddress = add & modeData.patTBase;
                patternTableAddressMask = add | patternTableAddressMaskBase;
                break;
            case 11:
                if ((mod & 0x03) == 0) break;
            case 5:
                add = ((register[11] << 15) | (register[5] << 7)) & 0x1ffff;
                spriteAttrTableAddress = add & modeData.sprAttrTBase;
                break;
            case 6:
                if ((mod & 0x3f) != 0) updateSpritePatternTableAddress();
                break;
            case 7:
                if ((mod & (modeData.bdPaletted ? 0x0f : 0xff)) != 0) updateBackdropColor();
                break;
            case 8:
                if ((mod & 0x20) != 0) updateTransparency();
                if ((mod & 0x02) != 0) updateSpritesConfig();
                break;
            case 9:
                if ((mod & 0x80) != 0) updateSignalMetrics(false);
                if ((mod & 0x08) != 0) updateRenderMetrics();
                if ((mod & 0x04) != 0) updateLayoutTableAddressMask();
                break;
            case 13:
                updateBlinking();
                break;
            case 14:
                if ((mod & 0x07) != 0) vramPointer = ((val & 0x07) << 14) | (vramPointer & 0x3fff);
                break;
            case 16:
                paletteFirstWrite = null;
                break;
            case 18:
                if ((mod & 0x0f) != 0) horizontalAdjust = -7 + ((val & 0x0f) ^ 0x07);
                if ((mod & 0xf0) != 0) { verticalAdjust = -7 + ((val >>> 4) ^ 0x07); updateSignalMetrics(false); }
                break;
            case 19:
                horizontalIntLine = (val - register[23]) & 255;
                break;
            case 23:
                horizontalIntLine = (register[19] - val) & 255;
                break;
            case 25:
                if (isV9958) {
                    if ((mod & 0x18) != 0) updateMode();
                    leftMask = (val & 0x02) != 0;
                    leftScroll2Pages = (val & 0x01) != 0;
                }
                break;
            case 26:
                if (isV9958) { leftScrollChars = val & 0x3f; leftScrollCharsInPage = leftScrollChars & 31; }
                break;
            case 27:
                if (isV9958) rightScrollPixels = val & 0x07;
                break;
            case 44:
                commandProcessor.cpuWrite(val);
                break;
            case 46:
                commandProcessor.startCommand(val);
                break;
        }
    }

    private void updateLayoutTableAddress() {
        int add = modeData.vramInter == Boolean.TRUE ? ((register[2] & 0x3f) << 11) | (1 << 10) : (register[2] & 0x7f) << 10;
        layoutTableAddress = add & modeData.layTBase;
        layoutTableAddressMaskSetValue = add | layoutTableAddressMaskBase;
        updateLayoutTableAddressMask();
    }

    private void updateLayoutTableAddressMask() {
        layoutTableAddressMask = layoutTableAddressMaskSetValue &
            (blinkEvenPage || ((register[9] & 0x04) != 0 && EO == 0) ? modeData.blinkPageMask : ~0);
    }

    private void updateSpritePatternTableAddress() {
        spritePatternTableAddress = (register[6] << 11) & 0x1ffff;
    }

    private int getStatus0() {
        int res = 0;
        if (F != 0) { res |= 0x80; F = 0; updateIRQ(); }
        if (spritesCollided) { res |= 0x20; spritesCollided = false; }
        if (spritesInvalid >= 0) { res |= 0x40 | spritesInvalid; spritesInvalid = -1; }
        else res |= spritesMaxComputed;
        spritesMaxComputed = 0;
        return res;
    }

    private void checkVRAMPointerWrap() {
        if ((vramPointer & 0x3fff) == 0) {
            register[14] = (register[14] + 1) & 0x07;
            vramPointer = register[14] << 14;
        }
    }

    private void paletteRegisterWrite(int reg, int val, boolean force) {
        if (paletteRegister[reg] == val && !force) return;
        paletteRegister[reg] = val;
        int value = getColorValueForPaletteValue(val);
        colorPaletteReal[reg] = value;
        colorPaletteSolid[reg] = value;
        if (reg == 0) { if (color0Solid) colorPalette[0] = value; }
        else colorPalette[reg] = value;
        if (reg == backdropColor) updateBackdropValue();
        else if (modeData.tiled && reg <= 3) backdropCacheUpdatePending = true;
    }

    private int getColorValueForPaletteValue(int val) {
        return colors9bitValues[((val & 0x700) >>> 2) | ((val & 0x70) >>> 1) | (val & 0x07)];
    }

    private void updateAllPaletteValues() {
        for (int reg = 0; reg < 16; reg++) paletteRegisterWrite(reg, paletteRegister[reg], true);
    }

    public void clockLine() {
        if (blinkPerLine && blinkPageDuration > 0)
            if (clockPageBlinking()) updateLayoutTableAddressMask();

        if (currentScanline == startingActiveScanline) inActiveRegion = true;
        else if (currentScanline - frameStartingActiveScanline == signalActiveHeight) inActiveRegion = false;

        if (FH != 0 && (register[0] & 0x10) == 0) FH = 0;
        if (currentScanline == startingActiveScanline - 1) VR = 0;
        else if (currentScanline - frameStartingActiveScanline == signalActiveHeight) triggerVerticalInterrupt();

        HR = 0;
        if (currentScanline >= startingVisibleTopBorderScanline && currentScanline < startingInvisibleScanline)
            renderLine();
        HR = 1;
        if (currentScanline - frameStartingActiveScanline == horizontalIntLine) triggerHorizontalInterrupt();

        ++currentScanline;
        vdpCycles += 1368;
        if (currentScanline >= finishingScanline) finishFrame();
    }

    private boolean inActiveRegion = false;

    private void triggerVerticalInterrupt() {
        VR = 1;
        if (F == 0) { F = 1; updateIRQ(); }
    }

    private void triggerHorizontalInterrupt() {
        if (FH == 0) { FH = 1; updateIRQ(); }
    }

    private void updateIRQ() {
        vdpInterrupt = (F != 0 && (register[1] & 0x20) != 0) || (FH != 0 && (register[0] & 0x10) != 0);
    }

    private void updateVRAMInterleaving() {
        if (modeData.vramInter == Boolean.TRUE && !vramInterleaving) vramEnterInterleaving();
        else if (modeData.vramInter == Boolean.FALSE && vramInterleaving) vramExitInterleaving();
    }

    private void vramEnterInterleaving() {
        int o = VRAM_SIZE >> 1;
        int[] aux = new int[o];
        System.arraycopy(vram, 0, aux, 0, o);
        int e = 0;
        for (int i = 0; i < VRAM_SIZE; i += 2, ++e, ++o) {
            vram[i] = aux[e];
            vram[i + 1] = vram[o];
        }
        vramInterleaving = true;
    }

    private void vramExitInterleaving() {
        int h = VRAM_SIZE >> 1;
        int e = 0, o = h;
        int[] aux = new int[VRAM_SIZE - h];
        System.arraycopy(vram, h, aux, 0, VRAM_SIZE - h);
        for (int i = 0; i < h; i += 2, ++e, ++o) { vram[e] = vram[i]; vram[o] = vram[i + 1]; }
        for (int i = 0; i < h; i += 2, ++e, ++o) { vram[e] = aux[i]; vram[o] = aux[i + 1]; }
        vramInterleaving = false;
    }

    private void updateMode() {
        ModeData oldData = modeData;
        int modeBits = (register[1] & 0x18) | ((register[0] & 0x0e) >>> 1);
        commandProcessor.setVDPModeData(modes[modeBits]);
        if (isV9958 && (register[25] & 0x08) != 0 && (modeBits & 0x10) == 0)
            modeBits = 0x20 | ((register[25] & 0x18) >> 3);
        modeData = modes[modeBits];

        int add;
        updateLayoutTableAddress();
        add = ((register[10] << 14) | (register[3] << 6)) & 0x1ffff;
        colorTableAddress = add & modeData.colorTBase;
        colorTableAddressMask = add | colorTableAddressMaskBase;
        add = (register[4] << 11) & 0x1ffff;
        patternTableAddress = add & modeData.patTBase;
        patternTableAddressMask = add | patternTableAddressMaskBase;
        add = ((register[11] << 15) | (register[5] << 7)) & 0x1ffff;
        spriteAttrTableAddress = add & modeData.sprAttrTBase;
        updateSpritePatternTableAddress();

        if (modeData.bdPaletted != oldData.bdPaletted) updateBackdropColor();
        if (modeData.tiled != oldData.tiled) backdropCacheUpdatePending = true;

        updateVRAMInterleaving();
        updateRenderMetrics();
    }

    private void updateSignalMetrics(boolean force) {
        int addBorder;
        if ((register[9] & 0x80) != 0) { signalActiveHeight = 212; addBorder = 0; }
        else { signalActiveHeight = 192; addBorder = 10; }
        startingVisibleTopBorderScanline = 16 - 8;
        startingActiveScanline = startingVisibleTopBorderScanline + 8 + addBorder + verticalAdjust;
        int startingVisibleBottomBorderScanline = startingActiveScanline + signalActiveHeight;
        startingInvisibleScanline = startingVisibleBottomBorderScanline + 8 + addBorder - verticalAdjust;
        finishingScanline = 262;
        if (force) frameStartingActiveScanline = startingActiveScanline;
    }

    private void updateRenderMetrics() {
        renderWidth = modeData.width == 512 ? 544 : 272;
        renderHeight = RENDER_HEIGHT;
    }

    private void renderLine() {
        if (!inActiveRegion) { renderLineBorders(); return; }
        if ((register[1] & 0x40) == 0) { renderLineBorders(); return; }
        switch (modeData.renderer) {
            case R_T1:  renderLineModeT1();  break;
            case R_T2:  renderLineModeT2();  break;
            case R_MC:  renderLineModeMC();  break;
            case R_G1:  renderLineModeG1();  break;
            case R_G2:  renderLineModeG2();  break;
            case R_G3:  renderLineModeG3();  break;
            case R_G4:  renderLineModeG4();  break;
            case R_G5:  renderLineModeG5();  break;
            case R_G6:  renderLineModeG6();  break;
            case R_G7:  renderLineModeG7();  break;
            case R_YJK: renderLineModeYJK(); break;
            case R_YAE: renderLineModeYAE(); break;
            default:    renderLineBorders(); break;
        }
    }

    private void updateSpritesConfig() {
        spritesEnabled = (register[8] & 0x02) == 0;
        spritesSize = (register[1] & 0x02) != 0 ? 16 : 8;
        spritesMag = register[1] & 0x01;
    }

    private void updateTransparency() {
        color0Solid = (register[8] & 0x20) != 0;
        colorPalette[0] = color0Solid ? colorPaletteSolid[0] : backdropValue;
    }

    private void updateBackdropColor() {
        backdropColor = register[7] & (modeData.bdPaletted ? 0x0f : 0xff);
        updateBackdropValue();
    }

    private void updateBackdropValue() {
        int value = modeData.bdPaletted ? colorPaletteSolid[backdropColor] : colors8bitValues[backdropColor];
        if (backdropValue == value) return;
        backdropValue = value;
        if (!color0Solid) colorPalette[0] = value;
        backdropCacheUpdatePending = true;
    }

    private void updateBackdropLineCache() {
        if (modeData.tiled) {
            int odd = colorPaletteSolid[backdropColor >>> 2];
            int even = colorPaletteSolid[backdropColor & 0x03];
            for (int i = 0; i < LINE_WIDTH; i += 2) { backdropLineCache[i] = odd; backdropLineCache[i + 1] = even; }
            backdropTileOdd = odd; backdropTileEven = even;
        } else {
            java.util.Arrays.fill(backdropLineCache, backdropValue);
        }
        backdropCacheUpdatePending = false;
    }

    private void updateBlinking() {
        blinkPerLine = (register[1] & 0x04) != 0;
        if ((register[13] >>> 4) == 0) { blinkEvenPage = false; blinkPageDuration = 0; }
        else if ((register[13] & 0x0f) == 0) { blinkEvenPage = true; blinkPageDuration = 0; }
        else { blinkEvenPage = true; blinkPageDuration = 1; }
        updateLayoutTableAddressMask();
    }

    private boolean clockPageBlinking() {
        if (--blinkPageDuration == 0) {
            blinkEvenPage = !blinkEvenPage;
            blinkPageDuration = ((register[13] >>> (blinkEvenPage ? 4 : 0)) & 0x0f) * 10;
            return true;
        }
        return false;
    }

    private int getRealLine() {
        return (currentScanline - frameStartingActiveScanline + register[23]) & 255;
    }

    private void renderLineBorders() {
        if (backdropCacheUpdatePending) updateBackdropLineCache();
        System.arraycopy(backdropLineCache, 0, frameBackBuffer, bufferPosition, LINE_WIDTH);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeT1() {
        paintBackdrop16(bufferPosition); paintBackdrop16(bufferPosition + 256);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels;
        int realLine = getRealLine();
        int colorCode = register[7];
        int lineInPattern = patternTableAddress + (realLine & 0x07);
        int on = colorPalette[colorCode >>> 4];
        int off = colorPalette[colorCode & 0xf];
        int namePos = layoutTableAddress + (realLine >>> 3) * 40;
        paintBackdrop8(bufferPos); bufferPos += 8;
        for (int c = 0; c < 40; ++c) {
            int name = vram[namePos]; ++namePos;
            int pattern = vram[(name << 3) + lineInPattern];
            paintPattern6(bufferPos, pattern, on, off);
            bufferPos += 6;
        }
        paintBackdrop8(bufferPos); bufferPos += 8;
        bufferPos -= rightScrollPixels + 256;
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeT2() {
        paintBackdrop32(bufferPosition); paintBackdrop32(bufferPosition + 512);
        int bufferPos = bufferPosition + 16 + ((horizontalAdjust + rightScrollPixels) << 1);
        int realLine = getRealLine();
        int lineInPattern = patternTableAddress + (realLine & 0x07);
        int name, pattern, colorCode, on, off;
        int namePos = layoutTableAddress + (realLine >>> 3) * 80;
        paintBackdrop16(bufferPos); bufferPos += 16;
        if (blinkEvenPage) {
            int blinkPos = colorTableAddress + (realLine >>> 3) * 10;
            int blinkBit = 7;
            for (int c = 0; c < 80; ++c) {
                int blink = (vram[blinkPos & colorTableAddressMask] >>> blinkBit) & 1;
                name = vram[namePos & layoutTableAddressMask]; ++namePos;
                colorCode = register[blink != 0 ? 12 : 7];
                pattern = vram[(name << 3) + lineInPattern];
                on = blink != 0 ? colorPaletteSolid[colorCode >>> 4] : colorPalette[colorCode >>> 4];
                off = blink != 0 ? colorPaletteSolid[colorCode & 0xf] : colorPalette[colorCode & 0xf];
                paintPattern6(bufferPos, pattern, on, off);
                if (--blinkBit < 0) { ++blinkPos; blinkBit = 7; }
                bufferPos += 6;
            }
        } else {
            colorCode = register[7];
            on = colorPalette[colorCode >>> 4];
            off = colorPalette[colorCode & 0xf];
            for (int c = 0; c < 80; ++c) {
                name = vram[namePos & layoutTableAddressMask]; ++namePos;
                pattern = vram[(name << 3) + lineInPattern];
                paintPattern6(bufferPos, pattern, on, off);
                bufferPos += 6;
            }
        }
        paintBackdrop16(bufferPos); bufferPos += 16;
        bufferPos -= (rightScrollPixels << 1) + 512;
        if (leftMask) paintBackdrop16(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop16(bufferPos + 512);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeMC() {
        int realLine = getRealLine();
        paintBackdrop16(bufferPosition); paintBackdrop16(bufferPosition + 256);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels;
        int extraPatPos = patternTableAddress + (((realLine >>> 3) & 0x03) << 1) + ((realLine >>> 2) & 0x01);
        int namePosBase = layoutTableAddress + ((realLine >>> 3) << 5);
        int namePos = namePosBase + leftScrollCharsInPage;
        if (leftScroll2Pages && leftScrollChars < 32) namePos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) namePos = leftScroll2Pages && leftScrollChars >= 32 ? namePosBase & modeData.evenPageMask : namePosBase;
            int name = vram[namePos]; ++namePos;
            int patternLine = (name << 3) + extraPatPos;
            int colorCode = vram[patternLine];
            int on = colorPalette[colorCode >>> 4];
            int off = colorPalette[colorCode & 0xf];
            paintPattern8(bufferPos, 0xf0, on, off);
            bufferPos += 8;
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode1(realLine, bufferPos);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeG1() {
        int realLine = getRealLine();
        paintBackdrop16(bufferPosition); paintBackdrop16(bufferPosition + 256);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels;
        int lineInPattern = patternTableAddress + (realLine & 0x07);
        int namePosBase = layoutTableAddress + ((realLine >>> 3) << 5);
        int namePos = namePosBase + leftScrollCharsInPage;
        if (leftScroll2Pages && leftScrollChars < 32) namePos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) namePos = leftScroll2Pages && leftScrollChars >= 32 ? namePosBase & modeData.evenPageMask : namePosBase;
            int name = vram[namePos]; ++namePos;
            int colorCode = vram[colorTableAddress + (name >>> 3)];
            int pattern = vram[(name << 3) + lineInPattern];
            int on = colorPalette[colorCode >>> 4];
            int off = colorPalette[colorCode & 0xf];
            paintPattern8(bufferPos, pattern, on, off);
            bufferPos += 8;
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode1(realLine, bufferPos);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeG2() {
        int realLine = getRealLine();
        paintBackdrop16(bufferPosition); paintBackdrop16(bufferPosition + 256);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels;
        int lineInColor = colorTableAddress + (realLine & 0x07);
        int lineInPattern = patternTableAddress + (realLine & 0x07);
        int blockExtra = (realLine & 0xc0) << 2;
        int namePosBase = layoutTableAddress + ((realLine >>> 3) << 5);
        int namePos = namePosBase + leftScrollCharsInPage;
        if (leftScroll2Pages && leftScrollChars < 32) namePos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) namePos = leftScroll2Pages && leftScrollChars >= 32 ? namePosBase & modeData.evenPageMask : namePosBase;
            int name = vram[namePos] | blockExtra; ++namePos;
            int colorCode = vram[((name << 3) + lineInColor) & colorTableAddressMask];
            int pattern = vram[((name << 3) + lineInPattern) & patternTableAddressMask];
            int on = colorPalette[colorCode >>> 4];
            int off = colorPalette[colorCode & 0xf];
            paintPattern8(bufferPos, pattern, on, off);
            bufferPos += 8;
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode1(realLine, bufferPos);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeG3() {
        int realLine = getRealLine();
        paintBackdrop16(bufferPosition); paintBackdrop16(bufferPosition + 256);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels;
        int lineInColor = colorTableAddress + (realLine & 0x07);
        int lineInPattern = patternTableAddress + (realLine & 0x07);
        int blockExtra = (realLine & 0xc0) << 2;
        int namePosBase = layoutTableAddress + ((realLine >>> 3) << 5);
        int namePos = namePosBase + leftScrollCharsInPage;
        if (leftScroll2Pages && leftScrollChars < 32) namePos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) namePos = leftScroll2Pages && leftScrollChars >= 32 ? namePosBase & modeData.evenPageMask : namePosBase;
            int name = vram[namePos] | blockExtra; ++namePos;
            int colorCode = vram[((name << 3) + lineInColor) & colorTableAddressMask];
            int pattern = vram[((name << 3) + lineInPattern) & patternTableAddressMask];
            int on = colorPalette[colorCode >>> 4];
            int off = colorPalette[colorCode & 0xf];
            paintPattern8(bufferPos, pattern, on, off);
            bufferPos += 8;
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode2(realLine, bufferPos, colorPaletteReal);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeG4() {
        int realLine = getRealLine();
        paintBackdrop16(bufferPosition); paintBackdrop16(bufferPosition + 256);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels;
        int pixelsPosBase = layoutTableAddress + (realLine << 7);
        int pixelsPos = pixelsPosBase + (leftScrollCharsInPage << 2);
        if (leftScroll2Pages && leftScrollChars < 32) pixelsPos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) pixelsPos = leftScroll2Pages && leftScrollChars >= 32 ? pixelsPosBase & modeData.evenPageMask : pixelsPosBase;
            for (int b = 0; b < 4; b++) {
                int pixels = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                frameBackBuffer[bufferPos++] = colorPalette[pixels >>> 4];
                frameBackBuffer[bufferPos++] = colorPalette[pixels & 0x0f];
            }
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode2(realLine, bufferPos, colorPaletteReal);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeG5() {
        int realLine = getRealLine();
        paintBackdrop32Tiled(bufferPosition); paintBackdrop32Tiled(bufferPosition + 512);
        int bufferPos = bufferPosition + 16 + ((horizontalAdjust + rightScrollPixels) << 1);
        int pixelsPosBase = layoutTableAddress + (realLine << 7);
        int pixelsPos = pixelsPosBase + (leftScrollCharsInPage << 2);
        if (leftScroll2Pages && leftScrollChars < 32) pixelsPos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        if (color0Solid) {
            for (int c = 0; c < 32; ++c) {
                if (c == scrollCharJump) pixelsPos = leftScroll2Pages && leftScrollChars >= 32 ? pixelsPosBase & modeData.evenPageMask : pixelsPosBase;
                for (int b = 0; b < 4; b++) {
                    int pixels = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                    frameBackBuffer[bufferPos++] = colorPaletteSolid[pixels >>> 6];
                    frameBackBuffer[bufferPos++] = colorPaletteSolid[(pixels >>> 4) & 0x03];
                    frameBackBuffer[bufferPos++] = colorPaletteSolid[(pixels >>> 2) & 0x03];
                    frameBackBuffer[bufferPos++] = colorPaletteSolid[pixels & 0x03];
                }
            }
        } else {
            for (int c = 0; c < 32; ++c) {
                if (c == scrollCharJump) pixelsPos = leftScroll2Pages && leftScrollChars >= 32 ? pixelsPosBase & modeData.evenPageMask : pixelsPosBase;
                for (int b = 0; b < 4; b++) {
                    int pixels = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                    frameBackBuffer[bufferPos++] = (pixels & 0xc0) != 0 ? colorPaletteSolid[pixels >>> 6] : backdropTileOdd;
                    frameBackBuffer[bufferPos++] = (pixels & 0x30) != 0 ? colorPaletteSolid[(pixels >>> 4) & 0x03] : backdropTileEven;
                    frameBackBuffer[bufferPos++] = (pixels & 0x0c) != 0 ? colorPaletteSolid[(pixels >>> 2) & 0x03] : backdropTileOdd;
                    frameBackBuffer[bufferPos++] = (pixels & 0x03) != 0 ? colorPaletteSolid[pixels & 0x03] : backdropTileEven;
                }
            }
        }
        bufferPos -= (rightScrollPixels << 1) + 512;
        renderSpritesLineMode2Tiled(realLine, bufferPos);
        if (leftMask) paintBackdrop16Tiled(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop16Tiled(bufferPos + 512);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeG6() {
        int realLine = getRealLine();
        paintBackdrop32(bufferPosition); paintBackdrop32(bufferPosition + 512);
        int bufferPos = bufferPosition + 16 + ((horizontalAdjust + rightScrollPixels) << 1);
        int pixelsPosBase = layoutTableAddress + (realLine << 8);
        int pixelsPos = pixelsPosBase + (leftScrollCharsInPage << 3);
        if (leftScroll2Pages && leftScrollChars < 32) pixelsPos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) pixelsPos = leftScroll2Pages && leftScrollChars >= 32 ? pixelsPosBase & modeData.evenPageMask : pixelsPosBase;
            for (int b = 0; b < 8; b++) {
                int pixels = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                frameBackBuffer[bufferPos++] = colorPalette[pixels >>> 4];
                frameBackBuffer[bufferPos++] = colorPalette[pixels & 0x0f];
            }
        }
        bufferPos -= (rightScrollPixels << 1) + 512;
        renderSpritesLineMode2Stretched(realLine, bufferPos);
        if (leftMask) paintBackdrop16(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop16(bufferPos + 512);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeG7() {
        int realLine = getRealLine();
        paintBackdrop16(bufferPosition); paintBackdrop16(bufferPosition + 256);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels;
        int pixelsPosBase = layoutTableAddress + (realLine << 8);
        int pixelsPos = pixelsPosBase + (leftScrollCharsInPage << 3);
        if (leftScroll2Pages && leftScrollChars < 32) pixelsPos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) pixelsPos = leftScroll2Pages && leftScrollChars >= 32 ? pixelsPosBase & modeData.evenPageMask : pixelsPosBase;
            for (int b = 0; b < 8; b++) {
                frameBackBuffer[bufferPos++] = colors8bitValues[vram[pixelsPos & layoutTableAddressMask]]; ++pixelsPos;
            }
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode2(realLine, bufferPos, spritePaletteG7);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeYJK() {
        int realLine = getRealLine();
        int[] yjk = getColorsYJKValues();
        paintBackdrop20(bufferPosition); paintBackdrop16(bufferPosition + 256 + 4);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels + 4;
        int pixelsPosBase = layoutTableAddress + (realLine << 8);
        int pixelsPos = pixelsPosBase + (leftScrollCharsInPage << 3);
        if (leftScroll2Pages && leftScrollChars < 32) pixelsPos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) pixelsPos = leftScroll2Pages && leftScrollChars >= 32 ? pixelsPosBase & modeData.evenPageMask : pixelsPosBase;
            for (int g = 0; g < 2; g++) {
                int v1 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int v2 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int v3 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int v4 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int chroma = ((v4 & 0x07) << 9) | ((v3 & 0x07) << 6) | ((v2 & 0x07) << 3) | (v1 & 0x07);
                frameBackBuffer[bufferPos++] = yjk[((v1 & 0xf8) << 9) | chroma];
                frameBackBuffer[bufferPos++] = yjk[((v2 & 0xf8) << 9) | chroma];
                frameBackBuffer[bufferPos++] = yjk[((v3 & 0xf8) << 9) | chroma];
                frameBackBuffer[bufferPos++] = yjk[((v4 & 0xf8) << 9) | chroma];
            }
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode2(realLine, bufferPos, colorPaletteReal);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void renderLineModeYAE() {
        int realLine = getRealLine();
        int[] yjk = getColorsYJKValues();
        paintBackdrop20(bufferPosition); paintBackdrop16(bufferPosition + 256 + 4);
        int bufferPos = bufferPosition + 8 + horizontalAdjust + rightScrollPixels + 4;
        int pixelsPosBase = layoutTableAddress + (realLine << 8);
        int pixelsPos = pixelsPosBase + (leftScrollCharsInPage << 3);
        if (leftScroll2Pages && leftScrollChars < 32) pixelsPos &= modeData.evenPageMask;
        int scrollCharJump = leftScrollCharsInPage != 0 ? 32 - leftScrollCharsInPage : -1;
        for (int c = 0; c < 32; ++c) {
            if (c == scrollCharJump) pixelsPos = leftScroll2Pages && leftScrollChars >= 32 ? pixelsPosBase & modeData.evenPageMask : pixelsPosBase;
            for (int g = 0; g < 2; g++) {
                int v1 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int v2 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int v3 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int v4 = vram[pixelsPos & layoutTableAddressMask]; ++pixelsPos;
                int chroma = ((v4 & 0x07) << 9) | ((v3 & 0x07) << 6) | ((v2 & 0x07) << 3) | (v1 & 0x07);
                frameBackBuffer[bufferPos++] = (v1 & 0x8) != 0 ? colorPalette[v1 >> 4] : yjk[((v1 & 0xf8) << 9) | chroma];
                frameBackBuffer[bufferPos++] = (v2 & 0x8) != 0 ? colorPalette[v2 >> 4] : yjk[((v2 & 0xf8) << 9) | chroma];
                frameBackBuffer[bufferPos++] = (v3 & 0x8) != 0 ? colorPalette[v3 >> 4] : yjk[((v3 & 0xf8) << 9) | chroma];
                frameBackBuffer[bufferPos++] = (v4 & 0x8) != 0 ? colorPalette[v4 >> 4] : yjk[((v4 & 0xf8) << 9) | chroma];
            }
        }
        bufferPos -= rightScrollPixels + 256;
        renderSpritesLineMode2(realLine, bufferPos, colorPaletteReal);
        if (leftMask) paintBackdrop8(bufferPos);
        if (rightScrollPixels != 0) paintBackdrop8(bufferPos + 256);
        bufferPosition += bufferLineAdvance;
    }

    private void paintPattern6(int bufferPos, int pattern, int on, int off) {
        frameBackBuffer[bufferPos]   = (pattern & 0x80) != 0 ? on : off; frameBackBuffer[bufferPos+1] = (pattern & 0x40) != 0 ? on : off; frameBackBuffer[bufferPos+2] = (pattern & 0x20) != 0 ? on : off;
        frameBackBuffer[bufferPos+3] = (pattern & 0x10) != 0 ? on : off; frameBackBuffer[bufferPos+4] = (pattern & 0x08) != 0 ? on : off; frameBackBuffer[bufferPos+5] = (pattern & 0x04) != 0 ? on : off;
    }

    private void paintPattern8(int bufferPos, int pattern, int on, int off) {
        frameBackBuffer[bufferPos]   = (pattern & 0x80) != 0 ? on : off; frameBackBuffer[bufferPos+1] = (pattern & 0x40) != 0 ? on : off; frameBackBuffer[bufferPos+2] = (pattern & 0x20) != 0 ? on : off; frameBackBuffer[bufferPos+3] = (pattern & 0x10) != 0 ? on : off;
        frameBackBuffer[bufferPos+4] = (pattern & 0x08) != 0 ? on : off; frameBackBuffer[bufferPos+5] = (pattern & 0x04) != 0 ? on : off; frameBackBuffer[bufferPos+6] = (pattern & 0x02) != 0 ? on : off; frameBackBuffer[bufferPos+7] = (pattern & 0x01) != 0 ? on : off;
    }

    private void paintBackdrop8(int bufferPos)  { for (int i = 0; i < 8; i++) frameBackBuffer[bufferPos + i] = backdropValue; }
    private void paintBackdrop16(int bufferPos) { for (int i = 0; i < 16; i++) frameBackBuffer[bufferPos + i] = backdropValue; }
    private void paintBackdrop20(int bufferPos) { for (int i = 0; i < 20; i++) frameBackBuffer[bufferPos + i] = backdropValue; }
    private void paintBackdrop32(int bufferPos) { for (int i = 0; i < 32; i++) frameBackBuffer[bufferPos + i] = backdropValue; }

    private void paintBackdrop16Tiled(int bufferPos) {
        for (int i = 0; i < 16; i += 2) { frameBackBuffer[bufferPos + i] = backdropTileOdd; frameBackBuffer[bufferPos + i + 1] = backdropTileEven; }
    }
    private void paintBackdrop32Tiled(int bufferPos) {
        for (int i = 0; i < 32; i += 2) { frameBackBuffer[bufferPos + i] = backdropTileOdd; frameBackBuffer[bufferPos + i + 1] = backdropTileEven; }
    }

    private void renderSpritesLineMode1(int line, int bufferPos) {
        if (vram[spriteAttrTableAddress] == 208) return;
        int size = spritesSize << spritesMag;
        int atrPos, color, name, lineInPattern, pattern;
        int sprite = -1, drawn = 0, y, spriteLine, x, s, f;
        spritesGlobalPriority -= 32;
        atrPos = spriteAttrTableAddress - 4;
        for (int i = 0; i < 32; ++i) {
            atrPos = atrPos + 4;
            sprite = sprite + 1;
            y = vram[atrPos];
            if (y == 208) break;
            spriteLine = (line - y - 1) & 255;
            if (spriteLine >= size) continue;
            if (++drawn > 4) { if (spritesInvalid < 0 && F == 0) spritesInvalid = sprite; }
            color = vram[atrPos + 3];
            if ((color & 0xf) == 0 && !color0Solid) continue;
            x = vram[atrPos + 1];
            if ((color & 0x80) != 0) { x -= 32; if (x <= -size) continue; }
            color &= 0x0f;
            if (spritesSize == 16) {
                name = vram[atrPos + 2] & 0xfc;
                lineInPattern = spritePatternTableAddress + (name << 3) + (spriteLine >>> spritesMag);
                pattern = (vram[lineInPattern] << 8) | vram[lineInPattern + 16];
            } else {
                name = vram[atrPos + 2];
                pattern = vram[spritePatternTableAddress + (name << 3) + (spriteLine >>> spritesMag)];
            }
            s = x <= 256 - size ? 0 : x - (256 - size);
            f = x >= 0 ? size : size + x;
            x += (size - f);
            paintSpriteMode1(x, line, bufferPos + x, spritesGlobalPriority + sprite, pattern, color, s, f, spritesMag, drawn < 5);
        }
        if (spritesInvalid < 0 && sprite > spritesMaxComputed) spritesMaxComputed = sprite;
    }

    private void paintSpriteMode1(int x, int y, int bufferPos, long spritePri, int pattern, int color, int start, int finish, int magShift, boolean collide) {
        for (int i = finish - 1; i >= start; --i, ++x, ++bufferPos) {
            int s = (pattern >>> (i >>> magShift)) & 0x01;
            if (s == 0) continue;
            if (spritesLinePriorities[x] < spritePri) {
                if (collide && !spritesCollided) setSpritesCollision(x, y);
                if (color != 0 && spritesLineColors[x] == 0) {
                    spritesLineColors[x] = color;
                    frameBackBuffer[bufferPos] = colorPaletteReal[color];
                }
            } else {
                spritesLinePriorities[x] = spritePri;
                spritesLineColors[x] = color;
                if (color != 0) frameBackBuffer[bufferPos] = colorPaletteReal[color];
            }
        }
    }

    private void renderSpritesLineMode2(int line, int bufferPos, int[] palette) {
        if (!spritesEnabled || vram[spriteAttrTableAddress + 512] == 216) return;
        int size = spritesSize << spritesMag;
        int atrPos, colorPos, color, name, lineInPattern, pattern;
        int sprite = -1, drawn = 0, y, spriteLine, x, s, f, cc;
        long spritePri = SPRITE_MAX_PRIORITY;
        spritesGlobalPriority -= 32;
        atrPos = spriteAttrTableAddress + 512 - 4;
        colorPos = spriteAttrTableAddress - 16;
        for (int i = 0; i < 32; ++i) {
            sprite = sprite + 1;
            atrPos = atrPos + 4;
            colorPos = colorPos + 16;
            y = vram[atrPos];
            if (y == 216) break;
            spriteLine = (line - y - 1) & 255;
            if (spriteLine >= size) continue;
            if (++drawn > 8) { if (spritesInvalid < 0 && F == 0) spritesInvalid = sprite; }
            spriteLine >>>= spritesMag;
            color = vram[colorPos + spriteLine];
            cc = color & 0x40;
            if (cc != 0) { if (spritePri == SPRITE_MAX_PRIORITY) continue; }
            else spritePri = spritesGlobalPriority + sprite;
            if ((color & 0xf) == 0 && !color0Solid) continue;
            x = vram[atrPos + 1];
            if ((color & 0x80) != 0) { x -= 32; if (x <= -size) continue; }
            if (spritesSize == 16) {
                name = vram[atrPos + 2] & 0xfc;
                lineInPattern = spritePatternTableAddress + (name << 3) + spriteLine;
                pattern = (vram[lineInPattern] << 8) | vram[lineInPattern + 16];
            } else {
                name = vram[atrPos + 2];
                pattern = vram[spritePatternTableAddress + (name << 3) + spriteLine];
            }
            s = x <= 256 - size ? 0 : x - (256 - size);
            f = x >= 0 ? size : size + x;
            x += (size - f);
            if (cc != 0) paintSpriteMode2CC(x, bufferPos + x, spritePri, pattern, color & 0xf, palette, s, f, spritesMag);
            else paintSpriteMode2(x, line, bufferPos + x, spritePri, pattern, color & 0xf, palette, s, f, spritesMag, (color & 0x20) == 0 && drawn < 9);
        }
        if (spritesInvalid < 0 && sprite > spritesMaxComputed) spritesMaxComputed = sprite;
    }

    private void paintSpriteMode2(int x, int y, int bufferPos, long spritePri, int pattern, int color, int[] palette, int start, int finish, int magShift, boolean collide) {
        for (int i = finish - 1; i >= start; --i, ++x, ++bufferPos) {
            int s = (pattern >>> (i >>> magShift)) & 0x01;
            if (s == 0) continue;
            if (spritesLinePriorities[x] < spritePri) { if (collide && !spritesCollided) setSpritesCollision(x, y); continue; }
            spritesLinePriorities[x] = spritePri;
            spritesLineColors[x] = color;
            frameBackBuffer[bufferPos] = palette[color];
        }
    }

    private void paintSpriteMode2CC(int x, int bufferPos, long spritePri, int pattern, int color, int[] palette, int start, int finish, int magShift) {
        int finalColor;
        for (int i = finish - 1; i >= start; --i, ++x, ++bufferPos) {
            int s = (pattern >>> (i >>> magShift)) & 0x01;
            if (s == 0) continue;
            long prevSpritePri = spritesLinePriorities[x];
            if (prevSpritePri < spritePri) continue;
            if (prevSpritePri == spritePri) finalColor = color | spritesLineColors[x];
            else { spritesLinePriorities[x] = spritePri; finalColor = color; }
            spritesLineColors[x] = finalColor;
            frameBackBuffer[bufferPos] = palette[finalColor];
        }
    }

    private void renderSpritesLineMode2Tiled(int line, int bufferPos) {
        if (!spritesEnabled || vram[spriteAttrTableAddress + 512] == 216) return;
        int size = spritesSize << spritesMag;
        int atrPos, colorPos, color, name, lineInPattern, pattern;
        int sprite = -1, drawn = 0, y, spriteLine, x, s, f, cc;
        long spritePri = SPRITE_MAX_PRIORITY;
        spritesGlobalPriority -= 32;
        atrPos = spriteAttrTableAddress + 512 - 4;
        colorPos = spriteAttrTableAddress - 16;
        for (int i = 0; i < 32; ++i) {
            sprite = sprite + 1;
            atrPos = atrPos + 4;
            colorPos = colorPos + 16;
            y = vram[atrPos];
            if (y == 216) break;
            spriteLine = (line - y - 1) & 255;
            if (spriteLine >= size) continue;
            if (++drawn > 8) { if (spritesInvalid < 0 && F == 0) spritesInvalid = sprite; }
            spriteLine >>>= spritesMag;
            color = vram[colorPos + spriteLine];
            cc = color & 0x40;
            if (cc != 0) { if (spritePri == SPRITE_MAX_PRIORITY) continue; }
            else spritePri = spritesGlobalPriority + sprite;
            if ((color & 0xf) == 0 && !color0Solid) continue;
            x = vram[atrPos + 1];
            if ((color & 0x80) != 0) { x -= 32; if (x <= -size) continue; }
            if (spritesSize == 16) {
                name = vram[atrPos + 2] & 0xfc;
                lineInPattern = spritePatternTableAddress + (name << 3) + spriteLine;
                pattern = (vram[lineInPattern] << 8) | vram[lineInPattern + 16];
            } else {
                name = vram[atrPos + 2];
                pattern = vram[spritePatternTableAddress + (name << 3) + spriteLine];
            }
            s = x <= 256 - size ? 0 : x - (256 - size);
            f = x >= 0 ? size : size + x;
            x += (size - f);
            if (cc != 0) paintSpriteMode2TiledCC(x, bufferPos + (x << 1), spritePri, pattern, color & 0xf, s, f, spritesMag);
            else paintSpriteMode2Tiled(x, line, bufferPos + (x << 1), spritePri, pattern, color & 0xf, s, f, spritesMag, (color & 0x20) == 0 && drawn < 9);
        }
        if (spritesInvalid < 0 && sprite > spritesMaxComputed) spritesMaxComputed = sprite;
    }

    private void paintSpriteMode2Tiled(int x, int y, int bufferPos, long spritePri, int pattern, int color, int start, int finish, int magShift, boolean collide) {
        for (int i = finish - 1; i >= start; --i, ++x, bufferPos += 2) {
            int s = (pattern >>> (i >>> magShift)) & 0x01;
            if (s == 0) continue;
            if (spritesLinePriorities[x] < spritePri) { if (collide && !spritesCollided) setSpritesCollision(x, y); continue; }
            spritesLinePriorities[x] = spritePri;
            spritesLineColors[x] = color;
            frameBackBuffer[bufferPos] = colorPaletteReal[color >>> 2];
            frameBackBuffer[bufferPos + 1] = colorPaletteReal[color & 0x03];
        }
    }

    private void paintSpriteMode2TiledCC(int x, int bufferPos, long spritePri, int pattern, int color, int start, int finish, int magShift) {
        int finalColor;
        for (int i = finish - 1; i >= start; --i, ++x, bufferPos += 2) {
            int s = (pattern >>> (i >>> magShift)) & 0x01;
            if (s == 0) continue;
            long prevSpritePri = spritesLinePriorities[x];
            if (prevSpritePri < spritePri) continue;
            if (prevSpritePri == spritePri) finalColor = color | spritesLineColors[x];
            else { spritesLinePriorities[x] = spritePri; finalColor = color; }
            spritesLineColors[x] = finalColor;
            frameBackBuffer[bufferPos] = colorPaletteReal[finalColor >>> 2];
            frameBackBuffer[bufferPos + 1] = colorPaletteReal[finalColor & 0x03];
        }
    }

    private void renderSpritesLineMode2Stretched(int line, int bufferPos) {
        if (!spritesEnabled || vram[spriteAttrTableAddress + 512] == 216) return;
        int size = spritesSize << spritesMag;
        int atrPos, colorPos, color, name, lineInPattern, pattern;
        int sprite = -1, drawn = 0, y, spriteLine, x, s, f, cc;
        long spritePri = SPRITE_MAX_PRIORITY;
        spritesGlobalPriority -= 32;
        atrPos = spriteAttrTableAddress + 512 - 4;
        colorPos = spriteAttrTableAddress - 16;
        for (int i = 0; i < 32; ++i) {
            sprite = sprite + 1;
            atrPos = atrPos + 4;
            colorPos = colorPos + 16;
            y = vram[atrPos];
            if (y == 216) break;
            spriteLine = (line - y - 1) & 255;
            if (spriteLine >= size) continue;
            if (++drawn > 8) { if (spritesInvalid < 0 && F == 0) spritesInvalid = sprite; }
            spriteLine >>>= spritesMag;
            color = vram[colorPos + spriteLine];
            cc = color & 0x40;
            if (cc != 0) { if (spritePri == SPRITE_MAX_PRIORITY) continue; }
            else spritePri = spritesGlobalPriority + sprite;
            if ((color & 0xf) == 0 && !color0Solid) continue;
            x = vram[atrPos + 1];
            if ((color & 0x80) != 0) { x -= 32; if (x <= -size) continue; }
            if (spritesSize == 16) {
                name = vram[atrPos + 2] & 0xfc;
                lineInPattern = spritePatternTableAddress + (name << 3) + spriteLine;
                pattern = (vram[lineInPattern] << 8) | vram[lineInPattern + 16];
            } else {
                name = vram[atrPos + 2];
                pattern = vram[spritePatternTableAddress + (name << 3) + spriteLine];
            }
            s = x <= 256 - size ? 0 : x - (256 - size);
            f = x >= 0 ? size : size + x;
            x += (size - f);
            if (cc != 0) paintSpriteMode2StretchedCC(x, bufferPos + (x << 1), spritePri, pattern, color & 0xf, s, f, spritesMag);
            else paintSpriteMode2Stretched(x, line, bufferPos + (x << 1), spritePri, pattern, color & 0xf, s, f, spritesMag, (color & 0x20) == 0 && drawn < 9);
        }
        if (spritesInvalid < 0 && sprite > spritesMaxComputed) spritesMaxComputed = sprite;
    }

    private void paintSpriteMode2Stretched(int x, int y, int bufferPos, long spritePri, int pattern, int color, int start, int finish, int magShift, boolean collide) {
        for (int i = finish - 1; i >= start; --i, ++x, bufferPos += 2) {
            int s = (pattern >>> (i >>> magShift)) & 0x01;
            if (s == 0) continue;
            if (spritesLinePriorities[x] < spritePri) { if (collide && !spritesCollided) setSpritesCollision(x, y); continue; }
            spritesLinePriorities[x] = spritePri;
            spritesLineColors[x] = color;
            frameBackBuffer[bufferPos] = frameBackBuffer[bufferPos + 1] = colorPaletteReal[color];
        }
    }

    private void paintSpriteMode2StretchedCC(int x, int bufferPos, long spritePri, int pattern, int color, int start, int finish, int magShift) {
        int finalColor;
        for (int i = finish - 1; i >= start; --i, ++x, bufferPos += 2) {
            int s = (pattern >>> (i >>> magShift)) & 0x01;
            if (s == 0) continue;
            long prevSpritePri = spritesLinePriorities[x];
            if (prevSpritePri < spritePri) continue;
            if (prevSpritePri == spritePri) finalColor = color | spritesLineColors[x];
            else { spritesLinePriorities[x] = spritePri; finalColor = color; }
            spritesLineColors[x] = finalColor;
            frameBackBuffer[bufferPos] = frameBackBuffer[bufferPos + 1] = colorPaletteReal[finalColor];
        }
    }

    private void setSpritesCollision(int x, int y) {
        spritesCollided = true;
        if (spritesCollisionX >= 0) return;
        spritesCollisionX = x + 12; spritesCollisionY = y + 8;
        if ((register[8] & 0xc0) == 0) {
            status[3] = spritesCollisionX & 255;
            status[4] = 0xfe | (spritesCollisionX >>> 8);
            status[5] = spritesCollisionY & 255;
            status[6] = 0xfc | (spritesCollisionY >>> 8);
        }
    }

    private void beginFrame() {
        currentScanline = 0;
        frameStartingActiveScanline = startingActiveScanline;
        EO ^= 1;
        bufferPosition = 0;
        bufferLineAdvance = LINE_WIDTH;
        updateLayoutTableAddressMask();
    }

    private void finishFrame() {
        present();
        ++frame;
        beginFrame();
    }

    private void present() {
        System.arraycopy(frameBackBuffer, 0, frontBuffer, 0, frameBackBuffer.length);
        frontWidth = renderWidth;
        frontHeight = renderHeight;
    }

    private void initRegisters() {
        java.util.Arrays.fill(register, 0);
        java.util.Arrays.fill(status, 0);
        register[9] = 0;
        status[0] = 0; F = 0;
        status[1] = isV9958 ? 0x04 : 0x00; FH = 0;
        status[2] = 0x0c; VR = 0; HR = 0; EO = 0;
        status[4] = 0xfe;
        status[6] = 0xfc;
        status[9] = 0xfe;
    }

    private void initColorPalette() {
        for (int c = 0; c < 16; ++c) {
            paletteRegister[c] = paletteRegisterInitialValuesV9938[c];
            int value = getColorValueForPaletteValue(paletteRegister[c]);
            colorPaletteReal[c] = value;
            colorPalette[c] = value;
            colorPaletteSolid[c] = value;
        }
        updateBackdropValue();
    }

    private void initModes() {
        ModeData nul = new ModeData("NUL", true, 0, 0, 0, 0, 256, 0, ~0, ~0, R_NUL, 0, 0, false, null, true, 0);
        for (int i = 0; i < modes.length; i++) modes[i] = nul;
        modes[0x10] = new ModeData("T1", false, -1 << 10, 0, -1 << 11, 0, 256, 0, ~(1 << 15), ~0, R_T1, 0, 0, false, false, true, 40);
        modes[0x12] = new ModeData("T2", true, -1 << 12, -1 << 9, -1 << 11, 0, 512, 0, ~(1 << 15), ~0, R_T2, 0, 0, false, false, true, 80);
        modes[0x08] = new ModeData("MC", false, -1 << 10, 0, -1 << 11, -1 << 7, 256, 0, ~(1 << 15), ~0, R_MC, 0, 1, false, false, true, 0);
        modes[0x00] = new ModeData("G1", false, -1 << 10, -1 << 6, -1 << 11, -1 << 7, 256, 0, ~(1 << 15), ~0, R_G1, 0, 1, false, false, true, 32);
        modes[0x01] = new ModeData("G2", false, -1 << 10, -1 << 13, -1 << 13, -1 << 7, 256, 0, ~(1 << 15), ~0, R_G2, 0, 1, false, false, true, 0);
        modes[0x02] = new ModeData("G3", true, -1 << 10, -1 << 13, -1 << 13, -1 << 10, 256, 0, ~(1 << 15), ~(1 << 15), R_G3, 0, 2, false, false, true, 0);
        modes[0x03] = new ModeData("G4", true, -1 << 15, 0, 0, -1 << 10, 256, 128, ~(1 << 15), ~(1 << 15), R_G4, 2, 2, false, false, true, 0);
        modes[0x04] = new ModeData("G5", true, -1 << 15, 0, 0, -1 << 10, 512, 128, ~(1 << 15), ~(1 << 15), R_G5, 4, 2, true, false, true, 0);
        modes[0x05] = new ModeData("G6", true, -1 << 16, 0, 0, -1 << 10, 512, 256, ~(1 << 16), ~(1 << 16), R_G6, 2, 2, false, true, true, 0);
        modes[0x07] = new ModeData("G7", true, -1 << 16, 0, 0, -1 << 10, 256, 256, ~(1 << 16), ~(1 << 16), R_G7, 1, 2, false, true, false, 0);
        modes[0x21] = new ModeData("YJK", true, -1 << 16, 0, 0, -1 << 10, 256, 256, ~(1 << 16), ~(1 << 16), R_YJK, 1, 2, false, true, true, 0);
        modes[0x23] = new ModeData("YAE", true, -1 << 16, 0, 0, -1 << 10, 256, 256, ~(1 << 16), ~(1 << 16), R_YAE, 1, 2, false, true, true, 0);
    }
}
