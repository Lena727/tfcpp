package tfcpp.compat.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * The category TFC++ adds to TFC's field guide. It holds what TFC has no chapter for: gases and ventilation, poisons, mine safety, sulfide ores and the retort, iron and phosphorus, pipes and pressure, coal chemistry, lead and silver, acids, glass and the tests of poisons. Each chapter says what is needed, how it is done, why it works so in reality and what is dangerous.
 * <p>
 * Chapters to write are listed, with the facts each must hold, in {@code docs/guide-audit.md}. Entries belong to {@code tfc:tfcpp} and lie in {@code entries/tfcpp/}.
 */
final class TfcppGuide
{
    static final String CATEGORY = "tfc:tfcpp";

    static List<Category> categories()
    {
        return List.of(new Category("tfcpp",
            L.of("TFC++", "TFC++"),
            L.of("What TFC++ adds to TerraFirmaCraft: gases and poisons, sulfide ores, coal chemistry, acids and glass, all under the physics and chemistry of the real thing.",
                "Что TFC++ добавляет к TerraFirmaCraft: газы и яды, сульфидные руды, углехимия, кислоты и стекло по физике и химии настоящих процессов."),
            "tfcpp:retort", 7));
    }

    static List<Entry> entries()
    {
        final List<Entry> entries = new ArrayList<>();
        entries.add(introduction());
        entries.addAll(GasesGuide.entries());
        entries.addAll(SulfidesGuide.entries());
        entries.addAll(PipesGuide.entries());
        return entries;
    }

    private static Entry introduction()
    {
        return Entry.of("tfcpp/introduction", CATEGORY, "tfcpp:retort", 0, L.of("TFC++", "TFC++"))
            .text(
                L.of("TFC++ takes TerraFirmaCraft past the smithy: from ore and the forge to coal chemistry, acids and glass, and later to oil, rubber, electricity and electronics.$(br2)Every process here follows the physics and chemistry of the real one. Nothing is made easier by being made false. Where something is hard to grasp, it is this book that explains it.",
                    "TFC++ уводит TerraFirmaCraft за пределы кузницы: от руды и горна к углехимии, кислотам и стеклу, а позже к нефти, резине, электричеству и электронике.$(br2)Каждый процесс здесь идёт по физике и химии настоящего. Ничто не упрощено ложью. Если что-то трудно понять, это объясняет эта книга."))
            .titled(
                L.of("What that means", "Что это значит"),
                L.of("$(li)Fire needs air and gives off gas. A closed room fills with it, and some of it is invisible.$(li)Sulfide ores are roasted rather than smelted, and their fumes poison.$(li)Heat has limits. A pit kiln cannot melt copper, and iron needs a blast of air on top of good fuel.$(li)Nothing comes from nowhere. A yield is always less than the input, and waste is raw material for the next step.$(li)Things wear out: pipes corrode, linings burn through, glass cracks.",
                    "$(li)Огню нужен воздух, и он даёт газ. Закрытая комната наполняется им, а часть его невидима.$(li)Сульфидные руды обжигают, а не плавят, и их пары ядовиты.$(li)У жара есть предел. В земляной печи медь не расплавить, а для железа к хорошему топливу нужно ещё дутьё.$(li)Ничто не берётся из ничего. Выход всегда меньше сырья, а отходы служат сырьём для следующего шага.$(li)Всё изнашивается: трубы ржавеют, футеровка прогорает, стекло трескается."))
            .titled(
                L.of("Danger", "Опасность"),
                L.of("Carbon monoxide has no colour and no smell, and it kills quietly. Other hazards announce themselves: sulfur dioxide by its sharp smell, methane by the halo in a Davy lamp, steam by the hiss of a safety valve.$(br2)The chapters of this section teach the signs, the instruments and the ventilation that answer them. Learn them before you light the furnace.",
                    "Угарный газ не имеет ни цвета, ни запаха и убивает тихо. Другие опасности заявляют о себе сами: сернистый газ резким запахом, метан ореолом в лампе Дэви, пар шипением предохранительного клапана.$(br2)Главы этого раздела учат признакам, приборам и вентиляции, которые на них отвечают. Выучите их до того, как разожжёте печь."))
            .titled(
                L.of("About this book", "О книге"),
                L.of("The chapters of TerraFirmaCraft that TFC++ made wrong or incomplete have been rewritten, so that what they say is true in this game. This section holds what TFC++ has added.$(br2)Each chapter says what is needed, how it is done, why it is so in reality, and what can kill you.",
                    "Главы TerraFirmaCraft, которые TFC++ сделал неверными или неполными, переписаны, и написанное в них верно для этой игры. В этом разделе то, что добавил TFC++.$(br2)Каждая глава говорит, что нужно, как это делается, почему так в настоящей жизни и чем это может убить."))
            .titled(
                L.of("Time and units", "Время и единицы"),
                L.of("Slow processes (corrosion, poisoning, wear) run on real time and do not follow the world calendar: a process $(bold)day$() lasts 24 minutes and an $(bold)hour$() one minute.$(br2)Chemical liquids count 1 mB as 1 litre. Molten metal counts 1 mB as 1 millilitre. Pressure is in atmospheres, temperature in °C, gas in parts per million.",
                    "Медленные процессы (коррозия, отравление, износ) идут по реальному времени и не следуют календарю мира: $(bold)сутки$() процесса длятся 24 минуты, а $(bold)час$() одну минуту.$(br2)Для химических жидкостей 1 мБ равен 1 литру. Для расплавленного металла 1 мБ равен 1 миллилитру. Давление в атмосферах, температура в °C, газ в частях на миллион."))
            .titled(
                L.of("A team's work", "Работа команды"),
                L.of("TFC++ is built for a server with many players. One person can take the first steps, but nobody finishes it alone: prospecting and mining, metallurgy, chemical plants, ventilation and piping are separate trades, and what one trade throws away is another's raw material.",
                    "TFC++ рассчитан на сервер с большим числом игроков. Первые шаги по силам одному, но один никто не пройдёт его до конца: разведка и добыча, металлургия, химические заводы, вентиляция и трубопроводы это разные ремёсла, и что одно выбрасывает, то другому сырьё."));
    }

    private TfcppGuide() {}
}
