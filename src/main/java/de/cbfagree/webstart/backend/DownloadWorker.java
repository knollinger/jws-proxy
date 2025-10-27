package de.cbfagree.webstart.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Der DownloadWorker fungiert als DaemonThread, welcher an der JobQueue
 * lauscht. Sobald einn neuer Job gefunden wird, so wird die Resource
 * herunter geladen und in den im Job angegebenen {@link WriteThroughBuffer}
 * eingestellt.
 * 
 */
class DownloadWorker extends Thread
{
    private static int workerNr = 0;

    private URL baseUrl;
    private LinkedBlockingQueue<DownloadTask> queue;

    /**
     * @param baseUrl
     * @param queue
     */
    public DownloadWorker(URL baseUrl, LinkedBlockingQueue<DownloadTask> queue)
    {
        this.baseUrl = baseUrl;
        this.queue = queue;
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

            int statusCode = conn.getResponseCode();
            task.buffer().setStatusCode(statusCode);
            task.buffer().setContentType(conn.getContentType());

            if (statusCode == 200)
            {
                try (InputStream in = conn.getInputStream())
                {
                    byte[] buffer = new byte[0xffff];
                    int read = in.read(buffer);
                    while (read != -1)
                    {
                        task.buffer().append(buffer, read);
                        read = in.read(buffer);
                    }
                    task.buffer().close();

                    File tmpFile = this.createCacheFile(conn, task.buffer());
                    task.observer().downloadCompleted(task.fileName(), tmpFile);
                }
            }
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
            task.buffer().setBackendException(e);
        }
    }

    /**
     * @param fileName
     * @return
     * @throws IOException
     */
    private HttpURLConnection createDownloadConnection(String fileName) throws IOException
    {
        URL url = this.createDownloadURL(fileName);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        conn.setRequestProperty("Accept", "*/*");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoInput(true);
        conn.setDoOutput(true);
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
     * Generiere das File für den Cache. Das Cache-File beinhaltet eine komplette
     * Http-Response, inklusive dem Header.Der Inhalt sieht also folgendermassen aus:
     * 
     * <pre>
     * HTTP &lt;responseCode&gt; &lt;reasonPhrase&gt\r\n
     * Content-Type: &lt;empfangener ContentType aus der URLConnection&gt;\r\n
     * Content-Length: &lt;totale größe des Buffers&gt;\r\n
     * Connection: close\r\n
     * \r\n
     * &lt;Inhalt des Buffers&gt;
     * </pre>
     * 
     * @param urlConn
     * @param content
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private File createCacheFile(HttpURLConnection urlConn, WriteThroughBuffer content)
        throws IOException, InterruptedException
    {

        File tmpFile = File.createTempFile("jwsproxy_", ".tmp");

        try (FileOutputStream fileOut = new FileOutputStream(tmpFile))
        {
            Writer hdrWriter = new OutputStreamWriter(fileOut, StandardCharsets.US_ASCII);
            hdrWriter.write(
                String.format("HTTP/1.1 %1$d %2$s\r\n", urlConn.getResponseCode(), urlConn.getResponseMessage()));
            hdrWriter.write(String.format("Content-Type: %1$s\r\n", urlConn.getContentType()));
            hdrWriter.write(String.format("Content-Length: %1$d\r\n", content.getTotalLength()));
            hdrWriter.write("Connection: close\r\n");
            hdrWriter.write("\r\n");
            hdrWriter.flush();

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
    }
}
