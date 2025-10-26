package de.cbfagree.webstart.config;

import java.io.File;

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
public class CacheConfig
{
    @JsonProperty("basePath")
    private File basePath;

    /**
     * @throws ConfigException
     */
    public void validate() throws ConfigException
    {
        if (this.basePath == null)
        {
            throw new ConfigException(EMsgId.ERR_NO_CACHE_BASE);
        }

    }
}
