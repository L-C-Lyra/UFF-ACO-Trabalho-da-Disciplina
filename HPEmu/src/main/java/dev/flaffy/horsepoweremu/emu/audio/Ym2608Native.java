package dev.flaffy.horsepoweremu.emu.audio;

import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

//backend de audio, usando lang foreing para interagir com YMFM
public final class Ym2608Native implements AudioChip {

    public static final int CLOCK_HZ = 7_987_200;

    public static final int OUTPUT_RATE = 44_100;

    private static final int MAX_FRAMES = 8192;

    private final Arena arena;
    private final MemorySegment chip;
    private final MemorySegment buffer;
    private final int sampleRate;

    private final MethodHandle hReset, hWrite, hRead, hIrq, hGenerate, hDestroy;

    private boolean closed = false;

    public Ym2608Native() {
        this.arena = Arena.ofShared();

        Path lib = extractLibrary(arena);
        Linker linker = Linker.nativeLinker();
        SymbolLookup look = SymbolLookup.libraryLookup(lib, arena);

        MethodHandle hCreate = linker.downcallHandle(
                look.find("hpym_create").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT));
        MethodHandle hSampleRate = linker.downcallHandle(
                look.find("hpym_sample_rate").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
        hReset = linker.downcallHandle(look.find("hpym_reset").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS));
        hWrite = linker.downcallHandle(look.find("hpym_write").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT));
        hRead = linker.downcallHandle(look.find("hpym_read").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        hIrq = linker.downcallHandle(look.find("hpym_irq").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
        hGenerate = linker.downcallHandle(look.find("hpym_generate").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));
        hDestroy = linker.downcallHandle(look.find("hpym_destroy").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS));

        try {
            this.chip = (MemorySegment) hCreate.invokeExact(CLOCK_HZ, OUTPUT_RATE);
            if (chip.address() == 0L) throw new IllegalStateException("hpym_create retornou null");
            this.sampleRate = (int) hSampleRate.invokeExact(chip);
        } catch (Throwable t) {
            throw new RuntimeException("YM2608 native init falhou", t);
        }

        this.buffer = arena.allocate((long) MAX_FRAMES * 2 * Short.BYTES);
    }

    private static Path extractLibrary(Arena arena) {
        String name = libResourceName();
        try (InputStream in = Ym2608Native.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("native lib nao esta na classpath: " + name);
            Path tmp = Files.createTempFile("hpym2608", suffix());
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (Exception e) {
            throw new RuntimeException("falhou extrair " + name, e);
        }
    }

    private static String libResourceName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String archDir = (arch.contains("aarch64") || arch.contains("arm64")) ? "aarch64" : "x86-64";
        if (os.contains("linux")) return "/native/linux-" + archDir + "/libhpym2608.so";
        if (os.contains("mac")) return "/native/macos-" + archDir + "/libhpym2608.dylib";
        if (os.contains("win")) return "/native/windows-" + archDir + "/hpym2608.dll";
        throw new IllegalStateException("Plataforma desconhecida ou não suportada: " + os + "/" + arch);
    }

    private static String suffix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) return ".dylib";
        if (os.contains("win")) return ".dll";
        return ".so";
    }

    @Override public void reset() {
        try { hReset.invokeExact(chip); } catch (Throwable t) { throw rethrow(t); }
    }

    @Override public void writeAddr0(int v) { write(0, v); }
    @Override public void writeData0(int v) { write(1, v); }
    @Override public void writeAddr1(int v) { write(2, v); }
    @Override public void writeData1(int v) { write(3, v); }

    private void write(int off, int data) {
        try { hWrite.invokeExact(chip, off, data & 0xff); } catch (Throwable t) { throw rethrow(t); }
    }

    @Override public int readStatus() {   // bank 0, read_status
        try { return (int) hRead.invokeExact(chip, 0) & 0xff; } catch (Throwable t) { throw rethrow(t); }
    }
    @Override public int readData() {      // bank 1, read_data_hi (ADPCM/data)
        try { return (int) hRead.invokeExact(chip, 3) & 0xff; } catch (Throwable t) { throw rethrow(t); }
    }

    @Override public boolean isIRQ() {
        try { return ((int) hIrq.invokeExact(chip)) != 0; } catch (Throwable t) { throw rethrow(t); }
    }

    @Override public int sampleRate() { return sampleRate; }

    @Override public int generate(short[] out, int frames) {
        if (closed) return 0;
        int n = Math.min(frames, MAX_FRAMES);
        try {
            hGenerate.invokeExact(chip, buffer, n);
        } catch (Throwable t) { throw rethrow(t); }
        MemorySegment.copy(buffer, JAVA_SHORT, 0L, out, 0, n * 2);
        return n;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { hDestroy.invokeExact(chip); } catch (Throwable ignored) { }
        arena.close();
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        return new RuntimeException(t);
    }
}
