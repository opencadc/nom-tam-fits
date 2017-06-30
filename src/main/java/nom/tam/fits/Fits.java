package nom.tam.fits;

/*
 * #%L
 * nom.tam FITS library
 * %%
 * Copyright (C) 2004 - 2015 nom-tam-fits
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

import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import nom.tam.fits.compress.CompressionManager;
import nom.tam.fits.utilities.FitsCheckSum;
import nom.tam.util.ArrayDataInput;
import nom.tam.util.ArrayDataOutput;
import nom.tam.util.BufferedDataInputStream;
import nom.tam.util.BufferedDataOutputStream;
import nom.tam.util.BufferedFile;
import nom.tam.util.RandomAccess;
import nom.tam.util.SafeClose;

/**
 * This class provides access to routines to allow users to read and write FITS
 * files. <br>
 * <b> Description of the Package </b>
 * <p>
 * This FITS package attempts to make using FITS files easy, but does not do
 * exhaustive error checking. Users should not assume that just because a FITS
 * file can be read and written that it is necessarily legal FITS. These classes
 * try to make it easy to transform between arrays of Java primitives and their
 * FITS encodings.
 * <ul>
 * <li>The Fits class provides capabilities to read and write data at the HDU
 * level, and to add and delete HDU's from the current Fits object. A large
 * number of constructors are provided which allow users to associate the Fits
 * object with some form of external data. This external data may be in a
 * compressed format.
 * <p>
 * Note that this association is limited, it only specifies where the various
 * read methods should read data from. It does not automatically read the data
 * content and store the results. To ensure that the external content has been
 * read and parsed the user may wish to invoke the read() method after creating
 * the Fits object associated with external data. E.g.,
 * 
 * <pre>
 *     File fl = ...  ; 
 *     Fits f = new Fits(fl); // Or we could have used one of the other constructors.
 *     // At this point the Fits object is empty.
 *     f.read();    // Read the external data into the Fits object
 *     // At this point the Fits object should have one or more HDUs depending
 *     // upon the external content.
 * </pre>
 * 
 * Users can choose to read only some of the HDUs in a given input, and may add
 * HDU's that were either read from other files or generated by the program. See
 * the various read and addHDU methods.
 * <li>The FitsFactory class is a factory class which is used to create HDUs.
 * HDU's can be of a number of types derived from the abstract class BasicHDU.
 * The hierarchy of HDUs is:
 * <ul>
 * <li>BasicHDU
 * <ul>
 * <li>ImageHDU
 * <li>RandomGroupsHDU
 * <li>TableHDU
 * <ul>
 * <li>BinaryTableHDU
 * <li>AsciiTableHDU
 * </ul>
 * <li>UndefinedHDU
 * </ul>
 * </ul>
 * <li>The Header class provides many functions to add, delete and read header
 * keywords in a variety of formats.
 * <li>The HeaderCard class provides access to the structure of a FITS header
 * card.
 * <li>The header package defines sets of enumerations that allow users to
 * create and access header keywords in a controlled way.
 * <li>The Data class is an abstract class which provides the basic methods for
 * reading and writing FITS data. It provides methods to get the the actual
 * underlying arrays and detailed methods for manipulation specific to the
 * different data types.
 * <li>The TableHDU class provides a large number of methods to access and
 * modify information in tables.
 * <li>The utilities package includes simple tools to copy and list FITS files.
 * </ul>
 * 
 * @version 1.12
 */
public class Fits implements Closeable {

    /**
     * logger to log to.
     */
    private static final Logger LOG = Logger.getLogger(Fits.class.getName());

    /**
     * The input stream associated with this Fits object.
     */
    private ArrayDataInput dataStr;

    /**
     * A vector of HDUs that have been added to this Fits object.
     */
    private final List<BasicHDU<?>> hduList = new ArrayList<BasicHDU<?>>();

    /**
     * Has the input stream reached the EOF?
     */
    private boolean atEOF;

    /**
     * The last offset we reached. A -1 is used to indicate that we cannot use
     * the offset.
     */
    private long lastFileOffset = -1;

    /**
     * Create an empty Fits object which is not associated with an input stream.
     */
    public Fits() {
    }

