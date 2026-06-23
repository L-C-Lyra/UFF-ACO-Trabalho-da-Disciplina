package dev.flaffy.horsepoweremu.emu.audio;



public interface AudioChip {

    void reset();

    void writeAddr0(int val);   // $FF08
    void writeData0(int val);   // $FF09 (W)
    void writeAddr1(int val);   // $FF0A
    void writeData1(int val);  // $FF0B (W)

    int readStatus(); // $FF09 (R)
    int readData(); // $FF0B (R)


    boolean isIRQ();

    int sampleRate();
    int generate(short[] out, int frames);

    default void close() {}
}
