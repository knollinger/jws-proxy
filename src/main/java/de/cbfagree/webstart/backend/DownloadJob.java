package de.cbfagree.webstart.backend;

public class DownloadJob
{
    private String fileName;
    private WriteThroughBuffer buffer;
    private DownloadObserver observer;

    public DownloadJob(String fileName, WriteThroughBuffer targetBuffer, DownloadObserver observer)
    {
        this.fileName = fileName;
        this.buffer = targetBuffer;
        this.observer = observer;
    }

    public String getFileName()
    {
        return fileName;
    }

    public WriteThroughBuffer getBuffer()
    {
        return buffer;
    }

    public DownloadObserver getObserver()
    {
        return observer;
    }
}
