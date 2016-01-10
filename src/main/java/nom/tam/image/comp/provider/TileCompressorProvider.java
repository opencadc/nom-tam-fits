package nom.tam.image.comp.provider;

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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import nom.tam.image.comp.ICompressOption;
import nom.tam.image.comp.ITileCompressor;
import nom.tam.image.comp.ITileCompressorProvider;
import nom.tam.image.comp.gzip.GZipCompressor.ByteGZipCompressor;
import nom.tam.image.comp.gzip.GZipCompressor.DoubleGZipCompressor;
import nom.tam.image.comp.gzip.GZipCompressor.FloatGZipCompressor;
import nom.tam.image.comp.gzip.GZipCompressor.IntGZipCompressor;
import nom.tam.image.comp.gzip.GZipCompressor.LongGZipCompressor;
import nom.tam.image.comp.gzip.GZipCompressor.ShortGZipCompressor;
import nom.tam.image.comp.gzip2.GZip2Compressor.ByteGZip2Compress;
import nom.tam.image.comp.gzip2.GZip2Compressor.IntGZip2Compressor;
import nom.tam.image.comp.gzip2.GZip2Compressor.LongGZip2Compressor;
import nom.tam.image.comp.gzip2.GZip2Compressor.ShortGZip2Compressor;
import nom.tam.image.comp.hcompress.HCompressor.ByteHCompressor;
import nom.tam.image.comp.hcompress.HCompressor.DoubleHCompressor;
import nom.tam.image.comp.hcompress.HCompressor.FloatHCompressor;
import nom.tam.image.comp.hcompress.HCompressor.IntHCompressor;
import nom.tam.image.comp.hcompress.HCompressor.ShortHCompressor;
import nom.tam.image.comp.plio.PLIOCompress.BytePLIOCompressor;
import nom.tam.image.comp.plio.PLIOCompress.IntPLIOCompressor;
import nom.tam.image.comp.plio.PLIOCompress.ShortPLIOCompressor;
import nom.tam.image.comp.rice.RiceCompressor.ByteRiceCompressor;
import nom.tam.image.comp.rice.RiceCompressor.DoubleRiceCompressor;
import nom.tam.image.comp.rice.RiceCompressor.FloatRiceCompressor;
import nom.tam.image.comp.rice.RiceCompressor.IntRiceCompressor;
import nom.tam.image.comp.rice.RiceCompressor.ShortRiceCompressor;

/**
 * Standard implementation of the {@code ITileCompressorProvider} interface.
 */
public class TileCompressorProvider implements ITileCompressorProvider {

    /**
     * private implementation of the tile compression provider, all is based on
     * the option based constructor of the compressors.
     */
    protected static class TileCompressorControl implements ITileCompressorControl {

        private final Constructor<ITileCompressor<Buffer>> constructor;

        private final Class<? extends ICompressOption> optionClass;

        @SuppressWarnings("unchecked")
        protected TileCompressorControl(Class<?> compressorClass) {
            this.constructor = (Constructor<ITileCompressor<Buffer>>) compressorClass.getConstructors()[0];
            this.optionClass = (Class<? extends ICompressOption>) (this.constructor.getParameterTypes().length == 0 ? null : this.constructor.getParameterTypes()[0]);
        }

        @Override
        public boolean compress(Buffer in, ByteBuffer out, ICompressOption option) {
            try {
                return newCompressor(option).compress(in, out);
            } catch (Exception e) {
                LOG.log(Level.FINE, "could not compress using " + this.constructor + " must fallback to other compression method", e);
                return false;
            }
        }

        @Override
        public void decompress(ByteBuffer in, Buffer out, ICompressOption option) {
            try {
                newCompressor(option).decompress(in, out);
            } catch (Exception e) {
                throw new IllegalStateException("could not decompress " + this.constructor, e);
            }
        }

        private ITileCompressor<Buffer> newCompressor(ICompressOption option) throws InstantiationException, IllegalAccessException, InvocationTargetException {
            return this.constructor.getParameterTypes().length == 0 ? this.constructor.newInstance() : this.constructor.newInstance(option);
        }

        @Override
        public ICompressOption option() {
            if (this.optionClass != null) {
                try {
                    return this.optionClass.newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException("could not instantiate option class for " + this.constructor, e);
                }
            }
            return ICompressOption.NULL;
        }
    }

    private static final Class<?>[] AVAILABLE_COMPRESSORS = {
        ByteRiceCompressor.class,
        ShortRiceCompressor.class,
        IntRiceCompressor.class,
        FloatRiceCompressor.class,
        DoubleRiceCompressor.class,

        BytePLIOCompressor.class,
        ShortPLIOCompressor.class,
        IntPLIOCompressor.class,

        ByteHCompressor.class,
        ShortHCompressor.class,
        IntHCompressor.class,
        FloatHCompressor.class,
        DoubleHCompressor.class,

        ByteGZip2Compress.class,
        ShortGZip2Compressor.class,
        IntGZip2Compressor.class,
        LongGZip2Compressor.class,

        ByteGZipCompressor.class,
        ShortGZipCompressor.class,
        IntGZipCompressor.class,
        LongGZipCompressor.class,
        FloatGZipCompressor.class,
        DoubleGZipCompressor.class
    };

    private static final TileCompressorControlNameComputer NAME_COMPUTER = new TileCompressorControlNameComputer();

    /**
     * logger to log to.
     */
    private static final Logger LOG = Logger.getLogger(TileCompressorProvider.class.getName());

    public static ITileCompressorControl findCompressorControl(String quantAlgorithm, String compressionAlgorithm, Class<?> baseType) {
        ITileCompressorProvider defaultProvider = null;
        for (ITileCompressorProvider iTileCompressorProvider : ServiceLoader.load(ITileCompressorProvider.class, Thread.currentThread().getContextClassLoader())) {
            if (iTileCompressorProvider instanceof TileCompressorProvider) {
                defaultProvider = iTileCompressorProvider;
            } else {
                ITileCompressorControl result = iTileCompressorProvider.createCompressorControl(quantAlgorithm, compressionAlgorithm, baseType);
                if (result != null) {
                    return result;
                }
            }
        }
        return defaultProvider.createCompressorControl(quantAlgorithm, compressionAlgorithm, baseType);
    }

    @Override
    public ITileCompressorControl createCompressorControl(String quantAlgorithm, String compressionAlgorithm, Class<?> baseType) {

        String className = NAME_COMPUTER.createCompressorClassName(quantAlgorithm, compressionAlgorithm, baseType);
        for (Class<?> clazz : AVAILABLE_COMPRESSORS) {
            if (clazz.getSimpleName().equals(className)) {
                return new TileCompressorControl(clazz);
            }
        }
        return null;
    }
}
