package de.cbfagree.webstart.config;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Dient als JSON-Deserializer für Menschen-lesbare Größen-Angaben.
 * 
 * Statt also so krude Werte wie "8388608" in die Konfiguration zu schreiben
 * reicht auch die Angabe "8MB".
 * 
 * Sollte kein Suffix vorhanden sein, so wird einfach der Wert als solcher
 * verwendet.
 * 
 * Um den Deserializer verwenden zu können, ist das entsprechende 
 * Json-Property zusätzlich folgendermassen zu annotieren:
 * 
 * <pre>
 *  @JsonDeserialize(using = HumanReadableSizeDeserializer.class)
 *  </pre>
 */
public class HumanReadableSizeDeserializer extends StdDeserializer<Integer>
{
    private static final long serialVersionUID = 1L;

    // Die Map der möglichen Suffixe. Als Wert ist der Multiplikator hinterlegt,
    // welcher bei der Berechnung heran gezogen werden soll. 
    private static final Map<String, Integer> suffixToMultiplier = Map.ofEntries(//
        new AbstractMap.SimpleEntry<String, Integer>("", 1), //
        new AbstractMap.SimpleEntry<String, Integer>("b", 1), //
        new AbstractMap.SimpleEntry<String, Integer>("kb", 1024), //
        new AbstractMap.SimpleEntry<String, Integer>("mb", 1024 * 1024), //
        new AbstractMap.SimpleEntry<String, Integer>("gb", 1024 * 1024 * 1024), //
        new AbstractMap.SimpleEntry<String, Integer>("tb", 1024 * 1024 * 1024 * 1024), //
        new AbstractMap.SimpleEntry<String, Integer>("pb", 1024 * 1024 * 1024 * 1024 * 1024) //
    );

    private static final Pattern PARSER_PATTERN = Pattern.compile("([0-9]*)([a-z]*)");

    /**
     * 
     */
    public HumanReadableSizeDeserializer()
    {
        this(null);
    }

    /**
     * @param vc
     */
    public HumanReadableSizeDeserializer(Class<?> vc)
    {
        super(vc);
    }

    /**
     *
     */
    @Override
    public Integer deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException
    {
        JsonNode node = jp.getCodec().readTree(jp);
        String val = node.asText().toLowerCase();

        Matcher m = PARSER_PATTERN.matcher(val);
        m.find();

        String numVal = m.group(1);
        String suffix = m.group(2);

        if (numVal.isBlank())
        {
            throw new JsonParseException(String.format("Der Wert '%1$s' enthält keinen numerischen Anteil", val));
        }

        Integer multiplier = suffixToMultiplier.get(suffix);
        if (multiplier == null)
        {
            throw new JsonParseException(String.format("Der Suffix '%1$s' im Wert '%2$s' ist ungültig", suffix, val));
        }
        return Integer.parseInt(numVal) * multiplier.intValue();
    }
}
