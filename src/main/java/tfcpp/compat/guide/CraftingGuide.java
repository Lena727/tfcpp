package tfcpp.compat.guide;

import java.util.List;
import com.google.gson.JsonObject;

import tfcpp.compat.guide.Pages.Structure;

/**
 * TFC's Crafting Systems chapters that TFC++ made wrong or incomplete: barrels (limewater from burnt lime only, quenching, what a barrel will not hold), the kiln (its door), glassworking (the heat each glass can be worked at) and leatherworking (limewater).
 */
final class CraftingGuide
{
    private static final String CATEGORY = "tfc:crafting";

    static List<Entry> entries()
    {
        return List.of(barrels(), glassworking(), kilns(), leatherMaking());
    }

    private static Entry barrels()
    {
        final JsonObject sideways = Structure.layers(new String[][] {{"X"}, {"Y"}, {"0"}},
            "X", "tfc:wood/barrel/kapok[facing=north,rack=false,sealed=true]", "Y", "tfc:wood/barrel/kapok[facing=north,rack=true,sealed=false]");
        return Entry.of("crafting/barrels", CATEGORY, "tfc:wood/barrel/palm", 0, L.of("Barrels", "Бочки"))
            .text(
                L.of("The $(thing)Barrel$() holds items and fluids. The middle slot is for items. Fluid shows in the tank on the left. Add it by putting a filled $(thing)bucket$() or $(thing)jug$() in the upper left slot, and take it out by putting an empty container there. $(item)$(k:key.use)$() with a bucket on the block works too.",
                    "$(thing)Бочка$() хранит предметы и жидкости. Средняя ячейка для предметов. Жидкость видна в баке слева. Её добавляют, положив полное $(thing)ведро$() или $(thing)кувшин$() в левую верхнюю ячейку, и забирают, положив туда пустую ёмкость. Работает и $(item)$(k:key.use)$() с ведром по блоку."))
            .showsOnLastPage("tag:tfc:barrels")
            .crafting("tfc:crafting/wood/barrel/oak", L.of("A barrel is made of seven $(thing)lumber$().", "Бочка делается из семи $(thing)досок$()."))
            .image(L.of("The barrel interface.", "Интерфейс бочки."), false, "tfc:textures/gui/book/gui/barrel.png")
            .text(
                L.of("A barrel can be $(thing)sealed$(). A sealed barrel can be broken without spilling, and it works recipes. The grey button on the right of the interface seals it, and so does $(item)$(k:key.use)$() with an empty hand while holding $(item)$(k:key.sneak)$().",
                    "Бочку можно $(thing)запечатать$(). Запечатанную бочку можно сломать без потерь, и в ней идут рецепты. Печатает её серая кнопка справа в интерфейсе, а также $(item)$(k:key.use)$() пустой рукой с зажатым $(item)$(k:key.sneak)$()."))
            .titled(
                L.of("Barrel Tips", "Советы"),
                L.of("$(li)Click an empty barrel on a fluid block to fill it.$(li)Rain fills barrels slowly, and melting icicles above them add water.$(li)Sealing throws out items that are not in the middle slot.$(li)Acids and other corrosive liquids do not go into a barrel: they eat the wood. Tar and the liquors of dry distillation do.",
                    "$(li)Чтобы наполнить пустую бочку, кликните ею по блоку жидкости.$(li)Дождь медленно наполняет бочки, и тающие сосульки над ними добавляют воду.$(li)Запечатывание выбрасывает предметы не из средней ячейки.$(li)Кислоты и другие едкие жидкости в бочку не идут: они разъедают дерево. Смола и жидкости сухой перегонки идут."))
            .titled(
                L.of("Recipes", "Рецепты"),
                L.of("A barrel makes new things from fluids and items together. For a recipe the contents must be in its proportions. Some recipes need the barrel sealed for a time, so that the contents can change. Others work at once.",
                    "Бочка делает новое из жидкостей и предметов вместе. Для рецепта содержимое должно быть в его пропорции. Одним рецептам нужна запечатанная бочка на какое-то время, чтобы содержимое преобразилось. Другие идут сразу."))
            .text(
                L.of("If the contents do not fit a recipe, the excess fluid or items are lost. For an instant recipe you must always have at least enough items to change all the fluid.",
                    "Если содержимое не подходит рецепту, лишние жидкость или предметы теряются. Для мгновенного рецепта предметов всегда должно хватать, чтобы превратить всю жидкость."))
            .instantBarrel("tfc:barrel/limewater",
                L.of("$(bold)Limewater$() is $(l:crafting/glassworking#lime)Lime$() in water: one powder of lime for each 500 mB of $(thing)Water$(). Raw flux will not do, it has to be burnt to lime first. Limewater serves $(l:crafting/leather_making)leather$() and mortar.",
                    "$(bold)Известковая вода$() это $(l:crafting/glassworking#lime)известь$() в воде: порошок извести на каждые 500 мБ $(thing)воды$(). Сырой флюс не годится, его сперва обжигают в известь. Известковая вода нужна для $(l:crafting/leather_making)кожи$() и раствора."))
            .anchor("limewater")
            .sealedBarrel("tfc:barrel/tannin",
                L.of("$(bold)Tannin$() is an acid liquid got by steeping the bark of certain $(thing)Logs$() in $(thing)Water$(). Bark of sequoia, maple, hickory, douglas fir, chestnut, birch or oak will all do.",
                    "$(bold)Дубильная кислота$() кислая жидкость, получаемая настаиванием коры некоторых $(thing)брёвен$() в $(thing)воде$(). Подойдёт кора секвойи, клёна, гикори, пихты Дугласа, каштана, берёзы или дуба."))
            .anchor("tannin")
            .text(
                L.of("Some recipes mix two fluids in a set ratio: put a filled bucket of one in the fluid slot of a barrel that holds the right amount of the other. So $(thing)Milk Vinegar$() is made, one part of vinegar to nine of milk. Vinegar in the same ratio with salt water makes $(thing)Brine$().",
                    "Некоторые рецепты смешивают две жидкости в заданной пропорции: положите полное ведро одной в ячейку жидкости бочки, где нужное количество другой. Так делают $(thing)молочный уксус$(): одна часть уксуса на девять частей молока. Уксус в той же пропорции с солёной водой даёт $(thing)рассол$()."))
            .text(
                L.of("A barrel quenches hot items. Put one into $(thing)Water$(), $(thing)Olive Oil$() or $(thing)Salt Water$() and its temperature drops fast: water and salt water take off 15 °C for each mB used, oils 5 °C. Water chills hard and oil gently, as steel quenched in them does in practice.",
                    "Бочка закаляет горячие предметы. Положите предмет в $(thing)воду$(), $(thing)оливковое масло$() или $(thing)солёную воду$(), и его температура быстро падает: вода и солёная вода снимают по 15 °C на каждый потраченный мБ, масла по 5 °C. Вода охлаждает резко, масло мягко, как и закалка стали в них на деле."))
            .text(
                L.of("Barrels $(l:mechanics/dye)dye$() and $(l:mechanics/dye#lye)bleach$() things. Carpet, candles, $(l:mechanics/advanced_building_materials#plaster)Plaster$() and most coloured items are dyed by sealing them in dye, and bleached by sealing them in lye. See the dye chapter.",
                    "В бочках $(l:mechanics/dye)красят$() и $(l:mechanics/dye#lye)отбеливают$(). Ковры, свечи, $(l:mechanics/advanced_building_materials#plaster)штукатурку$() и большинство цветных вещей красят, запечатав в краске, и отбеливают, запечатав в щёлоке. Смотрите главу о красителях."))
            .text(
                L.of("Vinegar keeps food. To get it, seal $(thing)Fruit$() together with $(thing)Alcohol$() in a barrel. How that goes is on the $(l:food/decay#vinegar)preservation page$().",
                    "Уксус сохраняет еду. Его делают, запечатав $(thing)фрукты$() в бочке с $(thing)алкоголем$(). Как это происходит, на $(l:food/decay#vinegar)странице о хранении$()."))
            .crafting("tfc:crafting/barrel_rack",
                L.of("A barrel set against the side of a block works like a standing one, with limits on placing. It needs a $(thing)Barrel Rack$(), added with $(item)$(k:key.use)$(), before another sideways barrel can go above it.",
                    "Бочка у бока блока работает как стоячая, но с ограничениями на установку. Прежде чем поставить над ней ещё одну боковую, нужна $(thing)стойка для бочек$(), её добавляют $(item)$(k:key.use)$()."))
            .multiblock(sideways, L.of("Sideways Barrels", "Боковые бочки"), L.of("A sideways barrel shows that it is sealed by its $(thing)Tap$(): a sideways tap means sealed.", "Боковая бочка показывает, что запечатана, своим $(thing)краном$(): кран вбок значит запечатана."))
            .text(
                L.of("An unsealed sideways barrel pours its fluid into open barrels and other containers set under its tap. The block the tap points into must be free air for the fluid to flow.",
                    "Незапечатанная боковая бочка переливает жидкость в открытые бочки и другие ёмкости под краном. Блок, в который смотрит кран, должен быть свободным воздухом, чтобы жидкость текла."))
            .image(L.of("A barrel dripping.", "Бочка капает."), true, "tfc:textures/gui/book/tutorial/barrel_drip.png");
    }

