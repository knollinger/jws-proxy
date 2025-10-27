package de.cbfagree.webstart.backend;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>
 * Der {@link PendingDownloadInputStream} kapselt den Zugriff auf einen
 * laufenden Download innerhalb des Proxies.
 * </p>
 * 
 * <p>
 * Sinn und Zweck des ganzen ist es, die bereits verfügbaren Daten eines
 * Downloads ausliefern zu können. Der Download wird durch eine Instanz
 * von {@link WriteThroughBuffer} beschrieben.
 * </p>
 * 
 * <p>
 * Sofern der Buffer noch nicht bereit ist, liefern die read(...)-Methoden
 * einfach nur 0 um anzuzeigen das keine Daten bereit stehen. Dies bricht
 * zwar die Interface-Spec von {@link InputStream}, der 
 * {@link PendingDownloadInputStream} soll aber zwingend non-blocking sein.
 * </p>
 * 
 * <p>
 * Wenn der Buffer bereit wird, so wird intern ein InputStream erzeugt, welcher
 * folgenden Inhalt liefert:
 * 
 * <ul>
 * <li>Einen HTTP-Header mit folgenden Parametern:
 *   <ul>
 *     <li>Empfangener HTTP-StatusCode</öi>
 *     <li>Transfer-Encoding: chunked</li>
 *     <li>Content-Type: &lt;empfangender ContentType&gt;
 *   </ul>
 * </li>
 * <li>den Content des Buffers als HTTP-ChunkedEncoding Inputstream</li>
 * </ul>
 * </p>
 * 
 * <p>
 * Der komplette {@link PendingDownloadInputStream} sieht also folgendermaßen aus:
 * 
 * <pre>
 * {@link PendingDownloadInputStream} -> {@link HttpInputStream} -> {@link ChunkedEncodingInputStream} -> {@link WriteThroughBufferInputStream} -> {@link WriteThroughBuffer}
 * </pre>
 * 
 * Ein wenig länglich, das ganze, es bricht aber die Komplexität in "handliche"
 * Portionen herunter.
 * 
 * </p>
 * 
 */
public class PendingDownloadInputStream extends InputStream
{
    private static final String HTTP_HEADER = "" //
        + "HTTP/1.1 %1$d OK\r\n" //
        + "Transfer-Encoding: chunked\r\n" //
        + "Content-Type: %2$s\r\n" //
        + "\r\n";

    private WriteThroughBuffer buffer;
    private InputStream srcStream;

    /**
     * @param buffer
     */
    public PendingDownloadInputStream(WriteThroughBuffer buffer)
    {
        this.buffer = buffer;
        this.srcStream = null;
    }

    /**
     *
     */
    @Override
    public void close() throws IOException
    {
        if (this.srcStream != null)
        {
            this.srcStream.close();
        }
    }

    /**
     *
     */
    @Override
    public int read() throws IOException
    {
        byte[] tmp = new byte[1];
        int read = this.read(tmp, 0, tmp.length);
        if (read != -1)
        {
            read = tmp[0];
        }
        return read;
    }

    /**
     * 
     */
    @Override
    public int read(byte[] buf) throws IOException
    {
        return this.read(buf, 0, buf.length);
    }

    /**
     *
     */
    @Override
    public int read(byte[] buf, int pos, int len) throws IOException
    {
        int result = 0;

        if (this.srcStream == null && this.buffer.isReady())
        {
            this.srcStream = this.createSrcStream();
        }

        if (this.srcStream != null)
        {
            result = this.srcStream.read(buf, pos, len);
        }

        return result;
    }

    /**
     * @return
     */
    private InputStream createSrcStream()
    {
        String httpHeader = String.format(HTTP_HEADER, //
            this.buffer.getStatusCode(), //
            this.buffer.getContentType());

        InputStream dataIn = new ChunkedEncodingInputStream(new WriteThroughBufferInputStream(this.buffer));
        return new HttpInputStream(httpHeader, dataIn);
    }

