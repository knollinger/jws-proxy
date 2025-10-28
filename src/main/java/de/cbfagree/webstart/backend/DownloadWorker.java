package de.cbfagree.webstart.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;

import de.cbfagree.webstart.config.BackendConfig;

/**
 * Der DownloadWorker fungiert als DaemonThread, welcher an der JobQueue
 * lauscht. Sobald einn neuer Job gefunden wird, so wird die Resource
 * herunter geladen und in den im Job angegebenen {@link WriteThroughBuffer}
 * eingestellt.
 * 
 */
class DownloadWorker extends Thread
{
    private static final String CHUNK_HEADER_PATTERN = "%x\r\n";
    private static final byte[] CHUNK_TRAILER = "\r\n".getBytes();
    private static final byte[] LAST_CHUNK = "0\r\n\r\n".getBytes();

    private static int workerNr = 0;

    private URL baseUrl;
    private Proxy proxy;
    private int connTimeout;
    private int readTimeout;
    private LinkedBlockingQueue<DownloadTask> queue;

    /**
     * @param baseUrl
     * @param queue
     */
    public DownloadWorker(BackendConfig cfg, Proxy httpProxy, LinkedBlockingQueue<DownloadTask> queue)
    {
        this.baseUrl = cfg.getBaseUrl();
        this.proxy = httpProxy;
        this.queue = queue;
        this.connTimeout = cfg.getConnTimeout();
        this.readTimeout = cfg.getReadTimeout();
        this.setName(String.format("download-worker-%1$d", workerNr++));
        this.setDaemon(true);
        this.start();
    }

    /**
     * Warte an der Queue bis ein Job verfügbar ist und führe diesen dann
     * aus. Nach dem Ausführen des Jobs hängt sich der Thread wieder an 
     * die Queue und wartet auf den nächsten Job.
     */
    @Override
    public void run()
    {
        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                DownloadTask job = this.queue.take();
                this.doDownload(job);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * @param task
     */
    private void doDownload(DownloadTask task)
    {
        try
        {
            HttpURLConnection conn = this.createDownloadConnection(task.fileName());
            WriteThroughBuffer taskBuffer = task.buffer();

            int statusCode = conn.getResponseCode();
            this.writeHTTPHeader(conn, taskBuffer);
            taskBuffer.setReadyForRead(true);

            if (statusCode < 300)
            {
                try (InputStream in = conn.getInputStream())
                {
                    byte[] buffer = new byte[0xffff];
                    int read = in.read(buffer);
                    while (read != -1)
                    {
                        taskBuffer.append(String.format(CHUNK_HEADER_PATTERN, read).getBytes());
                        taskBuffer.append(buffer, read);
                        taskBuffer.append(CHUNK_TRAILER); // TODO: zusammen fassen um nicht 3* den RWLock anfordern zu müssen
                        read = in.read(buffer);
                    }
                    taskBuffer.append(LAST_CHUNK);
                    taskBuffer.close();

                    if (statusCode == 200)
                    {
                        File tmpFile = this.createCacheFile(taskBuffer);
                        task.observer().downloadCompleted(task.fileName(), tmpFile);
                    }
                }
            }
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * @param conn
     * @param taskBuffer
     * @throws IOException
     * @throws InterruptedException
     */
    private void writeHTTPHeader(HttpURLConnection conn, WriteThroughBuffer taskBuffer)
        throws IOException, InterruptedException
    {
        StringBuilder hdr = new StringBuilder() //
            .append(String.format("HTTP/1.1 %d OK\r\n", conn.getResponseCode())) //
            .append("Connection: close\r\n") //
            .append("Transfer-Encoding: chunked\r\n") //
            .append(String.format("Content-Type: %s\r\n", conn.getContentType())) //
            .append("\r\n");
        taskBuffer.append(hdr.toString().getBytes());
    }

    /**
     * @param fileName
     * @return
     * @throws IOException
     */
    private HttpURLConnection createDownloadConnection(String fileName) throws IOException
    {
        URL url = this.createDownloadURL(fileName);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(this.proxy);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(this.connTimeout);
        conn.setReadTimeout(this.readTimeout);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        //        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    /**
     * @param fileName
     * @return
     * @throws MalformedURLException
     */
    private URL createDownloadURL(String fileName) throws MalformedURLException
    {
        String path = this.baseUrl.getPath();
        if (!path.endsWith("/") && !fileName.startsWith("/"))
        {
            path += "/";
        }
        path += fileName;

        return new URL(this.baseUrl.getProtocol(), this.baseUrl.getHost(), this.baseUrl.getPort(), path);
    }

    /**
     * 
     * Generiere das File für den Cache. Das Cache-File beinhaltet eine komplette
     * Http-Response, inklusive dem Header.Der Inhalt sieht also folgendermassen aus:
     * 
     * <pre>
     * HTTP &lt;responseCode&gt; &lt;reasonPhrase&gt\r\n
     * Content-Type: &lt;empfangener ContentType aus der URLConnection&gt;\r\n
     * Transfer-Encoding: chunked\r\n
     * Connection: close\r\n
     * \r\n
     * &lt;Inhalt des Buffers als Http-Chunks&gt;
     * </pre>
     * 
     * @param content
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private File createCacheFile(WriteThroughBuffer content) throws IOException, InterruptedException
    {
        File tmpFile = File.createTempFile("jwsproxy_", ".tmp");

        try (FileOutputStream fileOut = new FileOutputStream(tmpFile))
        {
            byte[] tmpBuf = new byte[8192];
            int currPos = 0;
            int read = content.getBytes(currPos, tmpBuf, 0, tmpBuf.length);
            while (read != -1)
            {
                fileOut.write(tmpBuf, 0, read);
                currPos += read;
                read = content.getBytes(currPos, tmpBuf, 0, tmpBuf.length);
            }
            fileOut.flush();

            return tmpFile;
        }
        catch (IOException | InterruptedException e)
        {
            tmpFile.delete();
            throw e;
        }
    }
}
