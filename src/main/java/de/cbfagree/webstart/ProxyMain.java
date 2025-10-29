package de.cbfagree.webstart;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

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
     * @throws ParseException 
     */
    public static void main(String[] args) throws ParseException
    {
        CommandLine cmdLine = ProxyMain.parseCommandLine(args);
        String cfgFilePath = cmdLine.getOptionValue("cfgFile");

        File cfgFile = new File(cfgFilePath);
        new ProxyMain().run(cfgFile);
    }

    private static CommandLine parseCommandLine(String[] args) throws ParseException
    {
        CommandLineParser cmdLineParser = new DefaultParser();

        Options opts = new Options();
        
        opts.addOption(Option.builder("c") //
            .longOpt("cfgFile") //
            .hasArg(true) //
            .desc("Der Pfad zum Konfigurations-File. Die Angabe ist verpflichten, oder verwende '--cfgFile'") //
            .required(true) //
            .build());
        
        opts.addOption(Option.builder("i") //
            .longOpt("import") //
            .hasArg(true) //
            .desc("Der Pfad zum JNLP-File für den Cache-Preload. Die Angabe ist optional, die Lang-Form '--import' ist ebenfalls möglich") //
            .required(false) //
            .build());
        
        return cmdLineParser.parse(opts, args);
    }

    /**
     * 
     */
    private static enum EMsgIds
    {
        INF_START_WITH_CFG, //
        ERR_START_PROXY
    }
}
