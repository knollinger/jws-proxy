package de.cbfagree.webstart.backend;

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
    private LinkedBlockingQueue<DownloadJob> taskQueue;
    private boolean isInShutdown = false;

    /**
     * 
     */
    public DownloaderEngine(BackendConfig cfg)
    {
        this.taskQueue = new LinkedBlockingQueue<DownloadJob>(1000);
        this.startThreads(cfg.getBaseUrl(), cfg.getMaxThreads(), this.taskQueue);
    }

    /**
     * @param task
     * @throws InterruptedException
     */
    public void submit(DownloadJob task) throws InterruptedException
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
     * @param maxThreads
     * @param taskQueue
     */
    private void startThreads(URL baseUrl, int maxThreads, LinkedBlockingQueue<DownloadJob> taskQueue)
    {
        int nrOfThreads = Math.min(maxThreads, Runtime.getRuntime().availableProcessors());
        this.threads = new ArrayList<>(nrOfThreads);

        for (int i = 0; i < nrOfThreads; ++i)
        {
            this.threads.add(new DownloadWorker(baseUrl, taskQueue));
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
