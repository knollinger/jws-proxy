package de.cbfagree.webstart.backend;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import de.cbfagree.webstart.config.BackendConfig;

/**
 * 
 */
public class DownloaderEngine
{
    private List<DownloadWorker> threads;
    private LinkedBlockingQueue<DownloadTask> taskQueue;
    private boolean isInShutdown = false;

    /**
     * 
     */
    public DownloaderEngine(BackendConfig cfg)
    {
        Proxy httpProxy = this.setupDownloadProxy(cfg);
        this.taskQueue = new LinkedBlockingQueue<DownloadTask>(1000);
        this.startThreads(cfg.getBaseUrl(), httpProxy, cfg.getMaxThreads(), this.taskQueue);
    }

    /**
     * konfiguriere einen Download-Proxy.
     * 
     * Das ist in Produktion <b>nicht</b> notwendig. Dort zeigt ja die baseUrl
     * entweder auf einen Parent-Proxy oder auf das Download-Portal. Für lokale
     * Tests im Campus mit irgendeiner URL ist es aber ggf. notwendig...
     * 
     * @param cfg
     * @return
     */
    private Proxy setupDownloadProxy(BackendConfig cfg)
    {
        Proxy result = Proxy.NO_PROXY;
        String proxyAdress = cfg.getHttpProxy();
        if (proxyAdress != null && !proxyAdress.toUpperCase().equalsIgnoreCase("NONE"))
        {
            String[] parts = proxyAdress.split(":");
            result = new Proxy(Proxy.Type.HTTP,  new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
        }
        return result;
    }

    /**
     * @param task
     * @throws InterruptedException
     */
    public void submit(DownloadTask task) throws InterruptedException
    {
        if (this.isInShutdown)
        {
            // TODO Exception werfen!
        }
        this.taskQueue.put(task);
    }

    /**
     * 
     */
    public void shutdown()
    {
        if (!this.isInShutdown)
        {
            this.isInShutdown = true;
            for (DownloadWorker worker : this.threads)
            {
                this.shutdownWorker(worker);
            }
        }
    }

    /**
     * @param baseUrl
     * @param httpProxy 
     * @param maxThreads
     * @param taskQueue
     */
    private void startThreads(URL baseUrl, Proxy httpProxy, int maxThreads, LinkedBlockingQueue<DownloadTask> taskQueue)
    {
        int nrOfThreads = Math.min(maxThreads, Runtime.getRuntime().availableProcessors());
        this.threads = new ArrayList<>(nrOfThreads);

        for (int i = 0; i < nrOfThreads; ++i)
        {
            this.threads.add(new DownloadWorker(baseUrl, httpProxy, taskQueue));
        }
    }

    /**
     * @param worker
     */
    private void shutdownWorker(DownloadWorker worker)
    {
        try
        {
            worker.interrupt();
            worker.join(30000);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
