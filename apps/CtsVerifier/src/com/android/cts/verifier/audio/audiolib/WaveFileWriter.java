/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.verifier.audio.audiolib;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;


/**
 * Write audio data to a WAV file.
 *
 * <pre>
 * <code>
 * WaveFileWriter writer = new WaveFileWriter(file);
 * writer.setFrameRate(22050);
 * writer.setBitsPerSample(24);
 * writer.write(floatArray);
 * writer.close();
 * </code>
 * </pre>
 *
 * This was borrowed with Phil Burk's permission from JSyn at:
 * https://github.com/philburk/jsyn/blob/master/src/main/java/com/jsyn/util/WaveFileWriter.java
 */
public class WaveFileWriter {

    private static final short WAVE_FORMAT_PCM = 1;
    private static final int PCM24_MIN = -(1 << 23);
    private static final int PCM24_MAX = (1 << 23) - 1;

    private final OutputStream mOutputStream;
    private long mRiffSizePosition = 0;
    private long mDataSizePosition = 0;
    private int mFrameRate = 44100;
    private int mSamplesPerFrame = 1;
    private int mBitsPerSample = 16;
    private int mBytesWritten;
    private final File mOutputFile;
    private boolean mHeaderWritten = false;

    /**
     * Create a writer that will write to the specified file.
     */
    public WaveFileWriter(File outputFile) throws FileNotFoundException {
        mOutputFile = outputFile;
        FileOutputStream fileOut = new FileOutputStream(outputFile);
        mOutputStream = new BufferedOutputStream(fileOut);
    }

    /**
     * @param frameRate default is 44100
     */
    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public int getFrameRate() {
        return mFrameRate;
    }

    /**
     * For stereo, set this to 2. Default is 1.
     */
    public void setSamplesPerFrame(int samplesPerFrame) {
        mSamplesPerFrame = samplesPerFrame;
    }

    public int getSamplesPerFrame() {
        return mSamplesPerFrame;
    }

    /**
     * Only 16 or 24 bit samples supported at the moment. Default is 16.
     */
    public void setBitsPerSample(int bits) {
        if ((bits != 16) && (bits != 24)) {
            throw new IllegalArgumentException(
                    "Only 16 or 24 bits per sample allowed. Not " + bits);
        }
        mBitsPerSample = bits;
    }

    public int getBitsPerSample() {
        return mBitsPerSample;
    }

    /**
     * Close the stream and fix the chunk sizes.
     * @throws IOException if the close fails
     */
    public void close() throws IOException {
        mOutputStream.close();
        fixSizes();
    }

