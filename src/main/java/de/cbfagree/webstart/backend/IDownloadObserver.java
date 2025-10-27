package de.cbfagree.webstart.backend;

import java.io.File;

/**
 * Die Download-Worker benachrichtigen einen {@link IDownloadObserver}
 * wenn der Status des DownloadTasks sich ändert.
 */
public interface IDownloadObserver
{
    public void downloadCompleted(String string, File file);
}
