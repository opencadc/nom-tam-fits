package nom.tam.image.comp.plio;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/*
 * #%L
 * nom.tam FITS library
 * %%
 * Copyright (C) 1996 - 2015 nom-tam-fits
 * %%
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * #L%
 */

public class PLIOCompress {

    private static final int FIRST_VALUE_WITH_13_BIT = 4096;

    private static final int FIRST_VALUE_WITH_14_BIT = 8192;

    private static final int FIRST_VALUE_WITH_15_BIT = 16384;

    private static final int FIRST_VALUE_WITH_16_BIT = 32768;

    private static final int HEADER_SIZE_FIELD1 = 3;

    private static final int HEADER_SIZE_FIELD2 = 4;

    private static final int LAST_VALUE_FITTING_IN_12_BIT = FIRST_VALUE_WITH_13_BIT - 1;

    private static final int MINI_HEADER_SIZE = 3;

    private static final int MINI_HEADER_SIZE_FIELD = 2;

    /**
     * The exact meening of this var is not clear at the moment of porting the
     * algorithm to java.
     */
    private static final int N20481 = 20481;

    private static final int OPCODE_1 = 1;

    private static final int OPCODE_2 = 2;

    private static final int OPCODE_3 = 3;

    private static final int OPCODE_4 = 4;

    private static final int OPCODE_5 = 5;

    private static final int OPCODE_6 = 6;

    private static final int OPCODE_7 = 7;

    private static final int OPCODE_8 = 8;

    private static final short[] PLIO_HEADER = {
        (short) 0,
        (short) 7,
        (short) -100,
        (short) 0,
        (short) 0,
        (short) 0,
        (short) 0
    };

    private static final int SHIFT_12_BITS = 12;

    private static final int SHIFT_15_BITS = 15;

    private static final int VALUE_OF_BIT_13_AND14_ON = 12288;

    /**
     * PL_P2L -- Convert a pixel array to a line list. The length of the list is
     * returned as the function value.
     * 
     * @param pixelData
     *            input pixel array
     * @param compressedData
     *            encoded line list
     * @param npix
     *            number of pixels to convert
     */
    public void compress(IntBuffer pixelData, ShortBuffer compressedData, int npix) {
        compressedData.position(0);
        compressedData.put(PLIO_HEADER);
        final int xe = npix - 1;
        int op = PLIO_HEADER.length;
        /* Computing MAX */
        int pv = Math.max(0, pixelData.get(0));
        int x1 = 0;
        int iz = 0;
        int hi = 1;
        int nv = 0;
        for (int ip = 0; ip <= xe; ++ip) {
            if (ip < xe) {
                /* Computing MAX */
                nv = Math.max(0, pixelData.get(ip + 1));
                if (nv == pv) {
                    continue;
                }
                if (pv == 0) {
                    pv = nv;
                    x1 = ip + 1;
                    continue;
                }
            } else {
                if (pv == 0) {
                    x1 = xe + 1;
                }
            }

            int np = ip - x1 + 1;
            int nz = x1 - iz;
            boolean skip = false;
            if (pv > 0) {
                int dv = pv - hi;
                if (dv != 0) {
                    hi = pv;
                    if (Math.abs(dv) > LAST_VALUE_FITTING_IN_12_BIT) {
                        compressedData.put(op, (short) ((pv & LAST_VALUE_FITTING_IN_12_BIT) + FIRST_VALUE_WITH_13_BIT));
                        ++op;
                        compressedData.put(op, (short) (pv / FIRST_VALUE_WITH_13_BIT));
                        ++op;
                    } else {
                        if (dv < 0) {
                            compressedData.put(op, (short) (-dv + VALUE_OF_BIT_13_AND14_ON));
                        } else {
                            compressedData.put(op, (short) (dv + FIRST_VALUE_WITH_14_BIT));
                        }
                        ++op;
                        if (np == 1 && nz == 0) {
                            int v = compressedData.get(op - 1);
                            compressedData.put(op - 1, (short) (v | FIRST_VALUE_WITH_15_BIT));
                            skip = true;
                        }
                    }
                }
            }
            if (!skip) {
                if (nz > 0) {
                    while (nz > 0) {
                        compressedData.put(op, (short) Math.min(LAST_VALUE_FITTING_IN_12_BIT, nz));
                        ++op;
                        nz += -LAST_VALUE_FITTING_IN_12_BIT;
                    }
                    if (np == 1 && pv > 0) {
                        compressedData.put(op - 1, (short) (compressedData.get(op - 1) + N20481));
                        skip = true;
                    }
                }
            }
            if (!skip) {
                while (np > 0) {
                    compressedData.put(op, (short) (Math.min(LAST_VALUE_FITTING_IN_12_BIT, np) + FIRST_VALUE_WITH_15_BIT));
                    ++op;
                    np += -LAST_VALUE_FITTING_IN_12_BIT;
                }
            }
            x1 = ip + 1;
            iz = x1;
            pv = nv;
        }
        compressedData.put(HEADER_SIZE_FIELD1, (short) (op % FIRST_VALUE_WITH_16_BIT));
        compressedData.put(HEADER_SIZE_FIELD2, (short) (op / FIRST_VALUE_WITH_16_BIT));
        compressedData.position(op);
    }