    /**
     * Liefert einen InputStream, welcher zuerst einen HTTP-Header ausliefert
     * und danach den Content eines DatenStreams.
     * 
     * Normalerweise könnte man hier einen SequenceInputStream verwenden. Dummerweise
     * sind die ContentStreams non-blocking, ein read auf diesen Streams können also
     * 0 liefern falls grade keine Daten anliegen. Dann versucht aber der 
     * SequenceInputStream den switch auf den nächsten Stream und das währe fatal.
     */
    private static class HttpInputStream extends InputStream
    {
        private InputStream headerStream;
        private InputStream dataStream;

        public HttpInputStream(String httpHeader, InputStream dataStream)
        {
            this.headerStream = new ByteArrayInputStream(httpHeader.getBytes());
            this.dataStream = dataStream;
        }

        public void close() throws IOException
        {
            this.dataStream.close();
        }

        public int read() throws IOException
        {
            throw new UnsupportedOperationException();
        }

        public int read(byte[] buffer) throws IOException
        {
            return this.read(buffer, 0, buffer.length);
        }

        public int read(byte[] buffer, int pos, int len) throws IOException
        {
            int read = this.headerStream.read(buffer, pos, len);
            if (read == -1)
            {
                read = this.dataStream.read(buffer, pos, len);
            }
            return read;
        }
    }
    
    /**
     * <p>
     * Der {@link ChunkedEncodingInputStream} liest die rohen Daten vom SourceStream
     * und liefert sie als Chunked-Stream im Sinne von RFC 9112 (Kapitel 7.1)
     * </p>
     * 
     * <p>
     * Der Stream ist nicht blockierend, aus diesem Grunde kann read() nicht
     * implementiert werden.
     * </p>
     * 
     * <p>
     * Es werden also die nächsten verfügbaren Bytes vom SourceStream gelesen,
     * wenn wenigstens ein Byte empfangen wurde, wird folgendes geliefert:
     * </p>
     * 
     * <pre>
     * CHUNK-LEN\r\n
     * Daten
     * \r\n
     * </pre>
     * 
     * <p>
     * Wobei CHUNK-LEN die Länge des Daten-Chunks in Hexadezimal-Darstellung 
     * ist. Wenn vom srcStream EOF geliefert wird, so wird noch ein leerer
     * Chunk geliefert und dann EOF.
     * </p>
     * 
     * <p>
     * Der leere Chunk sieht folgendermaßen aus:
     * </p>
     * 
     * <pre>
     * 0\r\n
     * \r\n
     * </pre>
     */
    private static class ChunkedEncodingInputStream extends InputStream
    {
        private static String HEADER_PATTERN = "%04x\r\n";
        private static int HEADER_LEN = 6; // 4 byte hex-länge + \r + \n
        private static byte[] TRAILER = "\r\n".getBytes();
        private static int TRAILER_LEN = TRAILER.length;
        private static byte[] ZERO_CHUNK = "0\r\n\r\n".getBytes();

        private InputStream srcStream;
        private byte[] buffer;
        private int currPos;
        private int lastPos;
        private boolean eof;

        /**
         * @param srcStream
         */
        public ChunkedEncodingInputStream(InputStream srcStream)
        {
            this.srcStream = srcStream;
            this.buffer = new byte[8 * 1024];
            this.currPos = 0;
            this.lastPos = 0;
            this.eof = false;
        }

        /**
         *
         */
        @Override
        public int read() throws IOException
        {
            byte[] tmp = new byte[1];
            int read = this.read(tmp, 0, tmp.length);
            if (read != -1)
            {
                read = tmp[0];
            }
            return read;
        }

        /**
         *
         */
        @Override
        public int read(byte[] buffer) throws IOException
        {
            return this.read(buffer, 0, buffer.length);
        }

