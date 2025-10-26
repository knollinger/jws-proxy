package de.cbfagree.webstart.frontend;

import java.io.PushbackInputStream;
import java.net.SocketAddress;

import de.cbfagree.webstart.messages.MsgFactory;
import lombok.extern.log4j.Log4j2;

/**
 * Der {@link TransferContext} hält alle Status-Informationen für einen
 * ProxyTransfer.
 * 
 * Über die Lebenszeit einer Verbindung wechselt diese zwischen verschieden
 * Zuständen. Zuerst wird eine neue Verbindung akzeptiert, danach wechselt 
 * sie sofort in den Zustand "lesend". Sobald der HttpHeader empfangen wurde,
 * wechselt die Connection in den Zustand "schreibend".
 * 
 * Diese Zustands-Übergänge werden einfach durch das komplette empfangen eines
 * HttpHeaders gesteuert. Der MainSelector liest Daten vom ClientChannel, solange
 * noch kein kompletter HTTPHeader empfangen wurde. Die empfangenen Daten pumpt 
 * er via {@link TransferContext#appendRequestData(byte[], int)} in den Context.
 * 
 * Sobald der Context "got it!" meldet, schaltet der {@link MainSelector} das
 * InterestiungSet des SelectorKey für den Channel auf OP_WRITE.
 * 
 * Der MainSelector 
 * 
 */
@Log4j2
class TransferContext
{
    private static final int INITIAL_RECV_BUFFER_SIZE = 8 * 1024;

    private SocketAddress remoteAddress;
    private byte[] recvBuffer = new byte[INITIAL_RECV_BUFFER_SIZE];
    private int recvBufferWritePos = 0;

    private HttpRequestHeader reqHeader;
    private PushbackInputStream resource;

    /**
     * 
     * @param remote
     */
    public TransferContext(SocketAddress remote)
    {
        this.remoteAddress = remote;
        log.debug(MsgFactory.get(this.getClass(), EMsgIds.CREATE_CONTEXT, remote));
    }

    /**
     * Füge Daten an den RequestBuffer an.
     * 
     * Wenn der RequestBuffer voll ist, so wird er mit mindestens der doppelten
     * Kapazität realloziert. Falls das für die Menge der neuen Daten nicht reicht,
     * so wird er um die aktuelle Größe + Länge der neuen Daten reallokiert.
     * 
     * Es wird geprüft, ob bereits ein kompletter HTTPRequestHeader empfangen wurde.
     * Sollte dies der Fall sein, so werden weitere appends ignoriert und der 
     * HttpRequestHeader geparsed und in den TransferContext gesetzt.
     * 
     * @param array
     * @param len
     * 
     * @return <code>true</code> wenn ein kompletter HTTPRequestHeader empfangen wurde.
     */
    public boolean appendRequestData(byte[] array, int len)
    {
        boolean result = false;
        if (this.recvBuffer != null)
        {
            int remainingCapacity = this.recvBuffer.length - this.recvBufferWritePos;
            if (remainingCapacity < len)
            {
                int newCapacity = Math.max(this.recvBuffer.length * 2, len + remainingCapacity);
                byte[] newBuffer = new byte[newCapacity];
                System.arraycopy(this.recvBuffer, 0, newBuffer, 0, this.recvBufferWritePos);
                this.recvBuffer = newBuffer;
            }
            System.arraycopy(array, 0, this.recvBuffer, this.recvBufferWritePos, len);
            this.recvBufferWritePos += len;

            result = this.isHttpRequestHeaderComplete();
            if (result)
            {
                this.reqHeader = new HttpRequestHeaderParser().parse(this.recvBuffer, this.recvBufferWritePos);
                this.recvBuffer = null;
                log.debug(
                    MsgFactory.get(this.getClass(), EMsgIds.REQ_HDR_COMPLETED, this.remoteAddress, this.reqHeader));
            }
        }
        return result;
    }

    /**
     * @return
     */
    public HttpRequestHeader getRequestHeader()
    {
        return this.reqHeader;
    }

    /**
     * Wurde der HTTP-RequestHeader komplett gelesen? Dies wird durch
     * die Existenz einer "\r\n\r\n"-Sequenz definiert.
     * 
     * Wir durchsuchen das ByteArray von hinten her. Der Delimiter 
     * (falls vorhanden) steht am Ende des Buffers! 
     * 
     * Einfach nur die letzten 4 Byte zu testen funktioniert leider nicht,
     * gegebenenfalls wird illegalerweise noch ein Body gesendet. Den treten 
     * wir zwar in die Tonne, er würde aber das Scan-Ergebniss beeinflussen.
     * 
     * @return
     */
    private boolean isHttpRequestHeaderComplete()
    {
        boolean result = false;
        for (int pos = this.recvBufferWritePos - 4; !result && pos >= 0; --pos)
        {
            result = (this.recvBuffer[pos + 0] == '\r') //
                && (this.recvBuffer[pos + 1] == '\n') //
                && (this.recvBuffer[pos + 2] == '\r') //
                && (this.recvBuffer[pos + 3] == '\n');
        }
        return result;
    }

    private static enum EMsgIds
    {
        CREATE_CONTEXT, //
        REQ_HDR_COMPLETED, //
    }

    public void setDataSrc(PushbackInputStream resource)
    {
        this.resource = resource;
    }

    public PushbackInputStream getDataSrc()
    {
        return this.resource;
    }
}