    /**
     * Write entire buffer of audio samples to the WAV file.
     */
    public void write(float[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    /**
     * Write single audio data value to the WAV file.
     */
    public void write(float value) throws IOException {
        if (!mHeaderWritten) {
            writeHeader();
        }

        if (mBitsPerSample == 24) {
            writePCM24(value);
        } else {
            writePCM16(value);
        }
    }

    private void writePCM24(float value) throws IOException {
        // Offset before casting so that we can avoid using floor().
        // Also round by adding 0.5 so that very small signals go to zero.
        float temp = (PCM24_MAX * value) + 0.5f - PCM24_MIN;
        int sample = ((int) temp) + PCM24_MIN;
        // clip to 24-bit range
        if (sample > PCM24_MAX) {
            sample = PCM24_MAX;
        } else if (sample < PCM24_MIN) {
            sample = PCM24_MIN;
        }
        // encode as little-endian
        writeByte(sample); // little end
        writeByte(sample >> 8); // middle
        writeByte(sample >> 16); // big end
    }

    private void writePCM16(float value) throws IOException {
        // Offset before casting so that we can avoid using floor().
        // Also round by adding 0.5 so that very small signals go to zero.
        float temp = (Short.MAX_VALUE * value) + 0.5f - Short.MIN_VALUE;
        int sample = ((int) temp) + Short.MIN_VALUE;
        if (sample > Short.MAX_VALUE) {
            sample = Short.MAX_VALUE;
        } else if (sample < Short.MIN_VALUE) {
            sample = Short.MIN_VALUE;
        }
        writeByte(sample); // little end
        writeByte(sample >> 8); // big end
    }

    /**
     * Write audio to the WAV file.
     */
    public void write(float[] buffer, int start, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            write(buffer[start + i]);
        }
    }

    // Write lower 8 bits. Upper bits ignored.
    private void writeByte(int b) throws IOException {
        mOutputStream.write(b);
        mBytesWritten += 1;
    }

    /**
     * Write a 32 bit integer to the stream in Little Endian format.
     */
    public void writeIntLittle(int n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
        writeByte(n >> 16);
        writeByte(n >> 24);
    }

    /**
     * Write a 16 bit integer to the stream in Little Endian format.
     */
    public void writeShortLittle(short n) throws IOException {
        writeByte(n);
        writeByte(n >> 8);
    }

    /**
     * Write a simple WAV header for PCM data.
     */
    private void writeHeader() throws IOException {
        writeRiffHeader();
        writeFormatChunk();
        writeDataChunkHeader();
        mOutputStream.flush();
        mHeaderWritten = true;
    }

    /**
     * Write a 'RIFF' file header and a 'WAVE' ID to the WAV file.
     */
    private void writeRiffHeader() throws IOException {
        writeByte('R');
        writeByte('I');
        writeByte('F');
        writeByte('F');
        mRiffSizePosition = mBytesWritten;
        // This will be overwritten by fixSizes() when the writer is closed.
        writeIntLittle(Integer.MAX_VALUE);
        writeByte('W');
        writeByte('A');
        writeByte('V');
        writeByte('E');
    }

    /**
     * Write an 'fmt ' chunk to the WAV file containing the given information.
     */
    public void writeFormatChunk() throws IOException {
        int bytesPerSample = (mBitsPerSample + 7) / 8;

        writeByte('f');
        writeByte('m');
        writeByte('t');
        writeByte(' ');
        writeIntLittle(16); // chunk size
        writeShortLittle(WAVE_FORMAT_PCM);
        writeShortLittle((short) mSamplesPerFrame);
        writeIntLittle(mFrameRate);
        // bytes/second
        writeIntLittle(mFrameRate * mSamplesPerFrame * bytesPerSample);
        // block align
        writeShortLittle((short) (mSamplesPerFrame * bytesPerSample));
        writeShortLittle((short) mBitsPerSample);
    }

    /**
     * Write a 'data' chunk header to the WAV file. This should be followed by call to
     * writeShortLittle() to write the data to the chunk.
     */
    public void writeDataChunkHeader() throws IOException {
        writeByte('d');
        writeByte('a');
        writeByte('t');
        writeByte('a');
        mDataSizePosition = mBytesWritten;
        // This will be overwritten by fixSizes() when the writer is closed.
        writeIntLittle(Integer.MAX_VALUE); // size
    }

    /**
     * Fix RIFF and data chunk sizes based on final size. Assume data chunk is the last chunk.
     */
    private void fixSizes() throws IOException {
        RandomAccessFile randomFile = new RandomAccessFile(mOutputFile, "rw");
        try {
            // adjust RIFF size
            long end = mBytesWritten;
            int riffSize = (int) (end - mRiffSizePosition) - 4;
            randomFile.seek(mRiffSizePosition);
            writeRandomIntLittle(randomFile, riffSize);
            // adjust data size
            int dataSize = (int) (end - mDataSizePosition) - 4;
            randomFile.seek(mDataSizePosition);
            writeRandomIntLittle(randomFile, dataSize);
        } finally {
            randomFile.close();
        }
    }

    private void writeRandomIntLittle(RandomAccessFile randomFile, int n) throws IOException {
        byte[] buffer = new byte[4];
        buffer[0] = (byte) n;
        buffer[1] = (byte) (n >> 8);
        buffer[2] = (byte) (n >> 16);
        buffer[3] = (byte) (n >> 24);
        randomFile.write(buffer);
    }

}
