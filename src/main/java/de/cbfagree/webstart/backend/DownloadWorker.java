package de.cbfagree.webstart.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;
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
    private LinkedBlockingQueue<DownloadJob> queue;

    /**
     * @param baseUrl
     * @param queue
     */
    public DownloadWorker(URL baseUrl, LinkedBlockingQueue<DownloadJob> queue)
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
                DownloadJob job = this.queue.take();
                this.doDownload(job);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * @param job
     */
    private void doDownload(DownloadJob job)
    {
        try
        {
            HttpURLConnection conn = this.createDownloadConnection(job.getFileName());

            int statusCode = conn.getResponseCode();
            job.getBuffer().setStatusCode(statusCode);
            job.getBuffer().setContentType(conn.getContentType());

            if (statusCode == 200)
            {
                
                Map<String, List<String>> rspHdrs = conn.getHeaderFields(); // hier den Header draus bosseln?
                
                File tmpFile = File.createTempFile("a21wsproxy", ".tmp");
                try (InputStream in = conn.getInputStream(); //
                    OutputStream fileOut = new FileOutputStream(tmpFile))
                {
                    byte[] buffer = new byte[0xffff];
                    int read = in.read(buffer);
                    while (read != -1)
                    {
                        job.getBuffer().append(buffer, read);
                        fileOut.write(buffer, 0, read);
                        read = in.read(buffer);
                    }
                    job.getBuffer().close();
                    fileOut.close();
                    
                    job.getObserver().downloadCompleted(job.getFileName(), tmpFile);
                }
            }
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
            job.getBuffer().setBackendException(e);
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
}