    /**
     * PL_L2PI -- Translate a PLIO line list into an integer pixel array. The
     * number of pixels output (always npix) is returned as the function value.
     * 
     * @param compressedData
     *            encoded line list
     * @param pixelData
     *            output pixel array
     * @param npix
     *            number of pixels to convert
     */
    public void decompress(ShortBuffer compressedData, IntBuffer pixelData, int npix) {
        int llfirt;
        int lllen;
        if (!(compressedData.get(2) > 0)) {
            lllen = (compressedData.get(HEADER_SIZE_FIELD2) << SHIFT_15_BITS) + compressedData.get(HEADER_SIZE_FIELD1);
            llfirt = compressedData.get(1);
        } else {
            lllen = compressedData.get(MINI_HEADER_SIZE_FIELD);
            llfirt = MINI_HEADER_SIZE;
        }
        final int xe = npix - 1;
        int op = 0;
        int x1 = 1;
        int pv = 1;
        for (int ip = llfirt; ip <= lllen; ++ip) {
            final int opcode = compressedData.get(ip) / FIRST_VALUE_WITH_13_BIT;
            final int data = compressedData.get(ip) & LAST_VALUE_FITTING_IN_12_BIT;
            final int sw0001 = opcode + 1;
            if (sw0001 == OPCODE_1 || sw0001 == OPCODE_5 || sw0001 == OPCODE_6) {
                final int x2 = x1 + data - 1;
                final int i2 = Math.min(x2, xe);
                final int np = i2 - Math.max(x1, 0) + 1;
                if (np > 0) {
                    final int otop = op + np - 1;
                    if (!(opcode == OPCODE_4)) {
                        for (int index = op; index <= otop; ++index) {
                            pixelData.put(index, 0);
                        }
                        if (opcode == OPCODE_5 && i2 == x2) {
                            pixelData.put(otop, pv);
                        }
                    } else {
                        for (int index = op; index <= otop; ++index) {
                            pixelData.put(index, pv);
                        }
                    }
                    op = otop + 1;
                }
                x1 = x2 + 1;
            } else if (sw0001 == OPCODE_2) {
                pv = (compressedData.get(ip + 1) << SHIFT_12_BITS) + data;
                ++ip;
            } else if (sw0001 == OPCODE_3) {
                pv += data;
            } else if (sw0001 == OPCODE_4) {
                pv -= data;
            } else if (sw0001 == OPCODE_7) {
                pv += data;
                if (x1 >= 0 && x1 <= xe) {
                    pixelData.put(op, pv);
                    ++op;
                }
                ++x1;
            } else if (sw0001 == OPCODE_8) {
                pv -= data;
                if (x1 >= 0 && x1 <= xe) {
                    pixelData.put(op, pv);
                    ++op;
                }
                ++x1;
            }
            if (x1 > xe) {
                break;
            }
        }
        for (int index = op; index < npix; ++index) {
            pixelData.put(index, 0);
        }
        pixelData.position(npix);
    }

}
