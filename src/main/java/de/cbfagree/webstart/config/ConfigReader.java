package de.cbfagree.webstart.config;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.cbfagree.webstart.config.ConfigException.EMsgId;

public class ConfigReader
{
    /**
     * 
     * @param cfgFile
     * @return
     * @throws ConfigException
     */
    public Config readConfig(File cfgFile) throws ConfigException
    {
        try
        {
            ObjectMapper objectMapper = JsonMapper.builder() //
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS) //
                .build();
            
            Config cfg = objectMapper.readValue(cfgFile, Config.class);
            cfg.validate();
            
            return cfg;
        }
        catch (IOException e)
        {
            throw new ConfigException(e, EMsgId.ERR_LOAD_CONFIG, cfgFile);
        }
    }
}
