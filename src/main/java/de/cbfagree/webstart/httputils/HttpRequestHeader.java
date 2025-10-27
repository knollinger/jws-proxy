package de.cbfagree.webstart.httputils;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter(AccessLevel.PUBLIC)
@Setter(AccessLevel.NONE)
@Builder()
@ToString()
public class HttpRequestHeader
{
    private String method;
    private String url;
    private String version;
}