    /**
     * Associate FITS object with a File. If the file is compressed a stream
     * will be used, otherwise random access will be supported.
     * 
     * @param myFile
     *            The File object. The content of this file will not be read
     *            into the Fits object until the user makes some explicit
     *            request. * @throws FitsException if the operation failed
     * @throws FitsException
     *             if the operation failed
     */
    public Fits(File myFile) throws FitsException {
        this(myFile, CompressionManager.isCompressed(myFile));
    }

    /**
     * Associate the Fits object with a File
     * 
     * @param myFile
     *            The File object. The content of this file will not be read
     *            into the Fits object until the user makes some explicit
     *            request.
     * @param compressed
     *            Is the data compressed?
     * @throws FitsException
     *             if the operation failed
     */
    public Fits(File myFile, boolean compressed) throws FitsException {
        fileInit(myFile, compressed);
    }

    /**
     * Create a Fits object associated with the given data stream. Compression
     * is determined from the first few bytes of the stream.
     * 
     * @param str
     *            The data stream. The content of this stream will not be read
     *            into the Fits object until the user makes some explicit
     *            request.
     * @throws FitsException
     *             if the operation failed
     */
    public Fits(InputStream str) throws FitsException {
        streamInit(str);
    }

    /**
     * Create a Fits object associated with a data stream.
     * 
     * @param str
     *            The data stream. The content of this stream will not be read
     *            into the Fits object until the user makes some explicit
     *            request.
     * @param compressed
     *            Is the stream compressed? This is currently ignored.
     *            Compression is determined from the first two bytes in the
     *            stream.
     * @throws FitsException
     *             if the operation failed
     * @deprecated use {@link #Fits(InputStream)} compression is auto detected.
     */
    @Deprecated
    public Fits(InputStream str, boolean compressed) throws FitsException {
        this(str);
        LOG.log(Level.INFO, "compression ignored, will be autodetected. was set to " + compressed);
    }

    /**
     * Associate the FITS object with a file or URL. The string is assumed to be
     * a URL if it begins one of the protocol strings. If the string ends in .gz
     * it is assumed that the data is in a compressed format. All string
     * comparisons are case insensitive.
     * 
     * @param filename
     *            The name of the file or URL to be processed. The content of
     *            this file will not be read into the Fits object until the user
     *            makes some explicit request.
     * @throws FitsException
     *             Thrown if unable to find or open a file or URL from the
     *             string given.
     **/
    public Fits(String filename) throws FitsException {
        this(filename, CompressionManager.isCompressed(filename));
    }

    /**
     * Associate the FITS object with a file or URL. The string is assumed to be
     * a URL if it begins one of the protocol strings. If the string ends in .gz
     * it is assumed that the data is in a compressed format. All string
     * comparisons are case insensitive.
     * 
     * @param filename
     *            The name of the file or URL to be processed. The content of
     *            this file will not be read into the Fits object until the user
     *            makes some explicit request.
     * @param compressed
     *            is the file compressed?
     * @throws FitsException
     *             Thrown if unable to find or open a file or URL from the
     *             string given.
     **/
    public Fits(String filename, boolean compressed) throws FitsException {
        if (filename == null) {
            throw new FitsException("Null FITS Identifier String");
        }
        try {
            File fil = new File(filename);
            if (fil.exists()) {
                fileInit(fil, compressed);
                return;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "not a file " + filename, e);
        }
        try {
            InputStream str = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
            if (str != null) {
                streamInit(str);
                return;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "not a resource " + filename, e);
        }
        try {
            InputStream is = FitsUtil.getURLStream(new URL(filename), 0);
            streamInit(is);
            return;
        } catch (Exception e) {
            LOG.log(Level.FINE, "not a url " + filename, e);
        }
        throw new FitsException("could not detect type of " + filename);
    }

    /**
     * Associate the FITS object with a given URL
     * 
     * @param myURL
     *            The URL to be read. The content of this URL will not be read
     *            into the Fits object until the user makes some explicit
     *            request.
     * @throws FitsException
     *             Thrown if unable to find or open a file or URL from the
     *             string given.
     */
    public Fits(URL myURL) throws FitsException {
        try {
            streamInit(FitsUtil.getURLStream(myURL, 0));
        } catch (IOException e) {
            throw new FitsException("Unable to open input from URL:" + myURL, e);
        }
    }

