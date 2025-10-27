package de.cbfagree.webstart.backend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * Der {@link WriteThroughBuffer} implementiert einen ByteBuffer, welcher in 
 * Chunks organisiert ist. Dies macht den Insert von beliebig langen byte-Arrays 
 * und das sequentielle lesen einfach zu handeln.
 * 
 * Der Inhalt des Buffers wird in einer Liste von Chunks zu jeweils 64kb 
 * verwaltet. Jeder Chunk wird beim write aufgefüllt, wenn der aktuell 
 * Chunk voll ist, so wird ein neuer Chunk angelegt. Auf diese Weise muss bei
 * write()-Operationen nicht ständig ein überlaufender Buffer reallokiert und 
 * kopiert werden.
 * 
 * Der Buffer selbst wird durch einen ReadWriteLock geschützt, es können also 
 * beliebig viele Threads parallel aus dem Buffer lesen. Idealerweise schreibt 
 * nur  ein Thread in den Buffer :-)
 * 
 * Der aktuelle WriteChunk ist erst zum lesen verfügbar, wenn er komplett "voll
 * gelaufen" ist oder der Buffer geschlossen wird.
 */
public class WriteThroughBuffer
{
    /**
     *  Die Größe eines Chunks. Wir verwenden hier erst einmal 64kb. Die meisten 
     *
     * JAR-Files sind recht klein, es gibt aber auch "Ausreißer" mit über 70MB.
     * Je größer die CHunkSize ist, um so länger dauert es bis der erste Chunk
     * zum lesen bereit steht! Muss man also ein wenig tunen...
     */
    private static final int CHUNK_SIZE = 0xffff;

    // Der ReadWrite-Lock welcher den Buffer schützt
    private ReentrantReadWriteLock rwLock;

    // Die Liste aller Chunks
    private List<byte[]> chunks;

    /** 
     * Die Position für den nächsten write innerhalb des aktuellen Write-Chunks.
     * Diese Position ist <b>nicht</b> die writePosition innerhalb des Gesamt-
     * Buffers!
     */
    private int writePos;

    /**
     * Der aktuelle WriteChunk. Dieser ist noch nicht in der Liste der Chunks
     * enthalten, von Ihm kann also auch noch nicht gelesen werden. Der Chunk
     * wird erst beim voll laufen oder beim schließen des Buffers in die 
     * ChunkList aufgenommen.
     */
    private byte[] currWriteChunk;

    /**
     * Wurde der Buffer geschlossen?
     * 
     * closed bedeutet nur, das keine weiteren Bytes mehr angefügt werden können.
     * Lesen ist weiterhin möglich.
     * 
     */
    private boolean isClosed;

    /**
     * lesen und schreiben findet in verschiedenen Threads statt. Sollte
     * beim schreiben ein Fehler auftreten, so bekommt der Reader nichts
     * davon mit.
     * 
     * Aus diesem Grund kann der schreiber eine Exception in den Buffer 
     * stellen, beim lesen wird diese gecheckt. Wenn eine Exception gesetzt 
     * ist, wird diese in eine IOException gewrapped und diese geworfen.
     */
    private AtomicReference<Exception> backendException;

    private int httpStatus;
    private String contentType;

    private String resourceName;

    /**
     * 
     */
    public WriteThroughBuffer(String resourceName)
    {
        this.rwLock = new ReentrantReadWriteLock();
        this.chunks = new ArrayList<>();
        this.writePos = 0;
        this.currWriteChunk = new byte[CHUNK_SIZE];
        this.isClosed = false;
        this.backendException = new AtomicReference<>(null);
        this.httpStatus = 0;
        this.contentType = null;
        this.resourceName = resourceName;
    }

    /**
     * Füge einen Bereich eines Arrays in den ChunkedBuffer ein.
     * 
     * Wenn im aktuellen Chunk noch Platz ist, wird der übergebene
     * Teil des Buffers einfach dorthin kopiert und die writePos weiter 
     * gezählt.
     * 
     * Wenn nicht, werden aus dem übergebenen Buffer die "grade noch passenden"
     * Bytes in den aktuellen Chunk kopiert, ein weiterer Chunk angelegt 
     * und der Rest kommt dort hinein. Die writePos wird derart angepasst,
     * dass die auf die nächste freie Position des aktuellen writeChunks 
     * zeigt.
     * 
     * Dummerweise kann es sein, das der einzufügende Buffer größer als
     * ein Chunk ist. In diesem Fall muss das ganze aufgeteilt, also in 
     * mehrere Chunks verteilt werden.
     * 
     * @param src
     * @param pos
     * @param len
     * 
     * @throws InterruptedException 
     * @throws IOException 
     */
    public void append(byte[] src, int len) throws InterruptedException, IOException
    {
        // TODO: src.length, pos, len gegeneinander checken!

        WriteLock wLock = this.rwLock.writeLock();
        try
        {
            wLock.lockInterruptibly();

            if (this.isClosed)
            {
                throw new IOException("chunked buffer is closed");
            }

            int remaining = len;
            int srcPos = 0;

            while (remaining > 0)
            {
                // Kopiere soviele Bytes in den aktuellen Chunk wie grade 
                // noch hinein passen
                int freeChunkSize = this.currWriteChunk.length - this.writePos;
                if (freeChunkSize > 0)
                {
                    int copyable = Math.min(remaining, freeChunkSize);
                    System.arraycopy(src, srcPos, this.currWriteChunk, this.writePos, copyable);
                    remaining -= copyable;
                    srcPos += copyable;
                    this.writePos += copyable;
                }

                // Wenn noch Bytes zum kopieren übrig sind, so allokiere einen
                // neuen WriteChunk und füge den aktuellen in die Liste ein. 
                // Im nächsten Durchlauf werden diese Bytes in den neuen Chunk kopiert. 
                // Wenn dann noch was  übrig bleibt gint es noch einen durchlauf....
                // ad infinitum oder bis zur OutOfMemeroryException
                if (remaining > 0)
                {
                    this.chunks.add(this.currWriteChunk);
                    this.currWriteChunk = new byte[CHUNK_SIZE];
                    this.writePos = 0;
                }
            }
        }
        finally
        {
            wLock.unlock();
        }
    }

