package de.cbfagree.webstart.frontend;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter(AccessLevel.PUBLIC)
@Setter(AccessLevel.NONE)
@Builder()
@ToString()
class HttpRequestHeader
{
    private String method;
    private String url;
    private String version;
}