    private static Entry glassworking()
    {
        return Entry.of("crafting/glassworking", CATEGORY, "tfc:silica_glass_bottle", 5, L.of("Glassworking", "Изготовление стекла"))
            .text(
                L.of("Glassworking turns sand into glass. First make a $(thing)Glass Batch$(). The colour of the sand decides which kind it is:$(br)$(br)$(li)Black sand: $(thing)Volcanic$()$(li)Green or brown sand: $(thing)Olivine$()$(li)Yellow, red or pink sand: $(thing)Hematitic$()$(li)White sand: $(thing)Silica$()",
                    "Стеклоделие превращает песок в стекло. Сначала готовят $(thing)стекольную шихту$(). Её вид определяет цвет песка:$(br)$(br)$(li)чёрный песок: $(thing)вулканическая$()$(li)зелёный или бурый песок: $(thing)оливиновая$()$(li)жёлтый, красный или розовый песок: $(thing)гематитовая$()$(li)белый песок: $(thing)кремнезёмная$()"))
            .crafting("tfc:crafting/silica_glass_batch",
                L.of("A batch is crafted from sand of one of those colours, $(l:crafting/glassworking#lime)Lime$() and a $(l:crafting/glassworking#potash)Potash$().", "Шихту делают из песка одного из этих цветов, $(l:crafting/glassworking#lime)извести$() и $(l:crafting/glassworking#potash)поташа$()."))
            .heating("tfc:heating/powder/lime",
                L.of("$(thing)Lime$() is one of the parts of a batch. It is a powder got by $(l:core_mechanics/heating)heating$() $(l:metalworking/flux)Flux$() to 840 °C. Borax flux does not burn to lime.", "$(thing)Известь$() входит в шихту. Это порошок, получаемый $(l:core_mechanics/heating)нагревом$() $(l:metalworking/flux)флюса$() до 840 °C. Флюс из буры в известь не обжигается."))
            .anchor("lime")
            .showsOnLastPage("tfc:powder/lime")
            .heating("tfc:heating/powder/soda_ash_from_seaweed",
                L.of("A $(thing)Potash$() or its like is the other part. $(thing)Soda Ash$() will do, a powder got by firing $(thing)Dried Seaweed$() or $(thing)Kelp$(), and so will $(l:the_world/ores_and_minerals#saltpeter)Saltpeter$().", "Нужен также $(thing)поташ$() или подобное. Годится $(thing)кальцинированная сода$(): порошок от нагрева $(thing)сушёных водорослей$() или $(thing)ламинарии$(). Можно и $(l:the_world/ores_and_minerals#saltpeter)селитру$()."))
            .anchor("potash")
            .showsOnLastPage("tfc:powder/soda_ash")
            .titled(
                L.of("Tools of the Trade", "Инструменты ремесла"),
                L.of("Glass is worked from a batch through a series of steps, and some steps need a tool:$(li)$(thing)Saw$(): a $(l:crafting/glassworking#saw)Gem Saw$()$(li)$(thing)Pinch$(): $(l:crafting/glassworking#jacks)Jacks$()$(li)$(thing)Flatten$(): a $(l:crafting/glassworking#paddle)Paddle$()$(li)$(thing)Blow$() and $(thing)Stretch$(): a $(l:crafting/glassworking#blowpipe)Blowpipe$()",
                    "Стекло из шихты делают рядом шагов, и для некоторых шагов нужен инструмент:$(li)$(thing)распиливание$(): $(l:crafting/glassworking#saw)пила для самоцветов$()$(li)$(thing)защипывание$(): $(l:crafting/glassworking#jacks)щипцы$()$(li)$(thing)расплющивание$(): $(l:crafting/glassworking#paddle)лопатка$()$(li)$(thing)выдувание$() и $(thing)вытягивание$(): $(l:crafting/glassworking#blowpipe)стеклодувная трубка$()"))
            .knapping("tfc:knapping/ceramic/unfired_blowpipe",
                L.of("The key tool is the $(thing)Blowpipe$(). Knap it from clay and fire it into a $(thing)Ceramic Blowpipe$().", "Главный инструмент $(thing)стеклодувная трубка$(). Выбейте её из глины и обожгите в $(thing)керамическую трубку$()."))
            .anchor("blowpipe")
            .anvil("tfc:anvil/blowpipe",
                L.of("Ceramic blowpipes are brittle and may break in use. A sturdier pipe is forged at the anvil from a $(thing)Brass Rod$(), but brass melts at 930 °C.", "Керамические трубки хрупки и могут разбиться при работе. Прочнее выкованная из $(thing)латунного прута$() на наковальне, но латунь плавится при 930 °C."))
            .crafting("tfc:crafting/paddle",
                L.of("$(thing)Flatten$() is done with a $(thing)Paddle$(), made of wood.", "$(thing)Расплющивание$() делают $(thing)лопаткой$() из дерева."))
            .anchor("paddle")
            .showsOnLastPage("tfc:paddle")
            .welding("tfc:welding/jacks",
                L.of("$(thing)Pinch$() is done with $(thing)Jacks$(), two brass rods welded together.", "$(thing)Защипывание$() делают $(thing)щипцами$(): две латунные палки, сваренные вместе."))
            .anchor("jacks")
            .showsOnLastPage("tfc:jacks")
            .crafting("tfc:crafting/gem_saw",
                L.of("$(thing)Saw$() is done with a $(thing)Gem Saw$(), which also breaks $(thing)Glass Blocks$() and $(thing)Glass Panes$() so that you can take them.", "$(thing)Распиливание$() делают $(thing)пилой для самоцветов$(), ею же ломают $(thing)стеклянные блоки$() и $(thing)панели$(), чтобы забрать их."))
            .anchor("saw")
            .showsOnLastPage("tfc:gem_saw")
            .titled(
                L.of("How to Glass", "Как работать со стеклом"),
                L.of("Heat the glass on the pipe to $(4)$(bold)Faint Red$() first. For each step, hold the blowpipe and keep $(item)$(k:key.use)$() pressed.$(br2)$(bold)Blow$(): use the pipe looking straight ahead.$(br)$(bold)Stretch$(): use it looking straight down.",
                    "Сначала нагрейте стекло на трубке до $(4)$(bold)тёмно-красного каления$(). Затем держите трубку и удерживайте $(item)$(k:key.use)$() на каждом шаге.$(br2)$(bold)Выдувание$(): используйте трубку, глядя прямо перед собой.$(br)$(bold)Вытягивание$(): используйте её, глядя вниз."))
            .text(
                L.of("$(bold)Flatten$(): use the pipe with a $(l:crafting/glassworking#paddle)Paddle$() in the off hand.$(br)$(bold)Pinch$(): with $(l:crafting/glassworking#jacks)Jacks$() in the off hand.$(br)$(bold)Saw$(): with a $(l:crafting/glassworking#saw)Gem Saw$() in the off hand.$(br)$(bold)Roll$(): with a $(l:crafting/weaving#wool_cloth)Wool Cloth$() in the off hand.",
                    "$(bold)Расплющивание$(): используйте трубку с $(l:crafting/glassworking#paddle)лопаткой$() в другой руке.$(br)$(bold)Защипывание$(): с $(l:crafting/glassworking#jacks)щипцами$() в другой руке.$(br)$(bold)Распиливание$(): с $(l:crafting/glassworking#saw)пилой для самоцветов$() в другой руке.$(br)$(bold)Прокатка$(): с $(l:crafting/weaving#wool_cloth)шерстяной тканью$() в другой руке."))
            .titled(
                L.of("How hot", "Какой жар"),
                L.of("Glass is worked while the pipe is hot, and not hotter than the pipe stands. Ordinary glass works from $(4)Faint Red$() (480 °C). $(thing)Borosilicate$() glass, made of sand and borax with no lime, needs 1250 °C. Brass melts at 930 °C, so only a ceramic pipe will do for it, between 1250 and 1500 °C: above that TFC returns the pipe empty. See $(l:tfcpp/glass)Borosilicate Glass$().",
                    "Стекло работают, пока трубка горяча, и не горячее, чем она выдерживает. Обычное стекло работается с $(4)тёмно-красного каления$() (480 °C). $(thing)Боросиликатному$() стеклу из песка и буры без извести нужно 1250 °C. Латунь плавится при 930 °C, поэтому ему подойдёт лишь керамическая трубка, между 1250 и 1500 °C: выше TFC возвращает пустую трубку. См. $(l:tfcpp/glass)Боросиликатное стекло$()."));
    }

