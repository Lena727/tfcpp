package tfcpp.compat.guide;

import java.util.List;

/**
 * The chapter of the TFC++ category about glass: why borosilicate, its batch, the heat it is worked at and the pipe it needs, the retort and the carboy, and the glass bulb.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code BorosilicateGlass} (working point 1250 C, window to 1500 C, brass 930 C), {@code BorosilicateGlassMixin}, the recipes {@code tfcpp:crafting/borosilicate_glass_batch}, {@code tfcpp:glassworking/glass_retort} and {@code glass_carboy}, {@code Distillation.CRACK_TEMPERATURE} (550 C) and the heat of a charcoal forge with a bellows (1600 C, the forge page of this guide). The composition (81% silica, 13% boric oxide, 4% soda) is from the ledger; the expansion of soda-lime glass, about 9 parts in a million a kelvin, against 3.3 for borosilicate, is from the literature.
 */
final class GlassGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(glass());
    }

    private static Entry glass()
    {
        return Entry.of("tfcpp/glass", CATEGORY, "tfcpp:glass_retort", 10, L.of("Borosilicate Glass", "Боросиликатное стекло"))
            .titled(
                L.of("Why borosilicate", "Зачем боросиликат"),
                L.of("Ordinary glass, of sand, lime and potash, cracks when one side is hot and the other cold. For the laboratory Otto Schott made a glass of sand and borax: about 81% silica, 13% boric oxide, 4% soda and almost no lime. It expands a third as much as ordinary glass, so it takes a boiling flask, and acids do not touch it.$(br2)Only this glass makes a retort or a carboy.",
                    "Обычное стекло из песка, извести и поташа лопается, когда одна его сторона горяча, а другая холодна. Для лаборатории Отто Шотт сварил стекло из песка и буры: около 81% кремнезёма, 13% борного оксида, 4% соды и почти без извести. Оно расширяется втрое меньше обычного, поэтому выдерживает кипящую колбу, а кислоты его не берут.$(br2)Только из этого стекла делают реторту и бутыль."))
            .anchor("why")
            .crafting("tfcpp:crafting/borosilicate_glass_batch",
                L.of("One borax flux and two white sand make 4 $(thing)Borosilicate Glass Batch$().",
                    "Один флюс из буры и два белых песка дают 4 $(thing)шихты боросиликатного стекла$()."))
            .showsOnLastPage("tfcpp:borosilicate_glass_batch")
            .anchor("batch")
            .text(
                L.of("Borax gives both the boron and the soda, so the batch has no lime and no potash. $(l:metalworking/flux)Borax flux$() is not the plain flux of the blast furnace, and it does not burn to lime.$(br2)The sand must be white, silica sand. Coloured sand has iron in it, and the glass would not be clear.",
                    "Бура даёт и бор, и соду, поэтому в шихте нет ни извести, ни поташа. $(l:metalworking/flux)Флюс из буры$() это не простой флюс доменной печи, и в известь он не обжигается.$(br2)Песок нужен белый, кремнезёмный. В цветном есть железо, и стекло вышло бы не прозрачным."))
            .titled(
                L.of("The heat and the pipe", "Жар и трубка"),
                L.of("Borosilicate softens at about 820 °C and is worked from 1250 °C up to 1500 °C: hotter than that, TFC takes the glass off the pipe. A charcoal forge at 1100 °C never gets there, and with a $(l:metalworking/bellows)bellows$() it reaches 1600 °C.$(br2)Only a $(thing)ceramic$() blowpipe will serve. Brass melts at 930 °C, and a brass pipe refuses the batch, by hand and in the recipe. Sawing the glass off needs no heat.",
                    "Боросиликат размягчается около 820 °C, а работают его от 1250 °C до 1500 °C: горячее TFC снимает стекло с трубки. Угольная кузница в 1100 °C туда не дойдёт, а с $(l:metalworking/bellows)мехами$() она даёт 1600 °C.$(br2)Подойдёт только $(thing)керамическая$() трубка. Латунь плавится при 930 °C, и латунная трубка не принимает шихту ни рукой, ни в рецепте. Отпилить стекло можно и без жара."))
            .anchor("heat")
            .glassworking("tfcpp:glassworking/glass_retort",
                L.of("The $(thing)Glass Retort$(): blow, stretch, pinch with jacks, saw.",
                    "$(thing)Стеклянная реторта$(): выдуть, вытянуть, защипнуть щипцами, отпилить."))
            .showsOnLastPage("tfcpp:glass_retort")
            .anchor("retort")
            .glassworking("tfcpp:glassworking/glass_carboy",
                L.of("The $(thing)Glass Carboy$(): blow twice, roll with wool cloth, saw.",
                    "$(thing)Стеклянная бутыль$(): выдуть дважды, прокатать шерстяной тканью, отпилить."))
            .showsOnLastPage("tfcpp:glass_carboy")
            .anchor("carboy")
            .titled(
                L.of("What it holds", "Что оно держит"),
                L.of("Borosilicate takes every acid of this book, hot, and a flask that boils. What it will not take is a steep gradient: above 550 °C a retort cracks, whatever is in it. Heat it through a $(l:tfcpp/acids#retort)sand bath$().$(br2)Glass breaks. A retort or a carboy lets out what it holds when it is broken, and an acid splashes. The use of both is in $(l:tfcpp/acids)Acids$().",
                    "Боросиликат выдерживает любую кислоту этой книги, горячую, и кипящую колбу. Не выдержит он крутого перепада: выше 550 °C реторта лопается, что бы в ней ни было. Греть её надо через $(l:tfcpp/acids#retort)песчаную баню$().$(br2)Стекло бьётся. Разбитая реторта или бутыль выпускает своё содержимое, а кислота брызгает. О применении обеих сказано в главе $(l:tfcpp/acids)Кислоты$()."))
            .crafting("tfcpp:crafting/glass_bulb",
                L.of("A $(thing)Glass Bulb$() is a pane of glass. It takes 5 mB of mercury (see $(l:tfcpp/sulfides#mercury)Sulfide Ores$()), for a thermometer or a manometer.",
                    "$(thing)Стеклянная колба$() это стеклянная панель. Она берёт 5 мБ ртути (см. $(l:tfcpp/sulfides#mercury)Сульфидные руды$()) для термометра или манометра."))
            .showsOnLastPage("tfcpp:glass_bulb");
    }

    private GlassGuide() {}
}