    /**
     * Markiere das EOF auf dem Buffer.
     * 
     * Der letzte Chunk wird auf die aktuelle WritePos gekürzt, so dass keinesfalls
     * über das Dateiende hinaus gelesen werden kann.
     * 
     * @throws InterruptedException 
     * @throws IOException 
     */
    public void close() throws InterruptedException, IOException
    {
        WriteLock wLock = this.rwLock.writeLock();
        try
        {
            wLock.lockInterruptibly();

            if (this.isClosed)
            {
                throw new IOException("chunked buffer already closed");
            }

            // Wenn noch ein aktueller WriteCHunk vorliegt und dieser nicht leer
            // ist, so wird der bis hierhin befüllte Teil des aktuelle WriteChunks
            // in die ChunkListe aufgenommen.
            if (this.writePos > 0)
            {
                this.currWriteChunk = Arrays.copyOf(this.currWriteChunk, this.writePos);
                this.chunks.add(this.currWriteChunk);
            }
            this.isClosed = true;
        }
        finally
        {
            wLock.unlock();
        }
    }

    /**
     * Liefere die aktuelle Gesämt-Länge des Buffers.
     * 
     * Solange der Buffer nicht geschlossen wurde ist dies natürlich eine
     * Moment-Aufnahme, da ja parallel noch geschrieben werden kann.
     * 
     * @return
     * @throws InterruptedException
     */
    public long getTotalLength() throws InterruptedException
    {
        ReadLock rLock = this.rwLock.readLock();
        try
        {
            rLock.lock();
            return (this.chunks.size() - 1) * CHUNK_SIZE + this.writePos;
        }
        finally
        {
            rLock.unlock();
        }
    }

    /**
     * lese von der angegebenen Position in den ZielBuffer. Das ganze ist
     * ein wenig komplizierter:
     * 
     * Da parallel in den Buffer geschrieben werden kann, der lesende
     * Thread aber ggf schneller ist als der schreibende Thread kann es
     * passieren, das beim lesen bereits das aktuelle Buffer-Ende erreicht
     * wird bevor vom schreibenden Thread neue Daten angefügt werden können.
     * 
     * Das ist natürlich noch nicht EOF, blokieren soll das ganze aber auch 
     * nicht. In diesem Fall wird einfach nichts gelesen und somit 0 geliefert.
     * 
     * @param pos die Position, ab der gelesen werden soll
     * 
     * @param target Der ZielBuffer
     * @param targetPos die Position ab der in den ZielBuffer geschrieben werden soll
     * @param int len die maximale Anzahl von Bytes die in den Zielbuffer kopiert werden sollen.
     * 
     * @return die Anzahl gelesener Bytes oder -1 wenn über das Ende
     *         des Buffers hinaus gelesen wurde.
     *         
     * @throws InterruptedException
     * @throws IOException 
     */
    public int getBytes(int pos, byte[] target, int targetPos, int len) throws InterruptedException, IOException
    {
        ReadLock rLock = this.rwLock.readLock();
        try
        {
            rLock.lockInterruptibly();

            Exception backendError = this.backendException.get();
            if (backendError != null)
            {
                throw new IOException("", backendError); // TODO: Message setzen
            }

            if (this.httpStatus != 0 && this.httpStatus >= 300)
            {
                throw new IOException(String.format("HTTP %1$d\r\n%2$s", this.httpStatus, this.resourceName));
            }

            int read = 0;
            int chunkIdx = pos / CHUNK_SIZE;
            if (chunkIdx < this.chunks.size())
            {
                byte[] chunk = this.chunks.get(chunkIdx);
                int chunkOff = pos % CHUNK_SIZE;

                if (chunkOff < chunk.length)
                {
                    read = Math.min((chunk.length - chunkOff), len);
                    System.arraycopy(chunk, chunkOff, target, targetPos, read);
                }
                else
                {
                    if (this.isClosed)
                    {
                        read = -1;
                    }
                }
            }
            return read;
        }
        finally
        {
            rLock.unlock();
        }
    }

    /**
     * 
     * @param e
     */
    public void setBackendException(Exception e)
    {
        this.backendException.set(e);
    }

    public void setStatusCode(int code)
    {
        this.httpStatus = code;
    }

    public int getStatusCode()
    {
        return this.httpStatus;
    }

    public void setContentType(String type)
    {
        this.contentType = type;
    }

    public String getContentType()
    {
        return this.contentType;
    }

    public boolean isReady()
    {
        return this.httpStatus != 0 && this.contentType != null;
    }
}
