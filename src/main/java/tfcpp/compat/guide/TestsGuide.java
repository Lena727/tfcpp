package tfcpp.compat.guide;

import java.util.List;

/**
 * The chapter of the TFC++ category about tests and indicators: what finds each danger that cannot be seen, the Marsh apparatus, the gold leaf, and what has no test.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code MarshApparatusItem} and {@code PoisonTests} (mirrors, samples, pinch), {@code GoldLeafIndicatorBlockEntity} and {@code GoldLeafIndicatorBlock} (leaf), the recipes {@code tfcpp:crafting/marsh_apparatus}, {@code gold_leaf_indicator} and {@code mercury_manometer}. The time a leaf takes in air that holds all the mercury it can (13 mg/m3, 1 mg/m3 of dose in 4.6 s at a local hour of 60 s) is worked out from them.
 */
final class TestsGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(tests());
    }

    private static Entry tests()
    {
        return Entry.of("tfcpp/tests", CATEGORY, "tfcpp:marsh_apparatus", 11, L.of("Tests and Indicators", "Пробы и индикаторы"))
            .titled(
                L.of("Seeing what cannot be seen", "Как увидеть невидимое"),
                L.of("Each danger has its test:$(li)Carbon monoxide: a $(l:tfcpp/poisons#carbon_monoxide)caged animal$(), which falls first.$(li)Firedamp and low oxygen: the $(l:tfcpp/mine_safety#lamp)Davy lamp$().$(li)Sulfur dioxide, chlorine, hydrogen chloride, ammonia: the nose, by a message of a smell, see $(l:tfcpp/poisons#lungs)Poisons$().$(li)Arsenic and antimony: the $(l:tfcpp/tests#marsh)Marsh apparatus$().$(li)Mercury: the $(l:tfcpp/tests#leaf)gold leaf$().$(li)Pressure: the $(l:tfcpp/pipes#gauge)mercury manometer$().",
                    "У каждой опасности своя проба:$(li)Угарный газ: $(l:tfcpp/poisons#carbon_monoxide)клетка с животным$(), которое падает первым.$(li)Рудничный газ и нехватка кислорода: $(l:tfcpp/mine_safety#lamp)лампа Дэви$().$(li)Сернистый газ, хлор, хлороводород, аммиак: нос, по сообщению о запахе, см. $(l:tfcpp/poisons#lungs)Яды$().$(li)Мышьяк и сурьма: $(l:tfcpp/tests#marsh)аппарат Марша$().$(li)Ртуть: $(l:tfcpp/tests#leaf)золотой лист$().$(li)Давление: $(l:tfcpp/pipes#gauge)ртутный манометр$()."))
            .crafting("tfcpp:crafting/marsh_apparatus",
                L.of("The $(thing)Marsh Apparatus$() is a bottle of silica glass and an ingot of zinc.",
                    "$(thing)Аппарат Марша$() это бутылка из кремнезёмного стекла и слиток цинка."))
            .showsOnLastPage("tfcpp:marsh_apparatus")
            .titled(
                L.of("Charging it", "Зарядка"),
                L.of("A new apparatus has no tests in it. Hold it with a $(thing)carboy$() of oil of vitriol or chamber acid in the other hand and right-click: it takes 1 L of the acid and holds 8 tests.$(br2)Zinc and acid make hydrogen, and with arsenic in the sample, arsine, which heat breaks up into a mirror of metal on the glass. That was the test of James Marsh in 1836, and it found arsenic in the bodies of the poisoned.",
                    "В новом аппарате проб нет. Держите его с $(thing)бутылью$() купоросного масла или камерной кислоты в другой руке и нажмите правой кнопкой: он возьмёт 1 л кислоты и заряд на 8 проб.$(br2)Цинк с кислотой дают водород, а если в пробе мышьяк, то арсин, который жар разлагает в зеркало металла на стекле. Это проба Джеймса Марша 1836 года, и по ней находили мышьяк в телах отравленных."))
            .titled(
                L.of("The air", "Воздух"),
                L.of("Right-click with the apparatus, the other hand empty or holding the carboy: it tests the air at your eye. The answer is a message:$(li)$(thing)no mirror$(): under 1 mg/m³ of arsenic or antimony oxide$(li)$(thing)a faint mirror$(): from 1 mg/m³, which is 0.02 mg in a sample of 20 L$(li)$(thing)a dense mirror$(): from 20 mg/m³, air that a man should leave$(br)Arsenic and antimony add up and look alike: the mirror does not say which it is.",
                    "Правый клик аппаратом, с пустой другой рукой или с бутылью в ней, проверяет воздух у ваших глаз. Ответ приходит сообщением:$(li)$(thing)зеркала нет$(): меньше 1 мг/м³ оксида мышьяка или сурьмы$(li)$(thing)слабое зеркало$(): от 1 мг/м³, это 0,02 мг в пробе 20 л$(li)$(thing)плотное зеркало$(): от 20 мг/м³, воздух, который надо покинуть$(br)Мышьяк и сурьма складываются и выглядят одинаково: зеркало не скажет, что именно."))
            .anchor("marsh")
            .titled(
                L.of("A sample", "Образец"),
                L.of("With a sample in the other hand it tests the sample:$(li)tetrahedrite, white arsenic and antimony oxide give a dense mirror$(li)an ingot of arsenical copper gives a faint one$(li)anything else gives none$(br2)The flask is vented, and the test lets a little of its arsine into the air at your eye: 5 mg of the oxide from a dense sample, a tenth of that from a faint one. Test in clean air, not in a closed room.",
                    "С образцом в другой руке аппарат проверяет образец:$(li)тетраэдрит, белый мышьяк и оксид сурьмы дают плотное зеркало$(li)слиток мышьяковистой меди даёт слабое$(li)всё остальное не даёт$(br2)Колба с вентиляцией, и проба выпускает в воздух у ваших глаз немного арсина: 5 мг оксида из плотного образца, десятую часть от слабого. Проверяйте на чистом воздухе, а не в закрытой комнате."))
            .crafting("tfcpp:crafting/gold_leaf_indicator",
                L.of("One sheet of gold, beaten thin, makes 8 $(thing)Gold Leaf Indicators$(). Place one where you want to watch the air.",
                    "Один лист золота, расплющенный тонко, даёт 8 $(thing)индикаторов из золотого листа$(). Поставьте его там, где хотите следить за воздухом."))
            .showsOnLastPage("tfcpp:gold_leaf_indicator")
            .titled(
                L.of("Mercury on gold", "Ртуть на золоте"),
                L.of("Mercury vapour takes gold into an amalgam that whitens it, and it does not wash off. Once a second the leaf breathes the air of its own cell and keeps the dose, in mg/m³ times hours (an hour is a process hour, a minute):$(li)stage 0: clean$(li)stage 1: traces, from 1$(li)stage 2: exposed, from 10$(li)stage 3: heavily poisoned, from 100$(br)An empty hand names the stage. In air as full of mercury as it can be, 13 mg/m³, a leaf is at stage 1 in 5 seconds and at stage 3 in 8 minutes.",
                    "Пары ртути превращают золото в амальгаму, которая белеет, и это не смывается. Раз в секунду лист дышит воздухом своей клетки и копит дозу в мг/м³ на часы (час это час процесса, минута):$(li)ступень 0: чистый$(li)ступень 1: следы, от 1$(li)ступень 2: заражён, от 10$(li)ступень 3: сильно отравлен, от 100$(br)Пустая рука называет ступень. В воздухе, насыщенном ртутью до предела, 13 мг/м³, лист доходит до ступени 1 за 5 секунд и до ступени 3 за 8 минут."))
            .anchor("leaf")
            .titled(
                L.of("A leaf is for life", "Лист на всю жизнь"),
                L.of("The whitening is for good, and the dose does not go with the leaf: a clean leaf breaks into a leaf again, a whitened one is lost. At stage 3 it reads no more. It is a plate a sixteenth of a block thick, and gas passes through it.$(br2)Put leaves where mercury may be: over the retort, at the tower, in the room where cinnabar roasts (see $(l:tfcpp/sulfides#mercury)Sulfide Ores$()). A leaf that stays clean is the news you wanted.",
                    "Побеление необратимо, и доза не уходит вместе с листом: чистый лист при ломке снова становится листом, побелевший пропадает. На ступени 3 он больше ничего не читает. Это пластина в шестнадцатую долю блока, и газ проходит сквозь неё.$(br2)Ставьте листы там, где может быть ртуть: над ретортой, у башни, в комнате, где обжигают киноварь (см. $(l:tfcpp/sulfides#mercury)Сульфидные руды$()). Лист, оставшийся чистым, это та новость, которой вы ждали."))
            .crafting("tfcpp:crafting/mercury_manometer",
                L.of("The $(thing)Mercury Manometer$() tests a line for pressure, in millimetres of mercury (see $(l:tfcpp/pipes#gauge)Pipes and Pressure$()). TFC's thermometer takes a mercury bulb too (see $(l:mechanics/redstone_devices)Redstone Devices$()).",
                    "$(thing)Ртутный манометр$() проверяет линию на давление, в миллиметрах ртутного столба (см. $(l:tfcpp/pipes#gauge)Трубы и давление$()). Термометр TFC тоже берёт ртутную колбу (см. $(l:mechanics/redstone_devices)Редстоун-устройства$())."))
            .showsOnLastPage("tfcpp:mercury_manometer")
            .titled(
                L.of("What has no test", "Что без пробы"),
                L.of("Carbon monoxide has no colour, no smell and no instrument but the caged animal: it takes in the gas twenty times faster than you do. Low oxygen has none either, but the Davy lamp goes out below 16%, and a fire dies below 12%.$(br2)Where a test is missing, ventilate first. A test shows a danger that is there. It does not show that none is.",
                    "У угарного газа нет ни цвета, ни запаха, ни прибора, кроме клетки с животным: оно вдыхает газ в двадцать раз быстрее вас. У нехватки кислорода тоже нет, но лампа Дэви гаснет ниже 16%, а огонь умирает ниже 12%.$(br2)Где пробы нет, сначала проветривайте. Проба показывает опасность, которая есть. Что её нет, она не показывает."));
    }

    private TestsGuide() {}
}