    /**
     * Associate the FITS object with a given uncompressed URL
     * 
     * @param myURL
     *            The URL to be associated with the FITS file. The content of
     *            this URL will not be read into the Fits object until the user
     *            makes some explicit request.
     * @param compressed
     *            Compression flag, ignored.
     * @throws FitsException
     *             Thrown if unable to use the specified URL.
     * @deprecated use {@link #Fits(InputStream)} compression is auto detected.
     */
    @Deprecated
    public Fits(URL myURL, boolean compressed) throws FitsException {
        this(myURL);
        LOG.log(Level.INFO, "compression ignored, will be autodetected. was set to " + compressed);
    }

    /**
     * @return a newly created HDU from the given Data.
     * @param data
     *            The data to be described in this HDU.
     * @param <DataClass>
     *            the class of the HDU
     * @throws FitsException
     *             if the operation failed
     */
    public static <DataClass extends Data> BasicHDU<DataClass> makeHDU(DataClass data) throws FitsException {
        Header hdr = new Header();
        data.fillHeader(hdr);
        return FitsFactory.hduFactory(hdr, data);
    }

    /**
     * @return a newly created HDU from the given header.
     * @param h
     *            The header which describes the FITS extension
     * @throws FitsException
     *             if the header could not be converted to a HDU.
     */
    public static BasicHDU<?> makeHDU(Header h) throws FitsException {
        Data d = FitsFactory.dataFactory(h);
        return FitsFactory.hduFactory(h, d);
    }

    /**
     * @return a newly created HDU from the given data kernel.
     * @param o
     *            The data to be described in this HDU.
     * @throws FitsException
     *             if the parameter could not be converted to a HDU.
     */
    public static BasicHDU<?> makeHDU(Object o) throws FitsException {
        return FitsFactory.hduFactory(o);
    }

    /**
     * @return the version of the library.
     */
    public static String version() {
        Properties props = new Properties();
        InputStream versionProperties = null;
        try {
            versionProperties = Fits.class.getResourceAsStream("/META-INF/maven/gov.nasa.gsfc.heasarc/nom-tam-fits/pom.properties");
            props.load(versionProperties);
            return props.getProperty("version");
        } catch (IOException e) {
            LOG.log(Level.INFO, "reading version failed, ignoring", e);
            return "unknown";
        } finally {
            saveClose(versionProperties);
        }
    }

    /**
     * close the input stream, and ignore eventual errors.
     *
     * @param in
     *            the input stream to close.
     */
    public static void saveClose(InputStream in) {
        SafeClose.close(in);
    }

    /**
     * Add an HDU to the Fits object. Users may intermix calls to functions
     * which read HDUs from an associated input stream with the addHDU and
     * insertHDU calls, but should be careful to understand the consequences.
     * 
     * @param myHDU
     *            The HDU to be added to the end of the FITS object.
     * @throws FitsException
     *             if the HDU could not be inserted.
     */
    public void addHDU(BasicHDU<?> myHDU) throws FitsException {
        insertHDU(myHDU, getNumberOfHDUs());
    }

    /**
     * Get the current number of HDUs in the Fits object.
     * 
     * @return The number of HDU's in the object.
     * @deprecated use {@link #getNumberOfHDUs()} instead
     */
    @Deprecated
    public int currentSize() {
        return getNumberOfHDUs();
    }

    /**
     * Delete an HDU from the HDU list.
     * 
     * @param n
     *            The index of the HDU to be deleted. If n is 0 and there is
     *            more than one HDU present, then the next HDU will be converted
     *            from an image to primary HDU if possible. If not a dummy
     *            header HDU will then be inserted.
     * @throws FitsException
     *             if the HDU could not be deleted.
     */
    public void deleteHDU(int n) throws FitsException {
        int size = getNumberOfHDUs();
        if (n < 0 || n >= size) {
            throw new FitsException("Attempt to delete non-existent HDU:" + n);
        }
        this.hduList.remove(n);
        if (n == 0 && size > 1) {
            BasicHDU<?> newFirst = this.hduList.get(0);
            if (newFirst.canBePrimary()) {
                newFirst.setPrimaryHDU(true);
            } else {
                insertHDU(BasicHDU.getDummyHDU(), 0);
            }
        }
    }

