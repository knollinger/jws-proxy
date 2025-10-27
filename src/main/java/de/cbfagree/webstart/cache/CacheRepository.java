package de.cbfagree.webstart.cache;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.message.Message;

import de.cbfagree.webstart.backend.DownloadJob;
import de.cbfagree.webstart.backend.DownloadObserver;
import de.cbfagree.webstart.backend.DownloaderEngine;
import de.cbfagree.webstart.backend.PendingDownloadInputStream;
import de.cbfagree.webstart.backend.WriteThroughBuffer;
import de.cbfagree.webstart.config.CacheConfig;
import de.cbfagree.webstart.messages.MsgFactory;
import lombok.extern.log4j.Log4j2;

@Log4j2()
public class CacheRepository implements DownloadObserver
{
    private File cacheBaseDir;
    private DownloaderEngine engine;
    private ConcurrentHashMap<String, InputStreamFactory> repo;

    /**
     * 
     * @param cacheBaseDir
     * @throws IOException 
     */
    public CacheRepository(CacheConfig cfg, DownloaderEngine downloader) throws IOException
    {
        this.repo = new ConcurrentHashMap<>();
        this.engine = downloader;
        this.cacheBaseDir = cfg.getBasePath().getAbsoluteFile();
        if (!this.cacheBaseDir.exists())
        {
            this.createCacheDirectory();
        }
        else
        {
            this.fillFromFileSystem();
        }
    }

    /**
     * @throws IOException 
     * 
     */
    private void createCacheDirectory() throws IOException
    {

        log.info(MsgFactory.get(this.getClass(), EMsgIds.CACHE_DIR_NOT_EXISTS, this.cacheBaseDir.getAbsolutePath()));
        if (!this.cacheBaseDir.mkdirs())
        {
            Message msg = MsgFactory.get(this.getClass(), EMsgIds.UNABLE_TO_CREATE_CACHE,
                this.cacheBaseDir.getAbsolutePath());
            log.error(msg);
            throw new IOException(msg.getFormattedMessage());
        }
    }

    /**
     * Befülle das Repo mit allen Dateien, welche bereits im Filesystem-
     * Cache enthalten sind. Für diese Resourcen werden FileInputStreamFactories
     * im Repo hinterlegt.
     * 
     * TODO: auch die SubDirs traversieren und deren Einträge unter dem subPath-Name 
     * im Cache verfügbar machen
     */
    private void fillFromFileSystem()
    {
        log.info(MsgFactory.get(this.getClass(), EMsgIds.FILL_REPO, this.cacheBaseDir.getAbsolutePath()));
        for (File file : this.cacheBaseDir.listFiles())
        {
            String fileName = file.getName();
            if (!file.isFile())
            {
                log.info(MsgFactory.get(this.getClass(), EMsgIds.IGNORE_CACHE_ENTRY, fileName));
            }
            else
            {
                if (fileName.endsWith(".cache"))
                {
                    log.info( MsgFactory.get(this.getClass(), EMsgIds.USE_CACHE_ENTRY, fileName));
                    this.repo.put("/" + fileName, new CachedEntryInputStreamFactory(file));
                }
            }
        }

        log.info(MsgFactory.get(this.getClass(), EMsgIds.REPO_SIZE, this.repo.size()));
    }

    /**
     * Liefere einen neuen Inputstream auf die angeforderte Resource.
     * 
     * Wenn die angeforderte Resource bereits im Repo gefunden wurde,
     * so wird ein FileInputStream geliefert.
     * 
     * Wenn die angeforderte Resource noch nicht im Repo existiert, so
     * wird ein ChunkedBuffer alloziert, der Download der Resource 
     * in diesen ChunkedBuffer asynchron gestartet und ein InputStream 
     * auf den ChunkedBuffer geliefert.
     * 
     * @param resourceName
     * @return niemals <code>null</code>
     * 
     * @throws IOException
     * @throws InterruptedException 
     */
    public InputStream getResource(String resourceName) throws IOException, InterruptedException
    {
        log.debug(MsgFactory.get(this.getClass(), EMsgIds.GET_RESOURCE, resourceName));
        InputStreamFactory fact = this.repo.get(resourceName);
        if (fact != null)
        {
            log.debug(MsgFactory.get(this.getClass(), EMsgIds.RESOURCE_FOUND, resourceName));
        }
        else
        {
            log.debug(MsgFactory.get(this.getClass(), EMsgIds.RESOURCE_NOT_FOUND, resourceName));

            // noch nicht im Repo gefunden, also alles für den Download
            // vorbereiten
            WriteThroughBuffer buffer = new WriteThroughBuffer(resourceName);
            PendingDownloadInputStreamFactory bufFact = new PendingDownloadInputStreamFactory(buffer);

            // Und versuchen in das Repo einzufügen. Sollte ein paralleler
            // Consumer-Thread schneller gewesen sein, so war die Vorbereitung halt
            // für die Katz.
            InputStreamFactory currentFact = this.repo.putIfAbsent(resourceName, bufFact);
            if (currentFact != null)
            {
                log.debug(MsgFactory.get(this.getClass(), EMsgIds.PENDING_DOWNLOAD, resourceName));
                fact = currentFact;
            }
            else
            {
                log.debug(MsgFactory.get(this.getClass(), EMsgIds.DOWNLOAD_INITATED, resourceName));
                fact = bufFact;
                DownloadJob downloadTask = new DownloadJob(resourceName, buffer, this);
                this.engine.submit(downloadTask);
            }
        }
        return fact.createInputStream();
    }

