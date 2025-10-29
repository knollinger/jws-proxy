package de.cbfagree.webstart.frontend;

import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import de.cbfagree.webstart.cache.CacheRepository;
import de.cbfagree.webstart.config.FrontendConfig;

/**
 * 
 */
public class MainSelector implements Runnable
{
    private byte[] ioBuffer;
    private CacheRepository cacheRepo;

    private FrontendConfig config;

    /**
     * @throws IOException
     */
    public MainSelector(FrontendConfig cfg, CacheRepository cacheRepo) throws IOException
    {
        this.config = cfg;
        this.ioBuffer = new byte[cfg.getIoBufferSize()];
        this.cacheRepo = cacheRepo;
    }

    /**
     * Die SelectorLoop
     */
    @Override
    public void run()
    {
        try
        {
            Thread.currentThread().setName("main-selector-thread");
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            ServerSocket socket = serverSocketChannel.socket();
            socket.bind(new InetSocketAddress(this.config.getPort()), this.config.getBacklog());
            socket.setReceiveBufferSize(this.config.getIoBufferSize());
            serverSocketChannel.configureBlocking(false);

            Selector selector = Selector.open();
            SelectionKey key = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (!Thread.currentThread().isInterrupted())
            {
                if (selector.select() > 0)
                {

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    while (keyIterator.hasNext())
                    {
                        key = keyIterator.next();
                        keyIterator.remove();

                        if (key.isAcceptable())
                        {
                            this.handleIncommingConnection(key);
                        }
                        else
                        {
                            if (key.isReadable())
                            {
                                this.handleIncommingData(key);
                            }
                            else
                            {
                                if (key.isWritable())
                                {
                                    this.handleWritableChannel(key);
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Behandle eine neu hereinkommende Verbindung.
     * 
     * Zuerst müssen wir den HTTPRequestHeader lesen, also setzen wir
     * die InterestMap auf OP_READ.
     * 
     * Desweiteren wird mit dem SelectorKey ein neuer {@link ChannelTransferContext}
     * assoziert. Dadurch können wir Status-Informationen zwischen den
     * asynchronen lese/schreib-Informationen an diesem Channel halten.
     * 
     * @param key
     */
    private void handleIncommingConnection(SelectionKey key)
    {
        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
        try
        {
            SocketChannel newChannel = channel.accept();
            newChannel.configureBlocking(false);
            SelectionKey newKey = newChannel.register(key.selector(), SelectionKey.OP_READ);

            SocketAddress remote = newChannel.socket().getRemoteSocketAddress();
            newKey.attach(new ChannelTransferContext(remote));
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Auf einer Verbindung sind Daten herein gekommen. Wir benutzen hier einen
     * gemeinsammen ReadBuffer über alle Channels, einfach um Speicherplatz
     * zu sparen. Die Channels werden ohnehin sequentiell verarbeitet.
     * 
     * Wir übertragen die empfangenen Daten in den TransferContext. Sollte
     * damit ein kompletter HTTP-Header empfangen sein, so wechseln wir das
     * InterestSet auf Write.
     * 
     * Dadurch werden ab dem nächsten Durchlauf des Selectors der Download der 
     * Daten ausgelöst.
     * 
     * @param key
     */
    private void handleIncommingData(SelectionKey key)
    {
        try
        {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer byteBuf = ByteBuffer.wrap(this.ioBuffer);

            int read = channel.read(byteBuf);
            if (read == -1)
            {
                // TODO: unexpected eof!
                channel.socket().close();
                key.cancel();
            }
            else
            {
                if (read > 0)
                {
                    byteBuf.flip();

                    ChannelTransferContext ctx = (ChannelTransferContext) key.attachment();
                    if (ctx.appendRequestData(byteBuf.array(), read))
                    {
                        String resName = ctx.getRequestHeader().getUrl();
                        PushbackInputStream pushbackStream = new PushbackInputStream( //
                            this.cacheRepo.getResource(resName), //
                            this.config.getIoBufferSize());

                        ctx.setDataSrc(pushbackStream);
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                }
            }
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }

    }

    /**
     * Der Channel ist bereit zum schreiben.
     * 
     * Wir lesen aus dem SourceStream des TransferContextes in den gemeinsammen
     * writeBuffer. 
     * 
     * Sollte dabei EOF des SourceStreams erkannt werden, dann wird der Channel 
     * geschlossen und aus dem Selector entfern.
     * 
     * Sollten aktuell keine Daten am SourceStream anliegen, so wird nichts 
     * gemacht.
     * 
     * Sollten weniger Bytes geschrieben werden können als gelesen wurden, so
     * werden die "restlichen" Daten in den SourceStream zurück gepushed.
     * 
     * @param key
     */
    private void handleWritableChannel(SelectionKey key)
    {
        SocketChannel channel = (SocketChannel) key.channel();
        try
        {
            ChannelTransferContext ctx = (ChannelTransferContext) key.attachment();
            PushbackInputStream in = ctx.getDataSrc();

            int read = in.read(this.ioBuffer);
            switch (read)
            {
                case -1 :
                    channel.socket().close();
                    key.cancel();
                    in.close();
                    break;

                case 0 :
                    break;

                default :
                    ByteBuffer buf = ByteBuffer.wrap(this.ioBuffer, 0, read);
                    int written = channel.write(buf);
                    if (read > written)
                    {
                        in.unread(ioBuffer, written, read - written);
                    }
                    break;
            }
        }
        catch (IOException e)
        {
            try
            {
                e.printStackTrace();
                key.cancel();
                channel.socket().close();
            }
            catch (IOException ex)
            {

            }
        }
    }
}
