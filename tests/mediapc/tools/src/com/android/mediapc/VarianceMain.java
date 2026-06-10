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

package com.android.mediapc;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This application reads an input yuv frame by frame and computes variance of all luma blocks
 * (block size is 16x16) and prints their average to an output file. The yuv file is expected to
 * be 8 bit or 10 bit with chroma sub sampling format being 2x2. As only luma blocks are
 * considered for variance, the yuv can be 420 planar or 420 semi-planar. For 10 bit yuv, the
 * input is expected to be in p010 format that is 10 bit sample data is stored in 2 bytes after
 * left shifting by 6. The LSB 6 bits contains zero and bits from 6 - 16 contain the actual sample.
 */
public class VarianceMain {
    public static double computeFrameVariance(int width, int height, Object luma) {
        final int bSize = 16;
        double varianceSum = 0;
        int blocks = 0;
        for (int i = 0; i < height - bSize; i += bSize) {
            for (int j = 0; j < width - bSize; j += bSize) {
                double sse = 0, sum = 0;
                int offset = i * width + j;
                for (int p = 0; p < bSize; p++) {
                    for (int q = 0; q < bSize; q++) {
                        int sample;
                        if (luma instanceof byte[]) {
                            sample = Byte.toUnsignedInt(((byte[]) luma)[offset + p * width + q]);
                        } else if (luma instanceof short[]) {
                            sample = Short.toUnsignedInt(((short[]) luma)[offset + p * width + q]);
                            sample >>= 6;
                        } else {
                            throw new IllegalArgumentException("Unsupported data type");
                        }
                        sum += sample;
                        sse += sample * sample;
                    }
                }
                double meanOfSquares = sse / (bSize * bSize);
                double mean = sum / (bSize * bSize);
                double squareOfMean = mean * mean;
                double blockVariance = (meanOfSquares - squareOfMean);
                varianceSum += blockVariance;
                blocks++;
            }
        }
        return (varianceSum / blocks);
    }

    public static short[] extractLuma(byte[] inputData) {
        int length = inputData.length / 2;
        short[] p010Data = new short[length];
        ByteBuffer.wrap(inputData)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .get(p010Data);
        return p010Data;
    }

    @Parameters(separators = " =")
    public static class Options {
        @Parameter(names = "-f", description = "input yuv file path", required = true)
        public String filePath;
        @Parameter(names = "-o", description = "output txt file path")
        public String outputFilename = "result.txt";
        @Parameter(names = "-w", description = "input width", validateWith = SizeValidator.class,
                required = true)
        public int width;
        @Parameter(names = "-h", description = "input height", validateWith = SizeValidator.class,
                required = true)
        public int height;
        @Parameter(names = "-n", description = "number of frames to process")
        public int numFrames = Integer.MAX_VALUE;
        @Parameter(names = "-b", description = "input bit-depth", validateWith =
                BitDepthValidator.class, required = true)
        public int bitDepth;
        @Parameter(names = "--help", help = true)
        public boolean help = false;
    }

    public static class BitDepthValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            int n = Integer.parseInt(value);
            if (n != 8 && n != 10) {
                throw new ParameterException(
                        "Parameter " + name + " should be 8 or 10 (found " + value + ")");
            }
        }
    }

    public static class SizeValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            int n = Integer.parseInt(value);
            if (n < 0) {
                throw new ParameterException(
                        "Parameter " + name + " should be positive (found " + value + ")");
            } else if ((n & 1) != 0) {
                throw new ParameterException(
                        "Parameter " + name + " should be even (found " + value + ")");
            }
        }
    }

    public static void main(String[] args) {
        Options options = new Options();
        JCommander jc = new JCommander(options);
        try {
            jc.parse(args);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            jc.usage();
            System.exit(0);
        }
        if (options.help) {
            jc.usage();
            System.exit(0);
        }
        int bpp = options.bitDepth == 8 ? 1 : 2;
        int ySize = options.width * options.height * bpp;
        int uSize = (options.width / 2) * (options.height / 2) * bpp;
        byte[] lumadata = new byte[ySize];
        try (FileInputStream fileInputStream = new FileInputStream(options.filePath);
             FileWriter writer = new FileWriter(options.outputFilename)) {
            for (int i = 0; i < options.numFrames; i++) {
                int bytesRead = fileInputStream.read(lumadata);
                if (bytesRead == -1) break;
                if (bytesRead != ySize) {
                    throw new IOException("Unexpected end of file or incorrect file format");
                }
                double variance;
                if (bpp == 2) {
                    variance = computeFrameVariance(options.width, options.height,
                            extractLuma(lumadata));
                } else {
                    variance = computeFrameVariance(options.width, options.height, lumadata);
                }
                writer.write(i + " " + variance + "\n");
                long skipped = fileInputStream.skip(uSize + uSize);
                if (skipped != uSize + uSize) {
                    throw new IOException("Unexpected end of file, expecting chroma samples");
                }
            }
        } catch (IOException e) {
            System.out.println("Error opening file: " + e.getMessage());
        }
    }
}
