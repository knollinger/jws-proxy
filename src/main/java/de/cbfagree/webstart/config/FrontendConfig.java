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

    @JsonProperty("ioBufferSize")
    @JsonDeserialize(using = HumanReadableSizeDeserializer.class)
    private int ioBufferSize = 0xFFFF;

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

        if (this.ioBufferSize < 1)
        {
            throw new ConfigException(EMsgId.ERR_FRONTEND_BAD_IO_BUFFER, this.ioBufferSize);
        }
    }
}
