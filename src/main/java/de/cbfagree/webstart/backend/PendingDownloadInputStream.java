package de.cbfagree.webstart.backend;

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
 * {@link PendingDownloadInputStream} -> {@link WriteThroughBufferInputStream} -> {@link WriteThroughBuffer}
 * </pre>
 * 
 * Ein wenig länglich das ganze, es bricht aber die Komplexität in "handliche"
 * Portionen herunter.
 * 
 * </p>
 * 
 */
public class PendingDownloadInputStream extends InputStream
{
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
        return new WriteThroughBufferInputStream(this.buffer);
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
