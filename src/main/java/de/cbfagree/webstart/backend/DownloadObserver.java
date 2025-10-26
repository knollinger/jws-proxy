package de.cbfagree.webstart.backend;

import java.io.File;

public interface DownloadObserver
{
    public void downloadCompleted(String string, File file);
}
