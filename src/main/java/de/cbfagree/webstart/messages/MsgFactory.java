package de.cbfagree.webstart.messages;

import java.util.ResourceBundle;

import org.apache.logging.log4j.message.LocalizedMessage;
import org.apache.logging.log4j.message.Message;

/**
 * Erzeuge Log4j2-Messages anhand eines ResourceBundles, einem IdEnum 
 * und einer Liste optionaler Parameter
 * 
 */
public class MsgFactory
{
    /**
     * @param clazz
     * @param key
     * @param args
     * @return
     */
    public static Message get(Class<?> clazz, Enum<?> key, Object... args)
    {
        ResourceBundle bundle = ResourceBundle.getBundle(clazz.getName());
        return new LocalizedMessage(bundle, key.name(), args);
    }
}
