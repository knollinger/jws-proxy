package de.cbfagree.webstart.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

@Getter(AccessLevel.PUBLIC)
@ToString
public class Config
{
    @JsonProperty("backend")
    private BackendConfig backend;

    @JsonProperty("frontend")
    private FrontendConfig frontend;

    @JsonProperty("cache")
    private CacheConfig cache;

    public void validate() throws ConfigException
    {
        this.backend.validate();
        this.frontend.validate();
        this.cache.validate();
    }
}
