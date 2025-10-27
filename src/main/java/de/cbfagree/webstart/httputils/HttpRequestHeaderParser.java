package de.cbfagree.webstart.httputils;

import de.cbfagree.webstart.httputils.HttpRequestHeader.HttpRequestHeaderBuilder;

public class HttpRequestHeaderParser
{
    public HttpRequestHeader parse(byte[] buffer, int len)
    {
        String[] lines = new String(buffer, 0, len).split("\r\n\r\n"); // CharSet US-ASCII?
        if (lines.length == 0)
        {
            // TODO: throw something
        }

        HttpRequestHeaderBuilder builder = HttpRequestHeader.builder();
        this.parseHeaderLine(lines[0], builder);
        
        return builder.build();
    }

    /**
     * parse die RequestLine
     * 
     * @param line
     * @param builder
     */
    private void parseHeaderLine(String line, HttpRequestHeaderBuilder builder)
    {
        String[] parts = line.split(" ");
        builder //
            .method(parts[0].toUpperCase()) //
            .url(parts[1].toLowerCase()) //
            .version(parts[2].toUpperCase());
    }
}