    private static Entry kilns()
    {
        return Entry.of("crafting/kilns", CATEGORY, "tfc:firebox", 9, L.of("Kilns", "Кирпичные печи"))
            .text(
                L.of("The $(thing)Kiln$() heats and fires many items at once in a space you choose. You build it from one $(thing)Firebox$(), one or more $(thing)Grates$(), and walls that resist fire.",
                    "$(thing)Кирпичная печь$() нагревает и обжигает сразу много предметов в выбранном вами объёме. Её строят из $(thing)топки$(), хотя бы одной $(thing)решётки$() и огнеупорных стен."))
            .crafting("tfc:crafting/firebox", "tfc:crafting/metal/grate/bronze", L.of("", ""))
            .text(
                L.of("The $(thing)Firebox$() has a temperature gauge and 16 slots, all for fuel, any fuel. In the open it heats nothing. Below Bright Red it heats up to 64 blocks, above that 128. The time it needs grows with the number of blocks and the temperature wanted. It is a fire like any other: it uses air and fumes up the room, with sulfur dioxide if it burns coal. See $(l:tfcpp/gases)Gases$() and $(l:tfcpp/poisons)Poisons$().",
                    "У $(thing)топки$() шкала температуры и 16 ячеек, все под топливо, любое. На открытом месте она ничего не греет. Ниже ярко-красного каления она греет до 64 блоков, выше 128. Нужное время растёт с числом блоков и желаемой температурой. Это такой же огонь, как другие: он расходует воздух и коптит комнату, а на каменном угле ещё и сернистым газом. См. $(l:tfcpp/gases)Газы$() и $(l:tfcpp/poisons)Яды$()."))
            .text(
                L.of("The firebox heats starting with the block above it. Its heat passes through blocks that are not solid and through Grates, so put a grate on top to let the heat up into the structure and keep access from below.",
                    "Топка греет, начиная с блока над собой. Жар проходит сквозь непрочные блоки и решётки, поэтому поставьте решётку сверху: тепло пойдёт в постройку, а снизу останется доступ."))
            .text(
                L.of("Allowed for the walls: Fireproof Doors, Tinted Glass, Fire Bricks and Clay Bricks. Plain stone bricks are refused. If the structure is broken during heating, the heating is lost and starts again once it is closed, and the timer also restarts when the temperature setting changes. Clay ware is fired at 850 °C, fire clay ware at 1300 °C.",
                    "Стены должны быть из глиняных кирпичей, огнеупорных кирпичей, тонированного стекла или огнеупорных дверей. Каменные кирпичи не годятся. Если постройку нарушить во время нагрева, нагрев пропадает и начнётся заново, когда её закроют, а таймер идёт заново и при смене температуры. Глиняные изделия обжигаются при 850 °C, шамотные при 1300 °C."))
            .crafting("tfc:crafting/fireproof_door",
                L.of("The Fireproof Door is made of wrought iron sheets.", "Огнеупорная дверь делается из листов кованого железа."));
    }

