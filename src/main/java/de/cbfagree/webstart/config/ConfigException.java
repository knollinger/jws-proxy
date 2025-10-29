package de.cbfagree.webstart.config;

import de.cbfagree.webstart.messages.MsgFactory;

public class ConfigException extends Exception
{
    private static final long serialVersionUID = 1L;

    public ConfigException(EMsgId msgId, Object... args)
    {
        super( //
            MsgFactory.get(ConfigException.class, msgId, args).getFormattedMessage() //
        );
    }
    
    public ConfigException(Throwable t, EMsgId msgId, Object... args)
    {
        super( //
            MsgFactory.get(ConfigException.class, msgId, args).getFormattedMessage(), //
            t //
        );
    }
    
    public enum EMsgId
    {
        ERR_LOAD_CONFIG, //
        
        ERR_NO_BACKEND_URL, //
        ERR_INV_BACKEND_CONN_TO, //
        ERR_INV_BACKEND_READ_TO, //
        ERR_INV_BACKEND_MAX_THREADS, //
        
        ERR_NO_CACHE_BASE, //
        
        ERR_FRONTEND_BAD_PORT, //
        ERR_FRONTEND_BAD_BACKLOG, //
        ERR_FRONTEND_BAD_IO_BUFFER, //
    }
}