    /**
     * Ein asynchroner Download ist komplett.
     */
    @Override
    public void downloadCompleted(String resourceName, File file)
    {
        try
        {
            log.debug(MsgFactory.get(this.getClass(), EMsgIds.DOWNLOAD_COMPLETED, resourceName));

            Path srcPath = Path.of(file.getAbsolutePath());
            Path targetPath = Path.of(this.cacheBaseDir.getAbsolutePath(), resourceName + ".cache");

            Path parentDir = targetPath.getParent();
            if (parentDir != null)
            {
                if (!parentDir.toFile().mkdirs())
                {
                    // TODO: throw something
                }
            }
            Files.move(srcPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            this.repo.put(resourceName, new CachedEntryInputStreamFactory(targetPath.toFile()));
        }
        catch (Exception e)
        {
            log.error(MsgFactory.get(this.getClass(), EMsgIds.ERR_TRANSFER_TO_REPO, resourceName, e));
        }
    }

    /**
     * Das CacheRepo verwaltet für jede Resource eine Daten-Quelle. Wenn die
     * Resource bereits auf Platte liegt, so kann sie direkt verwendet werden.
     * 
     * Wenn die Resource erst vom Parent geladen wird, so muss eine DatenQuelle
     * mit "write-through" Eigenschafften verwendet werden. Die Download-
     * Engine schreibt dann "von hinten" neu ankommende Daten hinein wärend
     * "vorn" beliebig viele Consumer lesen können.
     * 
     * Um diese Unterschiede zu abstrahieren, werden im Repo also nur Factories
     * für diese Datenquellen verwendet.
     */
    public interface InputStreamFactory
    {
        public InputStream createInputStream() throws IOException;
    }

    /**
     * Eine {@link InputStreamFactory}, welches einen InputStream für einen
     * {@link WriteThroughBuffer} erzeugt.
     */
    private static class PendingDownloadInputStreamFactory implements InputStreamFactory
    {
        private WriteThroughBuffer buffer;

        /**
         * @param buffer
         */
        public PendingDownloadInputStreamFactory(WriteThroughBuffer buffer)
        {
            this.buffer = buffer;
        }

        /**
         *
         */
        @Override
        public InputStream createInputStream() throws IOException
        {
            return new PendingDownloadInputStream(this.buffer);
        }
    }

    /**
     * Erzeugt einen InputStream für ein im Cache befindliches File
     * 
     * Der Stream besteht aus dem HTTP-Header, gefolgt vom Content des Files.
     * Im HTTP-Header werden folgende Werte gesetzt:
     * 
     * <ul>
     * <li>Content-Type: application/octedstream
     * <li>Content-Length: &lt;Länge des Files&gt;
     * </ul>
     */
    private static class CachedEntryInputStreamFactory implements InputStreamFactory
    {
        private File file;

        /**
         * @param file
         */
        public CachedEntryInputStreamFactory(File file)
        {
            this.file = file;
        }

        /**
         * Erzeuge den Stream
         */
        @Override
        public InputStream createInputStream() throws IOException
        {
            return new BufferedInputStream(new FileInputStream(this.file));
        }
    }

    /**
     * 
     */
    private enum EMsgIds
    {
        CACHE_DIR_NOT_EXISTS, //
        UNABLE_TO_CREATE_CACHE, //
        FILL_REPO, //
        IGNORE_CACHE_ENTRY, //
        USE_CACHE_ENTRY, //     
        REPO_SIZE, //
        GET_RESOURCE, //
        RESOURCE_FOUND, //
        RESOURCE_NOT_FOUND, //
        PENDING_DOWNLOAD, //
        DOWNLOAD_INITATED, //
        DOWNLOAD_COMPLETED, //
        ERR_TRANSFER_TO_REPO, //

    }
}
