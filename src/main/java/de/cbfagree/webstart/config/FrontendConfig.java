package de.cbfagree.webstart.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.cbfagree.webstart.config.ConfigException.EMsgId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

@Getter(AccessLevel.PUBLIC)
@ToString
public class FrontendConfig
{
    @JsonProperty("port")
    private int port = 9601;

    @JsonProperty("backlog")
    private int backlog = 200;

    @JsonProperty("recvBufferSize")
    @JsonDeserialize(using = HumanReadableSizeDeserializer.class)
    private int recvBufferSize = 0xFFFF;

    @JsonProperty("sendBufferSize")
    @JsonDeserialize(using = HumanReadableSizeDeserializer.class)
    private int sendBufferSize = 0xFFFF;

    public void validate() throws ConfigException
    {

        if (this.port <= 0 || this.port > 65535)
        {
            throw new ConfigException(EMsgId.ERR_FRONTEND_BAD_PORT, this.port);
        }

        if (this.backlog < 1)
        {
            throw new ConfigException(EMsgId.ERR_FRONTEND_BAD_BACKLOG, this.backlog);
        }

        if (this.recvBufferSize < 1)
        {
            throw new ConfigException(EMsgId.ERR_FRONTEND_BAD_RECV_BUFFER, this.recvBufferSize);
        }

        if (this.sendBufferSize < 1)
        {
            throw new ConfigException(EMsgId.ERR_FRONTEND_BAD_SEND_BUFFER, this.sendBufferSize);
        }
    }
}
