package tfcpp.compat.guide;

import java.util.Locale;

/**
 * A line of the guide in both languages. A page is written once, with its English and Russian text side by side, so that neither language can be forgotten or fall behind the other.
 */
public record L(String en, String ru)
{
    public static final String ENGLISH = "en_us", RUSSIAN = "ru_ru";

    public static L of(String en, String ru)
    {
        return new L(en, ru);
    }

    /** @param language a Minecraft language code, {@code en_us} or {@code ru_ru} */
    public String in(String language)
    {
        return switch (language.toLowerCase(Locale.ROOT))
        {
            case ENGLISH -> en;
            case RUSSIAN -> ru;
            default -> throw new IllegalArgumentException("The guide has no text in " + language);
        };
    }
}
