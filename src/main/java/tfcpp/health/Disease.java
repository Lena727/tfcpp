package tfcpp.health;

/** The two diseases of dirty water that v1.5 knows. Free of Minecraft types. */
public enum Disease
{
    /** Shigellosis: a short, violent diarrhoea that dries a person out. */
    DYSENTERY,
    /** Typhoid fever (Salmonella Typhi): a long fever, which killed one in ten to one in five before antibiotics. */
    TYPHOID;

    public static final Disease[] VALUES = values();

    public String id()
    {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