    /**
     * Get a stream from the file and then use the stream initialization.
     * 
     * @param myFile
     *            The File to be associated.
     * @param compressed
     *            Is the data compressed?
     * @throws FitsException
     *             if the opening of the file failed.
     */
    @SuppressFBWarnings(value = "OBL_UNSATISFIED_OBLIGATION", justification = "stream stays open, and will be read when nessesary.")
    protected void fileInit(File myFile, boolean compressed) throws FitsException {
        try {
            if (compressed) {
                streamInit(new FileInputStream(myFile));
            } else {
                randomInit(myFile);
            }
        } catch (IOException e) {
            throw new FitsException("Unable to create Input Stream from File: " + myFile, e);
        }
    }

    /**
     * @return the n'th HDU. If the HDU is already read simply return a pointer
     *         to the cached data. Otherwise read the associated stream until
     *         the n'th HDU is read.
     * @param n
     *            The index of the HDU to be read. The primary HDU is index 0.
     * @return The n'th HDU or null if it could not be found.
     * @throws FitsException
     *             if the header could not be read
     * @throws IOException
     *             if the underlying buffer threw an error
     */
    public BasicHDU<?> getHDU(int n) throws FitsException, IOException {
        int size = getNumberOfHDUs();
        for (int i = size; i <= n; i += 1) {
            BasicHDU<?> hdu = readHDU();
            if (hdu == null) {
                return null;
            }
        }
        return this.hduList.get(n);
    }

    /**
     * Get the current number of HDUs in the Fits object.
     * 
     * @return The number of HDU's in the object.
     */
    public int getNumberOfHDUs() {
        return this.hduList.size();
    }

    /**
     * Get the data stream used for the Fits Data.
     * 
     * @return The associated data stream. Users may wish to call this function
     *         after opening a Fits object when they wish detailed control for
     *         writing some part of the FITS file.
     */
    public ArrayDataInput getStream() {
        return this.dataStr;
    }

    /**
     * Insert a FITS object into the list of HDUs.
     * 
     * @param myHDU
     *            The HDU to be inserted into the list of HDUs.
     * @param position
     *            The location at which the HDU is to be inserted.
     * @throws FitsException
     *             if the HDU could not be inserted.
     */
    public void insertHDU(BasicHDU<?> myHDU, int position) throws FitsException {
        if (myHDU == null) {
            return;
        }
        if (position < 0 || position > getNumberOfHDUs()) {
            throw new FitsException("Attempt to insert HDU at invalid location: " + position);
        }
        try {
            if (position == 0) {
                // Note that the previous initial HDU is no longer the first.
                // If we were to insert tables backwards from last to first,
                // we could get a lot of extraneous DummyHDUs but we currently
                // do not worry about that.
                if (getNumberOfHDUs() > 0) {
                    this.hduList.get(0).setPrimaryHDU(false);
                }
                if (myHDU.canBePrimary()) {
                    myHDU.setPrimaryHDU(true);
                    this.hduList.add(0, myHDU);
                } else {
                    insertHDU(BasicHDU.getDummyHDU(), 0);
                    myHDU.setPrimaryHDU(false);
                    this.hduList.add(1, myHDU);
                }
            } else {
                myHDU.setPrimaryHDU(false);
                this.hduList.add(position, myHDU);
            }
        } catch (NoSuchElementException e) {
            throw new FitsException("hduList inconsistency in insertHDU", e);
        }
    }

    /**
     * Initialize using buffered random access. This implies that the data is
     * uncompressed.
     * 
     * @param file
     *            the file to open
     * @throws FitsException
     *             if the file could not be read
     */
    protected void randomInit(File file) throws FitsException {

        String permissions = "r";
        if (!file.exists() || !file.canRead()) {
            throw new FitsException("Non-existent or unreadable file");
        }
        if (file.canWrite()) {
            permissions += "w";
        }
        try {
            this.dataStr = new BufferedFile(file, permissions);
            ((BufferedFile) this.dataStr).seek(0);
        } catch (IOException e) {
            throw new FitsException("Unable to open file " + file.getPath(), e);
        }
    }

    /**
     * Return all HDUs for the Fits object. If the FITS file is associated with
     * an external stream make sure that we have exhausted the stream.
     * 
     * @return an array of all HDUs in the Fits object. Returns null if there
     *         are no HDUs associated with this object.
     * @throws FitsException
     *             if the reading failed.
     */
    public BasicHDU<?>[] read() throws FitsException {
        readToEnd();
        int size = getNumberOfHDUs();
        if (size == 0) {
            return new BasicHDU<?>[0];
        }
        return this.hduList.toArray(new BasicHDU<?>[size]);
    }