    private static Entry leatherMaking()
    {
        return Entry.of("crafting/leather_making", CATEGORY, "minecraft:leather", 2, L.of("Leatherworking", "Выделка кожи"))
            .text(
                L.of("$(thing)Leather$() is tough material from animal hides. It is needed for $(l:mechanics/armor)Armor$(), $(thing)Saddles$() and a $(l:metalworking/bellows)Bellows$(). A $(thing)Raw Hide$() goes through $(l:crafting/leather_making#soaking)soaking$(), $(l:crafting/leather_making#scraping)scraping$(), $(l:crafting/leather_making#preparing)preparing$() and $(l:crafting/leather_making#tanning)tanning$() to become leather.",
                    "$(thing)Кожа$() прочный материал из шкур животных. Она нужна для $(l:mechanics/armor)брони$(), $(thing)сёдел$() и $(l:metalworking/bellows)мехов$(). $(thing)Сырая шкура$() проходит $(l:crafting/leather_making#soaking)вымачивание$(), $(l:crafting/leather_making#scraping)скобление$(), $(l:crafting/leather_making#preparing)подготовку$() и $(l:crafting/leather_making#tanning)дубление$(), и становится кожей."))
            .spotlight("tfc:small_raw_hide,tfc:medium_raw_hide,tfc:large_raw_hide", L.of("Raw Hide", "Сырая шкура"),
                L.of("First you need $(thing)Raw Hide$(), dropped by many $(l:the_world/wild_animals)Animals$() when slaughtered. Different animals leave hides of different size.", "Сначала нужна $(thing)сырая шкура$(), её оставляют многие $(l:the_world/wild_animals)животные$() при убое. Разные животные дают шкуры разного размера."))
            .showsOnLastPage("tfc:small_raw_hide", "tfc:medium_raw_hide", "tfc:large_raw_hide")
            .text(
                L.of("The work needs:$(li)$(l:crafting/barrels#limewater)Limewater$(): burnt lime (the powder of $(thing)Lime$()) dissolved in water$(li)Water$(li)$(l:crafting/barrels#tannin)Tannin$(): an acid liquid steeped from certain tree bark$(li)$(thing)Raw Hides$()$(br2)With these ready, you can begin.",
                    "Для работы нужны:$(li)$(l:crafting/barrels#limewater)известковая вода$(): обожжённая известь (порошок $(thing)извести$()), растворённая в воде$(li)вода$(li)$(l:crafting/barrels#tannin)дубильная кислота$(): кислая жидкость из коры некоторых деревьев$(li)$(thing)сырые шкуры$()$(br2)Когда всё готово, можно начинать."))
            .sealedBarrel("tfc:barrel/medium_soaked_hide",
                L.of("First the raw hide is $(thing)soaked$() to clean it and loosen what is not wanted. Leave the hide sealed six hours or longer in a $(l:crafting/barrels#limewater)barrel of limewater$().", "Сначала сырую шкуру $(thing)вымачивают$(), чтобы очистить и разрыхлить лишнее. Запечатайте шкуру в $(l:crafting/barrels#limewater)бочке с известковой водой$() не меньше чем на шесть часов."))
            .anchor("soaking")
            .showsOnLastPage("tfc:small_soaked_hide", "tfc:medium_soaked_hide", "tfc:large_soaked_hide")
            .spotlight("tfc:small_scraped_hide,tfc:medium_scraped_hide,tfc:large_scraped_hide", L.of("Scraping", "Скобление"),
                L.of("A soaked hide must then be $(thing)scraped$() of what is left. Lay it on the side of a $(thing)Log$(), and $(item)$(k:key.use)$() on each part of it with a $(thing)Knife$(): its texture changes.", "Вымоченную шкуру затем $(thing)скоблят$(), убирая остатки. Положите её на бок $(thing)бревна$() и $(item)$(k:key.use)$() ножом по каждой части: текстура меняется."))
            .anchor("scraping")
            .showsOnLastPage("tfc:small_scraped_hide", "tfc:medium_scraped_hide", "tfc:large_scraped_hide")
            .image(L.of("When the hide is fully scraped, break it to take a $(thing)Scraped Hide$().", "Когда шкура соскоблена полностью, сломайте её и заберите $(thing)соскобленную шкуру$()."), true,
                "tfc:textures/gui/book/tutorial/soaked_hide_1.png", "tfc:textures/gui/book/tutorial/soaked_hide_2.png", "tfc:textures/gui/book/tutorial/soaked_hide_3.png", "tfc:textures/gui/book/tutorial/soaked_hide_4.png")
            .sealedBarrel("tfc:barrel/medium_prepared_hide",
                L.of("Seal the scraped hide in a $(l:crafting/barrels)barrel$() of $(thing)Water$() and leave it six hours or more: a last cleaning before tanning.", "Соскобленную шкуру запечатывают в $(l:crafting/barrels)бочке$() с $(thing)водой$() не меньше чем на шесть часов: ещё одна очистка перед дублением."))
            .anchor("preparing")
            .showsOnLastPage("tfc:small_prepared_hide", "tfc:medium_prepared_hide", "tfc:large_prepared_hide")
            .sealedBarrel("tfc:barrel/medium_leather",
                L.of("At last the prepared hide is sealed in a $(l:crafting/barrels#tannin)barrel of Tannin$(), which turns it to leather. After another six hours take the $(thing)Leather$() out.", "Наконец подготовленную шкуру запечатывают в $(l:crafting/barrels#tannin)бочке с дубильной кислотой$(), и она превращается в кожу. Ещё через шесть часов заберите $(thing)кожу$()."))
            .anchor("tanning")
            .showsOnLastPage("minecraft:leather");
    }

    private CraftingGuide() {}
}