        /**
         *
         */
        @Override
        public int read(byte[] buffer, int pos, int len) throws IOException
        {
            int result = 0;
            int remaining = this.lastPos - this.currPos;
            if (remaining > 0)
            {
                result = Math.min(len, remaining);
                System.arraycopy(this.buffer, this.currPos, buffer, pos, result);
                this.currPos += result;
            }
            else
            {
                if (this.eof)
                {
                    result = -1;
                }
                else
                {
                    this.fillBuffer();
                }
            }
            return result;
        }

        /**
         * Lese den nächsten rohen Teil vom SourceStream, überführe Ihn in die
         * Chunked-Darstellung und stelle ihn in den internen Buffer ein.
         * 
         * Sollte vom srcStream EOF empfangen werden, so wird noch der 0-Chunk 
         * in den Buffer gestellt und das eof-flag gesetzt.
         * 
         * Die ChunkLängen werden immer als 4-Stellige HEX-Digits dar gestellt,
         * ggf. mit führenden Nullen. Dadurch kann der read vom srcStream immer
         * in die selbe Offset-Position (4 Byte Länge + \r + \n) des internen
         * Buffers erfolgen. Die eigentliche ChunkLänge kennen wir ja erst nach 
         * dem read vom srcStream.
         * 
         * @throws IOException
         */
        private void fillBuffer() throws IOException
        {
            int freeCapacity = this.buffer.length - (HEADER_LEN + TRAILER_LEN);
            int read = this.srcStream.read(this.buffer, HEADER_LEN, freeCapacity);
            this.currPos = 0;
            switch (read)
            {
                case -1 :
                    System.arraycopy(ZERO_CHUNK, 0, this.buffer, 0, ZERO_CHUNK.length);
                    this.lastPos = ZERO_CHUNK.length;
                    this.eof = true;
                    break;

                case 0 :
                    this.lastPos = 0;
                    break;

                default :
                    byte[] header = String.format(HEADER_PATTERN, read).getBytes();
                    System.arraycopy(header, 0, this.buffer, 0, HEADER_LEN);
                    System.arraycopy(TRAILER, 0, this.buffer, read + HEADER_LEN, TRAILER_LEN);
                    this.lastPos = HEADER_LEN + read + TRAILER_LEN;
                    break;
            }
        }
        
        /**
         * @throws IOException 
         *
         */
        @Override
        public void close() throws IOException
        {
            this.eof = true;
            this.currPos = 0;
            this.lastPos = 0;
            this.srcStream.close();
        }
    }

    /**
     * Aus einem WriteThroughBuffer kann durch mehrere Threads parallel gelesen 
     * werden. Der {@link WriteThroughBufferInputStream} managed dafür die 
     * aktuelle LesePosition.
     * 
     * Diese kann nicht im WriteThroughBuffer verwaltet werden, da ja jeder Thread 
     * von einer unterschiedlichen Position lesen will.
     * 
     */
    private static class WriteThroughBufferInputStream extends InputStream
    {
        private int currPos;
        private WriteThroughBuffer dataSource;

        /**
         * @param src
         */
        public WriteThroughBufferInputStream(WriteThroughBuffer src)
        {
            this.currPos = 0;
            this.dataSource = src;
        }

        /**
         *
         */
        @Override
        public int read() throws IOException
        {
            byte[] tmp = new byte[1];
            int read = this.read(tmp, 0, tmp.length);
            if (read != -1)
            {
                read = tmp[0];
            }
            return read;
        }

        /**
         *
         */
        @Override
        public int read(byte[] buf) throws IOException
        {
            return this.read(buf, 0, buf.length);
        }

        /**
         *
         */
        @Override
        public int read(byte[] buf, int pos, int len) throws IOException
        {
            try
            {
                int read = this.dataSource.getBytes(this.currPos, buf, pos, len);
                if (read > 0)
                {
                    this.currPos += read;
                }
                return read;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IOException("read from chunkedbuffer interrupted", e);
            }
        }
    }
}