    /**
     * Read a FITS file from an InputStream object.
     * 
     * @param is
     *            The InputStream stream whence the FITS information is found.
     * @throws FitsException
     *             if the data read could not be interpreted
     */
    public void read(InputStream is) throws FitsException {
        if (is instanceof ArrayDataInput) {
            this.dataStr = (ArrayDataInput) is;
        } else {
            this.dataStr = new BufferedDataInputStream(is);
        }
        read();
    }

    /**
     * Read the next HDU on the default input stream.
     * 
     * @return The HDU read, or null if an EOF was detected. Note that null is
     *         only returned when the EOF is detected immediately at the
     *         beginning of reading the HDU.
     * @throws FitsException
     *             if the header could not be read
     * @throws IOException
     *             if the underlying buffer threw an error
     */
    public BasicHDU<?> readHDU() throws FitsException, IOException {
        if (this.dataStr == null || this.atEOF) {
            if (this.dataStr == null) {
                LOG.warning("trying to read a hdu, without an input source!");
            }
            return null;
        }
        if (this.dataStr instanceof RandomAccess && this.lastFileOffset > 0) {
            FitsUtil.reposition(this.dataStr, this.lastFileOffset);
        }
        Header hdr = Header.readHeader(this.dataStr);
        if (hdr == null) {
            this.atEOF = true;
            return null;
        }
        Data data = hdr.makeData();
        try {
            data.read(this.dataStr);
        } catch (PaddingException e) {
            e.updateHeader(hdr);
            if (!FitsFactory.getAllowTerminalJunk()) {
                throw e;
            }
        }
        this.lastFileOffset = FitsUtil.findOffset(this.dataStr);
        BasicHDU<Data> nextHDU = FitsFactory.hduFactory(hdr, data);
        this.hduList.add(nextHDU);
        return nextHDU;
    }

    /**
     * Read to the end of the associated input stream
     * 
     * @throws FitsException
     *             if the operation failed
     */
    private void readToEnd() throws FitsException {

        while (this.dataStr != null && !this.atEOF) {
            try {
                if (readHDU() == null) {
                    break;
                }
            } catch (EOFException e) {
                if (FitsFactory.getAllowTerminalJunk() && //
                        e.getCause() instanceof TruncatedFileException && //
                        getNumberOfHDUs() > 0) {
                    this.atEOF = true;
                    return;
                }
                throw new FitsException("IO error: " + e);
            } catch (IOException e) {
                throw new FitsException("IO error: " + e);
            }
        }
    }

    /**
     * Add or Modify the CHECKSUM keyword in all headers. by R J Mathar
     * 
     * @throws FitsException
     *             if the operation failed
     * @throws IOException
     *             if the underlying stream failed
     */
    public void setChecksum() throws FitsException, IOException {
        for (int i = 0; i < getNumberOfHDUs(); i += 1) {
            setChecksum(getHDU(i));
        }
    }

    /**
     * Set the data stream to be used for future input.
     * 
     * @param stream
     *            The data stream to be used.
     */
    public void setStream(ArrayDataInput stream) {
        this.dataStr = stream;
        this.atEOF = false;
        this.lastFileOffset = -1;
    }

    /**
     * Return the number of HDUs in the Fits object. If the FITS file is
     * associated with an external stream make sure that we have exhausted the
     * stream.
     * 
     * @return number of HDUs.
     * @deprecated The meaning of size of ambiguous. Use
     *             {@link #getNumberOfHDUs()} instead. Note size() will read the
     *             input file/stream to the EOF before returning the number of
     *             HDUs which {@link #getNumberOfHDUs()} does not. If you wish
     *             to duplicate this behavior and ensure that the input has been
     *             exhausted before getting the number of HDUs then use the
     *             sequence: <code>
     *    read(); 
     *    getNumberofHDUs();
     * </code>
     * @throws FitsException
     *             if the file could not be read.
     */
    @Deprecated
    public int size() throws FitsException {
        readToEnd();
        return getNumberOfHDUs();
    }

