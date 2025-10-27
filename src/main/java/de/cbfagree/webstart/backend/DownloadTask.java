package de.cbfagree.webstart.backend;

/**
 * Beschreibt einen Download-Auftrag.
 * 
 * Da es sich um ein immutable ValueObject handelt ist ein record grade richtig.
 */
public record DownloadTask(//
    String fileName, //
    WriteThroughBuffer buffer, //
    IDownloadObserver observer)
{

}
