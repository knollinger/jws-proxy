package de.cbfagree.webstart.config;

import java.net.URL;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.cbfagree.webstart.config.ConfigException.EMsgId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

/**
 * Das Config-Objekt für die Backend-Connection
 */
@Getter(AccessLevel.PUBLIC)
@ToString
public class BackendConfig
{
    @JsonProperty("baseUrl")
    private URL baseUrl;

    @JsonProperty("connTimeout")
    private int connTimeout = 10000;

    @JsonProperty("readTimeout")
    private int readTimeout = 1000;

    @JsonProperty("maxThreads")
    private int maxThreads = 4;

    @JsonProperty("httpProxy")
    private String httpProxy = "NONE";

    /**
     * validiere das Konfigurations-Objekt.
     * 
     * @throws ConfigException wenn die Config einen ungültigen Wert enthält
     */
    public void validate() throws ConfigException
    {
        if (this.baseUrl == null)
        {
            throw new ConfigException(EMsgId.ERR_NO_BACKEND_URL);
        }

        if (this.connTimeout <= 0)
        {
            throw new ConfigException(EMsgId.ERR_INV_BACKEND_CONN_TO, this.connTimeout);
        }

        if (this.readTimeout <= 0)
        {
            throw new ConfigException(EMsgId.ERR_INV_BACKEND_READ_TO, this.readTimeout);
        }

        if (this.maxThreads < 1)
        {
            throw new ConfigException(EMsgId.ERR_INV_BACKEND_MAX_THREADS, this.maxThreads);
        }

        // TODO: httpProxy validieren
    }
}