    /**
     * Skip the next HDU on the default input stream.
     * 
     * @throws FitsException
     *             if the HDU could not be skipped
     * @throws IOException
     *             if the underlying stream failed
     */
    public void skipHDU() throws FitsException, IOException {
        if (this.atEOF) {
            return;
        } else {
            Header hdr = new Header(this.dataStr);
            int dataSize = (int) hdr.getDataSize();
            this.dataStr.skipAllBytes(dataSize);
            if (this.dataStr instanceof RandomAccess) {
                this.lastFileOffset = ((RandomAccess) this.dataStr).getFilePointer();
            }
        }
    }

    /**
     * Skip HDUs on the associate input stream.
     * 
     * @param n
     *            The number of HDUs to be skipped.
     * @throws FitsException
     *             if the HDU could not be skipped
     * @throws IOException
     *             if the underlying stream failed
     */
    public void skipHDU(int n) throws FitsException, IOException {
        for (int i = 0; i < n; i += 1) {
            skipHDU();
        }
    }

    /**
     * Initialize the input stream. Mostly this checks to see if the stream is
     * compressed and wraps the stream if necessary. Even if the stream is not
     * compressed, it will likely be wrapped in a PushbackInputStream. So users
     * should probably not supply a BufferedDataInputStream themselves, but
     * should allow the Fits class to do the wrapping.
     * 
     * @param inputStream
     *            stream to initialize
     * @throws FitsException
     *             if the initialization failed
     */
    protected void streamInit(InputStream inputStream) throws FitsException {
        this.dataStr = new BufferedDataInputStream(CompressionManager.decompress(inputStream));
    }

    /**
     * Write a Fits Object to an external Stream.
     * 
     * @param os
     *            A DataOutput stream.
     * @throws FitsException
     *             if the operation failed
     */
    public void write(DataOutput os) throws FitsException {
        ArrayDataOutput obs;
        boolean newOS = false;
        if (os instanceof ArrayDataOutput) {
            obs = (ArrayDataOutput) os;
        } else if (os instanceof DataOutputStream) {
            newOS = true;
            obs = new BufferedDataOutputStream((DataOutputStream) os);
        } else {
            throw new FitsException("Cannot create ArrayDataOutput from class " + os.getClass().getName());
        }
        for (BasicHDU<?> basicHDU : hduList) {
            basicHDU.write(obs);
        }
        if (newOS) {
            try {
                obs.flush();
                obs.close();
            } catch (IOException e) {
                throw new FitsException("Error flushing/closing the FITS output stream: " + e, e);
            }
        }
        if (obs instanceof BufferedFile) {
            try {
                ((BufferedFile) obs).setLength(((BufferedFile) obs).getFilePointer());
            } catch (IOException e) {
                throw new FitsException("Error resizing the FITS output stream: " + e, e);
            }
        }
    }

    /**
     * Write the FITS to the specified file. This is a wrapper method provided
     * for convenience, which calls the {@link #write(DataOutput)} method. It
     * creates a suitable {@link nom.tam.util.BufferedFile}, to which the FITS
     * is then written. Upon completion the underlying stream is closed.
     * 
     * @param file
     *            a file to which the FITS is to be written.
     * @throws FitsException
     *             if {@link #write(DataOutput)} failed
     * @throws IOException
     *             if the underlying output stream could not be created or
     *             closed.
     */
    public void write(File file) throws IOException, FitsException {
        BufferedFile bf = null;
        try {
            bf = new BufferedFile(file, "rw");
            write(bf);
        } finally {
            SafeClose.close(bf);
        }
    }

    @Override
    public void close() throws IOException {
        if (dataStr != null) {
            this.dataStr.close();
        }
    }

    /**
     * set the checksum of a HDU.
     * 
     * @param hdu
     *            the HDU to add a checksum
     * @throws FitsException
     *             the checksum could not be added to the header
     * @deprecated use {@link FitsCheckSum#setChecksum(BasicHDU)}
     */
    @Deprecated
    public static void setChecksum(BasicHDU<?> hdu) throws FitsException {
        FitsCheckSum.setChecksum(hdu);
    }

    /**
     * calculate the checksum for the block of data
     * 
     * @param data
     *            the data to create the checksum for
     * @return the checksum
     * @deprecated use {@link FitsCheckSum#checksum(byte[])}
     */
    @Deprecated
    public static long checksum(final byte[] data) {
        return FitsCheckSum.checksum(data);
    }
}
