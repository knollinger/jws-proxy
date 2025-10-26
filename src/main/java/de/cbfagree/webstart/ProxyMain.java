package de.cbfagree.webstart;

import java.io.File;
import java.io.IOException;

import de.cbfagree.webstart.backend.DownloaderEngine;
import de.cbfagree.webstart.cache.CacheRepository;
import de.cbfagree.webstart.config.Config;
import de.cbfagree.webstart.config.ConfigException;
import de.cbfagree.webstart.config.ConfigReader;
import de.cbfagree.webstart.frontend.MainSelector;
import de.cbfagree.webstart.messages.MsgFactory;
import lombok.extern.log4j.Log4j2;

/**
 * Die Haupt-Klasse des Webstart-Proxies.
 * 
 * Im wesentlichen wird die Konfiguration gelesen, alle SubSysteme hoch gezogen
 * und der MainSelector gestartet.
 * 
 */
@Log4j2
public class ProxyMain
{
    /**
     * 
     */
    private void run(File cfgFile)
    {
        try
        {
            Config cfg = new ConfigReader().readConfig(cfgFile);
            log.info(MsgFactory.get(this.getClass(), EMsgIds.INF_START_WITH_CFG, cfg));

            DownloaderEngine downloadEngine = new DownloaderEngine(cfg.getBackend());
            CacheRepository cacheRepo = new CacheRepository(cfg.getCache(), downloadEngine);
            MainSelector mainSelector = new MainSelector(cfg.getFrontend(), cacheRepo);

            mainSelector.run();
        }
        catch (ConfigException | IOException e)
        {
            e.printStackTrace();
            log.error(MsgFactory.get(ProxyMain.class, EMsgIds.ERR_START_PROXY, e));
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        if (args.length < 1)
        {
            log.error(MsgFactory.get(ProxyMain.class, EMsgIds.ERR_NO_CFG_FILE));
        }
        else
        {
            File cfgFile = new File(args[0]);
            new ProxyMain().run(cfgFile);
        }
    }

    /**
     * 
     */
    private static enum EMsgIds
    {
        ERR_NO_CFG_FILE, //
        INF_START_WITH_CFG, //
        ERR_START_PROXY
    }
}
