package tfcpp.compat.guide;

import java.util.List;
import com.google.gson.JsonObject;

import tfcpp.compat.guide.Pages.Structure;

/**
 * TFC's Metalworking chapters that TFC++ made wrong or incomplete, written again from the code: bloomery, blast furnace, steel, charcoal forge, charcoal pit, flux, fire clay, crucible, bellows and anvils.
 * <p>
 * Every number on these pages is read from the data and the code of this mod (see {@code docs/physics-ledger.md}), not from TFC's text.
 */
final class MetalworkingGuide
{
    private static final String CATEGORY = "tfc:metalworking";

    static List<Entry> entries()
    {
        return List.of(anvils(), bellows(), blastFurnace(), bloomery(), charcoalForge(), charcoalPit(), crucible(), fireClay(), flux(), steel());
    }

    private static Entry bloomery()
    {
        return Entry.of("metalworking/bloomery", CATEGORY, "tfc:bloomery", 7, L.of("Bloomery", "Сыродутная печь"))
            .text(
                L.of("The $(thing)Bloomery$() reduces iron ore to a spongy lump of iron, a $(thing)Bloom$(), without melting it. The ores are $(l:the_world/ores_and_minerals#hematite)Hematite$(), $(l:the_world/ores_and_minerals#limonite)Limonite$() and $(l:the_world/ores_and_minerals#magnetite)Magnetite$().$(br2)Only ore goes in, and slag left by earlier smelts. Iron ingots and tools do not: they are iron already, and melt back into iron. Ore on its own melts only into oxide, which gives no metal. The carbon of charcoal has to take the oxygen away.",
                    "$(thing)Сыродутная печь$() восстанавливает железную руду до губчатого куска железа, $(thing)крицы$(), не расплавляя его. Руды: $(l:the_world/ores_and_minerals#hematite)гематит$(), $(l:the_world/ores_and_minerals#limonite)лимонит$() и $(l:the_world/ores_and_minerals#magnetite)магнетит$().$(br2)В печь идёт только руда и шлак прежних плавок. Железные слитки и орудия не годятся: это уже железо, и они плавятся обратно в железо. Одна руда плавится лишь в оксид, металла он не даёт. Кислород должен забрать углерод древесного угля."))
            .crafting("tfc:crafting/bloomery",
                L.of("The bloomery is built of 8 $(thing)Bronze Double Sheets$().", "Печь собирают из 8 $(thing)двойных листов бронзы$()."))
            .multiblockOf("tfc:bloomery", L.of("A Bloomery", "Сыродутная печь"),
                L.of("A bloomery of the least size. $(item)$(k:key.use)$() opens and closes its door.", "Сыродутная печь наименьшего размера. $(item)$(k:key.use)$() открывает и закрывает её створку."))
            .text(
                L.of("The bloomery takes up to $(cfg:bloomeryTotalCapacity) $(thing)items$(), $(cfg:bloomeryCapacity) for each layer of chimney. Add a layer by stacking two more rings of stone. Climb to the top and drop ore and charcoal down the chimney: a column of grey ore builds up.",
                    "В сыродутную печь входит до $(cfg:bloomeryTotalCapacity) $(thing)предметов$(), по $(cfg:bloomeryCapacity) на каждый слой трубы. Слой добавляют двумя новыми кольцами камня. Поднимитесь наверх и бросайте руду и древесный уголь в трубу: вырастет столб серой руды."))
            .image(L.of("Dropping a load in from the top.", "Загрузка сверху."), true, "tfc:textures/gui/book/tutorial/bloomery_hole.png")
            .text(
                L.of("Making a bloom takes 150 mB of iron oxide and 2 $(thing)Charcoal$(). Ore gives its oxide by size: small 10 mB, poor 15, normal 25, rich 35. Light the bloomery and wait 12 hours. It leaves a $(thing)Bloom$() block: mine it with a pickaxe, again and again, for $(thing)Raw Iron Blooms$().$(br2)Ore beyond a whole bloom is not lost. It comes out as slag, 25 mB a piece, which can be loaded again.",
                    "Крица требует 150 мБ оксида железа и 2 $(thing)древесного угля$(). Руда даёт оксид по размеру: мелкая 10 мБ, бедная 15, обычная 25, богатая 35. Разожгите печь и ждите 12 часов. Останется блок $(thing)крицы$(): добывайте его киркой раз за разом и получайте $(thing)сырые железные крицы$().$(br2)Руда сверх целой крицы не пропадает. Она выходит шлаком по 25 мБ на кусок, его можно загрузить снова."))
            .multiblock(Structure.block("tfc:bloom[layers=8]"), L.of("The Bloom Block", "Блок крицы"), L.of("A large $(thing)Bloom$().", "Большая $(thing)крица$()."))
            .anvil("tfc:anvil/refined_iron_bloom",
                L.of("A $(thing)Raw Iron Bloom$() still holds slag. Forge it on an $(l:metalworking/anvils)anvil$() into a $(thing)Refined Iron Bloom$().", "В $(thing)сырой железной крице$() ещё есть шлак. Скуйте её на $(l:metalworking/anvils)наковальне$() в $(thing)очищенную железную крицу$()."))
            .showsOnLastPage("tfc:raw_iron_bloom")
            .anvil("tfc:anvil/metal/ingot/wrought_iron",
                L.of("Forge the refined bloom again into $(thing)Wrought Iron Ingots$(). A bloom also melts straight into wrought iron, never into cast iron.", "Скуйте очищенную крицу ещё раз в $(thing)слитки кованого железа$(). Крица плавится и сразу в кованое железо, но не в чугун."))
            .showsOnLastPage("tfc:refined_iron_bloom")
            .titled(
                L.of("Dangers and details", "Опасности и подробности"),
                L.of("$(li)The oxygen of the ore leaves as carbon dioxide, and the fire itself makes carbon monoxide: over a third of its carbon. A bloomery shut in a room poisons it. Build it in the open.$(li)The phosphorus of limonite goes into the slag, not the bloom, so a bloom is plain iron. That slag stays phosphoric.$(li)Overfilling spits items out of the front.$(li)To get items back from a cold bloomery, break the bloomery block, not the tower of ore.",
                    "$(li)Кислород руды уходит углекислым газом, а сам огонь делает угарный: больше трети своего углерода. Сыродутная печь в закрытой комнате отравит её. Ставьте на открытом месте.$(li)Фосфор лимонита уходит в шлак, а не в крицу, поэтому крица чистое железо. Сам шлак остаётся фосфористым.$(li)При переполнении предметы выбрасываются из печи спереди.$(li)Чтобы вернуть предметы из холодной печи, ломайте блок самой печи, а не столб руды."));
    }

    private static Entry blastFurnace()
    {
        return Entry.of("metalworking/blast_furnace", CATEGORY, "tfc:blast_furnace", 11, L.of("Blast Furnace", "Доменная печь"))
            .text(
                L.of("The $(thing)Blast Furnace$() turns iron ore into $(thing)Pig Iron$(): a shaft of ore, flux and fuel with a blast of air from below, so hot that the iron melts and runs out. Pig iron is full of carbon, runs well and breaks easily. $(l:metalworking/steel)Steel$() is made from it.$(br2)To build one you need a $(l:metalworking/crucible)Crucible$() and many $(thing)Wrought Iron Sheets$().",
                    "$(thing)Доменная печь$() превращает железную руду в $(thing)передельный чугун$(): шахта из руды, флюса и топлива с дутьём снизу, так горячая, что железо плавится и стекает. Чугун полон углерода, хорошо льётся и легко ломается. Из него делают $(l:metalworking/steel)сталь$().$(br2)Для постройки нужен $(l:metalworking/crucible)тигель$() и много $(thing)листов кованого железа$()."))
            .crafting("tfc:crafting/blast_furnace",
                L.of("The crucible and the sheets make the furnace block.", "Тигель и листы дают блок печи."))
            .text(
                L.of("Its stack is built of $(l:metalworking/fire_clay#fire_bricks)Reinforced Fire Bricks$(), made from fire bricks and wrought iron or steel sheets: plain brick would not stand the heat. The chimney is up to five layers of eight bricks, and each layer lets the furnace hold four more pieces of ore.",
                    "Шахта строится из $(l:metalworking/fire_clay#fire_bricks)укреплённых огнеупорных кирпичей$(): огнеупорный кирпич и листы кованого железа или стали. Простой кирпич жара не выдержит. Труба до пяти слоёв по восемь кирпичей, и каждый слой даёт ещё четыре места для руды."))
            .multiblockOf("tfc:blast_furnace", L.of("A Blast Furnace", "Доменная печь"), L.of("A furnace with the lowest chimney.", "Печь с самой низкой трубой."))
            .titled(
                L.of("What goes in", "Что загружать"),
                L.of("Drop into the top of the stack $(thing)ore$() and $(l:metalworking/flux)Flux$() in equal numbers, and fuel. Ore is iron ore of any kind or iron slag. Ingots and tools do not count: they are iron already. The flux must be plain flux powder, not borax flux.$(br2)Fuel is $(thing)Charcoal$() or $(thing)Coke$(). Coal and lignite are refused: their sulfur would go into the iron, and their lumps cake and choke the blast.",
                    "Бросайте в верх шахты $(thing)руду$() и $(l:metalworking/flux)флюс$() поровну, а также топливо. Руда это любая железная руда или железистый шлак. Слитки и орудия не годятся: это уже железо. Флюс нужен обычный порошок флюса, не борный.$(br2)Топливо: $(thing)древесный уголь$() или $(thing)кокс$(). Каменный уголь и лигнит не принимаются: их сера ушла бы в железо, а куски спекаются и душат дутьё."))
            .titled(
                L.of("The interface", "Интерфейс"),
                L.of("Using the furnace opens an interface with gauges of ore and fuel. Its top right slot holds a $(thing)Tuyere$(), the pipe that carries the blast in. A tuyere is forged on an $(l:metalworking/anvils)Anvil$().",
                    "Использование печи открывает интерфейс со шкалами руды и топлива. В верхней правой ячейке лежит $(thing)фурма$(), труба для дутья. Фурму куют на $(l:metalworking/anvils)наковальне$()."))
            .image(L.of("The blast furnace interface", "Интерфейс доменной печи"), false, "tfc:textures/gui/book/gui/blast_furnace.png")
            .titled(
                L.of("The blast", "Дутьё"),
                L.of("A $(l:metalworking/bellows)Bellows$() on any of the four sides gives the blast. Without it the fuel burns at 1100 °C (charcoal) or 1250 °C (coke), and iron oxide melts at 1565 °C. With a full blast charcoal reaches 1600 °C and coke 1750 °C. Use it once the furnace has warmed.",
                    "$(l:metalworking/bellows)Меха$() с любой из четырёх сторон дают дутьё. Без него топливо горит при 1100 °C (древесный уголь) или 1250 °C (кокс), а оксид железа плавится при 1565 °C. С полным дутьём древесный уголь даёт 1600 °C, кокс 1750 °C. Включайте его, когда печь прогрелась."))
            .multiblockOf("tfc:full_blast_furnace", L.of("", ""), L.of("A full furnace with bellows and crucible.", "Полная печь с мехами и тиглем."))
            .text(
                L.of("Light it with a $(l:getting_started/firepit#firestarter)Fire Starter$(), $(thing)Flint and Pyrite$() or $(thing)Flint and Steel$(), and keep it fuelled. The ore warms, melts and runs out as $(thing)Pig Iron$().$(br2)The iron drips into a container for molten metal set below: a $(l:metalworking/crucible)Crucible$(), a mold, or a $(l:metalworking/steel)Bessemer converter$().",
                    "Разожгите её $(l:getting_started/firepit#firestarter)огнивом$(), $(thing)кремнем с пиритом$() или $(thing)кремнем и сталью$() и подкладывайте топливо. Руда прогревается, плавится и стекает $(thing)передельным чугуном$().$(br2)Железо капает в ёмкость для расплавленного металла под печью: $(l:metalworking/crucible)тигель$(), форму или $(l:metalworking/steel)конвертер Бессемера$()."))
            .titled(
                L.of("Dangers and details", "Опасности и подробности"),
                L.of("$(li)The furnace burns about 200 kW of fuel and sends three quarters of its carbon up as carbon monoxide, and the ore and the flux add carbon dioxide. Never run it in a shut room.$(li)Only a tenth of the sulfur of the fuel escapes as sulfur dioxide, as lime slag takes the rest. Each flux leaves a piece of slag.$(li)Limonite in the charge makes all the iron phosphoric: good for casting, brittle in steel.",
                    "$(li)Печь сжигает около 200 кВт топлива и отправляет вверх три четверти углерода угарным газом, а руда и флюс добавляют углекислый. Не запускайте её в закрытой комнате.$(li)Лишь десятая часть серы топлива выходит сернистым газом: остальное забирает известковый шлак. Каждый флюс оставляет кусок шлака.$(li)Лимонит в шихте делает фосфористым всё железо: оно годится для литья и хрупко в стали."));
    }

    private static Entry steel()
    {
        return Entry.of("metalworking/steel", CATEGORY, "tfc:metal/ingot/steel", 8, L.of("Steel", "Сталь"))
            .text(
                L.of("Tools and $(l:mechanics/armor)Armor$() are made of $(thing)Steel$() in four grades: plain steel, then the black, red and blue steels.$(br2)It starts from iron with its carbon adjusted. $(thing)Pig Iron$() from the $(l:metalworking/blast_furnace)Blast Furnace$() holds over 4% carbon, steel from 0.2 to 1%. A hammer cannot take carbon out of iron. It must be diluted or burnt out.",
                    "Из $(thing)стали$() делают орудия и $(l:mechanics/armor)броню$() в четырёх сортах: обычная сталь, затем чёрная, красная и синяя.$(br2)Она начинается с железа, у которого подогнан углерод. В $(thing)передельном чугуне$() из $(l:metalworking/blast_furnace)доменной печи$() больше 4% углерода, в стали от 0,2 до 1%. Молотом углерод из железа не выбить. Его разбавляют или выжигают."))
            .showsOnLastPage("tfc:metal/ingot/pig_iron", "tfc:metal/ingot/high_carbon_steel", "tfc:metal/ingot/steel")
            .titled(
                L.of("Diluting in the crucible", "Разбавление в тигле"),
                L.of("Melt $(thing)Wrought Iron$() with $(thing)Pig Iron$() in a $(l:metalworking/crucible)Crucible$() and the carbon averages out by weight. Pig iron at about 4 to 24% of the mix gives $(thing)Steel$(), 24 to 52% $(thing)High Carbon Steel$(), 52 to 99% $(thing)Cast Iron$(). The pot must pass 1535 °C: charcoal with a blast, or coal.",
                    "Расплавьте $(thing)кованое железо$() с $(thing)передельным чугуном$() в $(l:metalworking/crucible)тигле$(), и углерод усреднится по массе. Чугуна от 4 до 24% смеси даёт $(thing)сталь$(), от 24 до 52% $(thing)высокоуглеродистую сталь$(), от 52 до 99% $(thing)литейный чугун$(). Тигель должен пройти 1535 °C: нужен древесный уголь с дутьём или каменный уголь."))
            .titled(
                L.of("Burning in the converter", "Выжигание в конвертере"),
                L.of("The $(thing)Bessemer Converter$() burns the carbon out of liquid pig iron with air, and burns no fuel. Set it under a blast furnace, or pour iron in, and blow air through it by pipe. It wants about one mole of air a second and iron not cooler than 1350 °C, or the iron sets in the vessel. Blow too long and the iron itself burns.",
                    "$(thing)Конвертер Бессемера$() выжигает углерод из жидкого чугуна воздухом и не тратит топлива. Поставьте его под доменную печь или влейте железо и продувайте воздухом по трубе. Нужно около моля воздуха в секунду и железо не холоднее 1350 °C, иначе оно застынет в сосуде. Если продувать слишком долго, сгорит само железо."))
            .crafting("tfcpp:crafting/converter",
                L.of("The converter is seven $(thing)Wrought Iron Double Sheets$() round a $(thing)Fire Brick$().", "Конвертер: семь $(thing)двойных листов кованого железа$() вокруг $(thing)огнеупорного кирпича$()."))
            .titled(
                L.of("Reading the flame", "Как читать пламя"),
                L.of("$(li)Sparks and hardly any flame: the silicon burns.$(li)A long white flame: the carbon burns.$(li)The flame shortens: stop now for steel.$(li)The flame drops and brown smoke rises: the carbon is gone and the iron burns.$(br2)Water in the blast pipe blows the vessel apart: a litre of water becomes 1700 litres of steam. The lining wears and must be relaid with fire bricks while the vessel is empty.",
                    "$(li)Искры и почти нет пламени: горит кремний.$(li)Длинное белое пламя: горит углерод.$(li)Пламя укорачивается: для стали пора остановиться.$(li)Пламя падает и идёт бурый дым: углерода нет, горит железо.$(br2)Вода в трубе дутья разнесёт сосуд: литр воды даёт 1700 литров пара. Футеровка изнашивается, её меняют огнеупорными кирпичами в пустом сосуде."))
            .titled(
                L.of("Phosphorus", "Фосфор"),
                L.of("A new converter has an acid lining of fire clay, which cannot remove phosphorus. Iron from limonite stays phosphoric, and a tap of it is brittle $(thing)Cold Short Iron$(), only fit to be cast or poured in again. Only a basic lining of dolomite bricks, with lime thrown in, takes the phosphorus out, as Thomas and Gilchrist found in 1878.",
                    "У нового конвертера кислая футеровка из огнеупорной глины, и фосфор она не убирает. Железо из лимонита остаётся фосфористым, и выпуск из него это хрупкое $(thing)хладноломкое железо$(), которое можно лишь отлить или залить снова. Фосфор забирает только основная футеровка из доломитовых кирпичей с известью, как нашли Томас и Гилкрист в 1878 году."))
            .titled(
                L.of("Carbon and the hammer", "Углерод и молот"),
                L.of("Hammering does not remove carbon, so pig iron, cast iron and high carbon steel cannot be forged into steel on an anvil. $(thing)Cast Iron$() is brittle: it cannot be forged into rods or sheets, nor welded, only cast. High carbon steel becomes steel by blowing in the converter, or by melting it with wrought iron.",
                    "Ковка углерод не убирает, поэтому передельный чугун, литейный чугун и высокоуглеродистую сталь на наковальне в сталь не перековать. $(thing)Литейный чугун$() хрупок: из него не выковать прутья и листы и не сварить, его только отливают. Высокоуглеродистая сталь становится сталью продувкой в конвертере или сплавлением с кованым железом."))
            .titled(L.of("Black Steel", "Чёрная сталь"), L.of("$(thing)Black Steel$() is steel alloyed with other metals. First make $(thing)Weak Steel$() in a $(l:metalworking/crucible)Crucible$().", "$(thing)Чёрная сталь$() это сталь, сплавленная с другими металлами. Сначала в $(l:metalworking/crucible)тигле$() делают $(thing)слабую сталь$()."))
            .anchor("black_steel")
            .spotlight("tfc:metal/ingot/weak_steel", L.of("Weak Steel", "Слабая сталь"),
                L.of("$(bold)Needs:$()$(li)$(thing)Steel$(), 50 to 70 %$(li)$(thing)Nickel$(), 15 to 25 %$(li)$(thing)Black Bronze$(), 15 to 25 %$(br2)The liquid $(thing)Weak Steel$() is poured into ingot molds.", "$(bold)Нужно:$()$(li)$(thing)сталь$(), 50–70 %$(li)$(thing)никель$(), 15–25 %$(li)$(thing)чёрная бронза$(), 15–25 %$(br2)Жидкую $(thing)слабую сталь$() разливают в формы для слитков."))
            .showsOnLastPage("tfc:metal/ingot/weak_steel")
            .welding("tfc:welding/metal/ingot/high_carbon_black_steel",
                L.of("Weld $(thing)Weak Steel Ingots$() to $(thing)Pig Iron Ingots$() for $(thing)High Carbon Black Steel$(), and work that on an $(l:metalworking/anvils)Anvil$() into $(thing)Black Steel Ingots$().", "Сварите $(thing)слитки слабой стали$() со $(thing)слитками передельного чугуна$(): выйдет $(thing)высокоуглеродистая чёрная сталь$(). На $(l:metalworking/anvils)наковальне$() из неё куют $(thing)слитки чёрной стали$()."))
            .showsOnLastPage("tfc:metal/ingot/high_carbon_black_steel", "tfc:metal/ingot/black_steel")
            .text(
                L.of("$(thing)Black Steel$() makes tools and $(l:mechanics/armor)Armor$(), and is the base of $(l:metalworking/steel#blue_steel)Blue Steel$() and $(l:metalworking/steel#red_steel)Red Steel$().", "Из $(thing)чёрной стали$() делают орудия и $(l:mechanics/armor)броню$(), и она основа $(l:metalworking/steel#blue_steel)синей стали$() и $(l:metalworking/steel#red_steel)красной стали$()."))
            .titled(L.of("Blue Steel", "Синяя сталь"), L.of("$(thing)Blue Steel$() is, with $(l:metalworking/steel#red_steel)Red Steel$(), the best metal there is. As with black steel, first make an alloy, $(thing)Weak Blue Steel$(), in a $(l:metalworking/crucible)Crucible$().", "$(thing)Синяя сталь$() вместе с $(l:metalworking/steel#red_steel)красной сталью$() лучший из металлов. Как и с чёрной, сначала в $(l:metalworking/crucible)тигле$() делают сплав, $(thing)слабую синюю сталь$()."))
            .anchor("blue_steel")
            .spotlight("tfc:metal/ingot/weak_blue_steel", L.of("Weak Blue Steel", "Слабая синяя сталь"),
                L.of("$(bold)Needs:$()$(li)$(thing)Black Steel$(), 50 to 55 %$(li)$(thing)Steel$(), 20 to 25 %$(li)$(thing)Bismuth Bronze$(), 10 to 15 %$(li)$(thing)Sterling Silver$(), 10 to 15 %", "$(bold)Нужно:$()$(li)$(thing)чёрная сталь$(), 50–55 %$(li)$(thing)сталь$(), 20–25 %$(li)$(thing)висмутовая бронза$(), 10–15 %$(li)$(thing)стерлинговое серебро$(), 10–15 %"))
            .showsOnLastPage("tfc:metal/ingot/weak_blue_steel")
            .welding("tfc:welding/metal/ingot/high_carbon_blue_steel",
                L.of("Weld $(thing)Weak Blue Steel Ingots$() to $(l:metalworking/steel#black_steel)Black Steel Ingots$() for $(thing)High Carbon Blue Steel$(), and work that on an $(l:metalworking/anvils)Anvil$() into $(thing)Blue Steel Ingots$().", "Сварите $(thing)слитки слабой синей стали$() со $(l:metalworking/steel#black_steel)слитками чёрной стали$(): выйдет $(thing)высокоуглеродистая синяя сталь$(). На $(l:metalworking/anvils)наковальне$() из неё куют $(thing)слитки синей стали$()."))
            .showsOnLastPage("tfc:metal/ingot/high_carbon_blue_steel", "tfc:metal/ingot/blue_steel")
            .text(
                L.of("$(thing)Blue Steel$() yields tools, $(l:mechanics/armor)Armor$() and a bucket that holds $(cfg:metalBucketCapacity) of any liquid, $(6)Lava$() included.", "$(thing)Синяя сталь$() даёт орудия, $(l:mechanics/armor)броню$() и ведро, в которое входит $(cfg:metalBucketCapacity) любой жидкости, в том числе $(6)лавы$()."))
            .titled(L.of("Red Steel", "Красная сталь"), L.of("$(thing)Red Steel$() is, with $(l:metalworking/steel#blue_steel)Blue Steel$(), the best metal there is. First make an alloy, $(thing)Weak Red Steel$(), in a $(l:metalworking/crucible)Crucible$().", "$(thing)Красная сталь$() вместе с $(l:metalworking/steel#blue_steel)синей сталью$() лучший из металлов. Сначала в $(l:metalworking/crucible)тигле$() делают сплав, $(thing)слабую красную сталь$()."))
            .anchor("red_steel")
            .spotlight("tfc:metal/ingot/weak_red_steel", L.of("Weak Red Steel", "Слабая красная сталь"),
                L.of("$(bold)Needs:$()$(li)$(thing)Black Steel$(), 50 to 55 %$(li)$(thing)Steel$(), 20 to 25 %$(li)$(thing)Brass$(), 10 to 15 %$(li)$(thing)Rose Gold$(), 10 to 15 %", "$(bold)Нужно:$()$(li)$(thing)чёрная сталь$(), 50–55 %$(li)$(thing)сталь$(), 20–25 %$(li)$(thing)латунь$(), 10–15 %$(li)$(thing)розовое золото$(), 10–15 %"))
            .showsOnLastPage("tfc:metal/ingot/weak_red_steel")
            .welding("tfc:welding/metal/ingot/high_carbon_red_steel",
                L.of("Weld $(thing)Weak Red Steel Ingots$() to $(l:metalworking/steel#black_steel)Black Steel Ingots$() for $(thing)High Carbon Red Steel$(), and work that on an $(l:metalworking/anvils)Anvil$() into $(thing)Red Steel Ingots$().", "Сварите $(thing)слитки слабой красной стали$() со $(l:metalworking/steel#black_steel)слитками чёрной стали$(): выйдет $(thing)высокоуглеродистая красная сталь$(). На $(l:metalworking/anvils)наковальне$() из неё куют $(thing)слитки красной стали$()."))
            .showsOnLastPage("tfc:metal/ingot/high_carbon_red_steel", "tfc:metal/ingot/red_steel")
            .text(
                L.of("$(thing)Red Steel$() yields tools, $(l:mechanics/armor)Armor$() and a bucket that holds $(cfg:metalBucketCapacity) of any liquid, $(6)Lava$() included.", "$(thing)Красная сталь$() даёт орудия, $(l:mechanics/armor)броню$() и ведро, в которое входит $(cfg:metalBucketCapacity) любой жидкости, в том числе $(6)лавы$()."));
    }

    private static Entry charcoalForge()
    {
        return Entry.of("metalworking/charcoal_forge", CATEGORY, "tfc:textures/block/molten_lit.png", 3, L.of("Charcoal Forge", "Угольная кузница"))
            .text(
                L.of("The $(thing)Charcoal Forge$() heats and melts things. Items are heated in it for the $(l:metalworking/anvils)Anvil$(), and a $(l:metalworking/crucible)Crucible$() stands on it. You build it by ringing a $(l:metalworking/charcoal_pit#charcoal_pile)charcoal pile$() seven or eight layers deep with five blocks of $(thing)stone$(), and then lighting the pile.",
                    "$(thing)Угольная кузница$() нагревает и плавит. В ней греют заготовки для $(l:metalworking/anvils)наковальни$(), и на ней стоит $(l:metalworking/crucible)тигель$(). Это пять блоков $(thing)камня$() вокруг $(l:metalworking/charcoal_pit#charcoal_pile)кучи древесного угля$() в 7 или 8 слоёв, которую поджигают."))
            .multiblockOf("tfc:charcoal_forge", L.of("A Charcoal Forge", "Угольная кузница"), L.of("A finished forge, ready to light.", "Готовая кузница, можно поджигать."))
            .text(
                L.of("The block above must be air or a crucible. TFC++ asks neither for a chimney nor for the sky: a forge burns without them. But a chimney is what carries the fumes away. A forge in a shut room fills it with smoke and carbon monoxide, and with sulfur dioxide if it burns coal. Build it under open sky, or ventilate the room.",
                    "Блок сверху должен быть воздухом или тиглем. TFC++ не требует ни трубы, ни неба: кузница горит и без них. Но труба уносит дым. Кузница в закрытой комнате наполняет её дымом и угарным газом, а на каменном угле ещё и сернистым газом. Ставьте её под открытым небом или проветривайте комнату."))
            .text(
                L.of("A $(l:metalworking/bellows)Bellows$() can stand one block up and beside the forge. The fire goes out when the air has less than 12% oxygen.",
                    "$(l:metalworking/bellows)Меха$() можно поставить на блок выше и сбоку от кузницы. Огонь гаснет, когда в воздухе меньше 12% кислорода."))
            .image(L.of("The forge's interface.", "Интерфейс кузницы."), false, "tfc:textures/gui/book/gui/charcoal_forge.png")
            .titled(
                L.of("Fuel", "Топливо"),
                L.of("The five lower slots hold fuel, and the forge burns one piece at a time:$(li)Charcoal 1100 °C$(li)Lignite 1000 °C$(li)Coal 1200 °C$(li)Lignite char 1100 °C$(li)Coke 1250 °C$(br)The five upper slots heat items up to the gauge. The slots at the side hold vessels and molds, which fill with the metal that melts.",
                    "В пяти нижних ячейках лежит топливо, кузница сжигает по куску. Температура:$(li)древесный уголь 1100 °C$(li)лигнит 1000 °C$(li)каменный уголь 1200 °C$(li)буроугольный полукокс 1100 °C$(li)кокс 1250 °C$(br)Пять верхних ячеек греют предметы до значения на шкале. В боковые ячейки ставят сосуды и формы, и в них стекает расплавленный металл."))
            .titled(
                L.of("Blast and rain", "Дутьё и дождь"),
                L.of("A bellows adds at most 500 °C to the fuel and doubles its burn: charcoal reaches 1600 °C. Iron, steel and fire clay need that. Rain falling on an open forge cools it by 300 °C and doubles the burn too.$(br2)Coal, lignite and coke carry sulfur, and every piece they burn releases sulfur dioxide.",
                    "Меха добавляют к топливу не больше 500 °C и вдвое ускоряют горение: древесный уголь доходит до 1600 °C. Железу, стали и шамоту это нужно. Дождь на открытой кузнице остужает её на 300 °C и тоже удваивает горение.$(br2)В каменном угле, лигните и коксе есть сера, и каждый сгоревший кусок выпускает сернистый газ."));
    }

    private static Entry charcoalPit()
    {
        final JsonObject pit = Structure.layers(new String[][] {{"DDD", "DDD", "DDD"}, {"DDD", "D0D", "DDD"}, {"DDD", "DDD", "DDD"}}, "D", "tfc:dirt/aridisol", "0", "tfc:log_pile");
        return Entry.of("metalworking/charcoal_pit", CATEGORY, "minecraft:charcoal", 2, L.of("Charcoal Pit", "Угольная яма"))
            .text(
                L.of("The $(thing)Charcoal Pit$() makes $(thing)Charcoal$() from logs. Logs are stacked in $(thing)Log Piles$(): $(item)$(k:key.use)$() with $(item)$(k:key.sneak)$() and a log in hand places a pile. More logs go in with $(item)$(k:key.use)$() holding a log, or into the pile's window opened with any other item.",
                    "$(thing)Угольная яма$() делает $(thing)древесный уголь$() из брёвен. Брёвна складывают в $(thing)поленницы$(): $(item)$(k:key.use)$() с $(item)$(k:key.sneak)$() и бревном в руке ставит поленницу. Ещё брёвна кладут $(item)$(k:key.use)$() с бревном в руке или в окно поленницы, открытое любым другим предметом."))
            .showsOnLastPage("minecraft:charcoal")
            .multiblock(Structure.block("tfc:log_pile"), L.of("The Log Pile", "Поленница"),
                L.of("A log pile needs a solid block to stand on, and burns easily.", "Поленнице нужна твёрдая опора, и она легко горит."))
            .text(
                L.of("Close the piles in on every side with solid blocks that do not burn: earth is usual. Light one pile and cover it. Smoke rising from the heap shows that it caught. The charcoal you get follows the logs inside.",
                    "Закройте поленницы со всех сторон твёрдыми негорючими блоками: обычно землёй. Подожгите одну поленницу и накройте. Дым над кучей значит, что она занялась. Угля выйдет столько, сколько в ней было брёвен."))
            .multiblock(pit, L.of("A Pit", "Яма"), L.of("One possible pit: earth all round the pile.", "Одна из возможных ям: земля вокруг поленницы."))
            .text(
                L.of("When the pit has burnt out and stopped smoking, $(thing)Charcoal Piles$() remain, up to 8 layers each. Dig them with a shovel for charcoal. A pile can be added to or placed with $(item)$(k:key.use)$().",
                    "Когда яма выгорела и дым прекратился, остаются $(thing)кучи древесного угля$() до 8 слоёв. Копайте их лопатой, это и есть уголь. К куче можно добавлять уголь, и её можно ставить $(item)$(k:key.use)$()."))
            .anchor("charcoal_pile")
            .cycle(L.of("The charcoal pile, of 1 to 8 layers.", "Куча угля, от 1 до 8 слоёв."),
                Structure.block("tfc:charcoal_pile[layers=1]"), Structure.block("tfc:charcoal_pile[layers=2]"), Structure.block("tfc:charcoal_pile[layers=3]"), Structure.block("tfc:charcoal_pile[layers=4]"),
                Structure.block("tfc:charcoal_pile[layers=5]"), Structure.block("tfc:charcoal_pile[layers=6]"), Structure.block("tfc:charcoal_pile[layers=7]"), Structure.block("tfc:charcoal_pile[layers=8]"))
            .titled(
                L.of("Danger", "Опасность"),
                L.of("The pit burns without air, and is the dirtiest fire there is: up to 30% of its carbon leaves as carbon monoxide, with some methane from the wood. The turf is never airtight. Even under thick cover a tenth of the gas gets through, and people have died sleeping beside charcoal pits. Burn it in the open, and never sleep next to it.",
                    "Яма горит без воздуха и это самый грязный из огней: до 30% её углерода уходит угарным газом, да ещё немного метана из древесины. Дёрн никогда не герметичен. Даже под толстым покрытием десятая часть газа просачивается, и люди умирали, засыпая возле угольных ям. Жгите её на открытом месте и никогда не спите рядом."));
    }

    private static Entry flux()
    {
        return Entry.of("metalworking/flux", CATEGORY, "tfc:powder/flux", 1, L.of("Flux", "Флюс"))
            .text(
                L.of("Flux is a powder needed for $(l:metalworking/anvils#welding)Welding$() and as the catalyst of a $(l:metalworking/blast_furnace)Blast Furnace$(). Burnt to lime, it also serves glass, mortar, limewater and leather. It is carbonate stone or shell ground in a $(l:crafting/quern)Quern$().$(br2)$(thing)Borax$() is not that kind of flux: it gives $(thing)Borax Flux$(), a different powder.",
                    "Флюс это порошок, нужный для $(l:metalworking/anvils#welding)сварки$() и как катализатор в $(l:metalworking/blast_furnace)доменной печи$(). Обожжённый до извести, он идёт ещё на стекло, раствор, известковую воду и кожу. Это карбонатный камень или раковины, смолотые в $(l:crafting/quern)жёрнове$().$(br2)$(thing)Бура$() не флюс в этом смысле: из неё получается $(thing)флюс из буры$(), другой порошок."))
            .showsOnLastPage("tfc:powder/flux")
            .quern("tfc:quern/powder/flux",
                L.of("Limestone, dolomite, chalk and marble grind to flux. So do scutes, clams, mollusks and the remains of shellfish.", "Известняк, доломит, мел и мрамор мелются во флюс. Так же скутеллы, моллюски, ракушки и остатки панцирных."))
            .quern("tfc:quern/powder/flux_from_borax",
                L.of("Borax ore grinds to six pieces of $(thing)Borax Flux$(). It welds, but it does not burn to lime, and the blast furnace refuses it. Borax is also the boron of borosilicate glass.", "Руда буры мелется в шесть порошков $(thing)флюса из буры$(). Он годится для сварки, но не обжигается в известь, и доменная печь его не берёт. Бура ещё и бор борсиликатного стекла."))
            .heating("tfc:heating/powder/lime",
                L.of("Burnt at 840 °C, flux becomes $(thing)Lime$(): CaCO3 gives CaO and CO2, and nearly half the weight leaves as carbon dioxide. Burn it in the open.", "При 840 °C флюс обжигается в $(thing)известь$(): CaCO3 даёт CaO и CO2, и почти половина веса уходит углекислым газом. Обжигайте на открытом месте."));
    }

    private static Entry fireClay()
    {
        return Entry.of("metalworking/fire_clay", CATEGORY, "tfc:fire_clay", 12, L.of("Fire Clay", "Огнеупорная глина"))
            .text(
                L.of("Fire clay is clay that stands more heat. It is used for the things that get hottest: the crucible, fire bricks, fire ingot molds, channels and mold tables. There are few uses, and every one matters.",
                    "Огнеупорная глина выдерживает больше жара, чем обычная. Из неё делают то, что греется сильнее всего: тигель, огнеупорные кирпичи, огнеупорные формы для слитков, жёлоба и литейные столы. Применений немного, и каждое важно."))
            .crafting("tfc:crafting/fire_clay",
                L.of("It is made of $(l:the_world/ores_and_minerals#graphite)Graphite$() powder ground in a $(l:crafting/quern)Quern$(), and $(l:the_world/ores_and_minerals#kaolinite)Kaolinite$() powder.", "Её делают из порошка $(l:the_world/ores_and_minerals#graphite)графита$(), смолотого в $(l:crafting/quern)жёрнове$(), и порошка $(l:the_world/ores_and_minerals#kaolinite)каолинита$()."))
            .heating("tfc:heating/powder/kaolinite",
                L.of("Kaolinite powder comes from heating $(l:the_world/ores_and_minerals#kaolinite)Kaolin Clay$() to 500 °C, but badly: only one piece in five becomes powder.", "Порошок каолинита получают, нагревая $(l:the_world/ores_and_minerals#kaolinite)каолиновую глину$() до 500 °C, но плохо: порошком становится лишь каждый пятый кусок."))
            .knapping("tfc:knapping/ceramic/unfired_crucible",
                L.of("The unfired $(l:metalworking/crucible)Crucible$() is knapped from fire clay.", "Необожжённый $(l:metalworking/crucible)тигель$() выбивают из огнеупорной глины."))
            .anchor("crucible")
            .knapping("tfc:knapping/ceramic/unfired_fire_brick",
                L.of("A $(l:metalworking/blast_furnace)Blast Furnace$() stack takes only fire bricks, reinforced with iron.", "Шахта $(l:metalworking/blast_furnace)доменной печи$() строится только из огнеупорных кирпичей, укреплённых железом."))
            .anchor("fire_bricks")
            .knapping("tfc:knapping/ceramic/unfired_fire_ingot_mold",
                L.of("A $(thing)Fire Ingot Mold$() is the stronger $(l:getting_started/pottery#mold)Ingot Mold$(): it breaks once in 100 pours, a plain one once in 10.", "$(thing)Огнеупорная форма для слитков$() прочнее обычной $(l:getting_started/pottery#mold)формы$(): она ломается раз в 100 отливок, обычная раз в 10."))
            .titled(
                L.of("Firing", "Обжиг"),
                L.of("Fire clay ware needs 1300 °C. That is hotter than a pit kiln gets (about 900 °C), and than coal burns in a forge without blast (1200 °C). Fire it in a $(l:metalworking/charcoal_forge)Charcoal Forge$() with a $(l:metalworking/bellows)Bellows$(). Ordinary clay ware needs only 850 °C.",
                    "Изделия из огнеупорной глины обжигают при 1300 °C. Это жарче, чем в земляной печи (около 900 °C) и чем горит каменный уголь в кузнице без дутья (1200 °C). Обжигайте в $(l:metalworking/charcoal_forge)угольной кузнице$() с $(l:metalworking/bellows)мехами$(). Обычной керамике хватает 850 °C."));
    }

    private static Entry crucible()
    {
        final JsonObject forge = Structure.layers(new String[][] {{"   ", " C ", "   "}, {"GGG", "G0G", "GGG"}}, "C", "tfc:crucible", "0", "tfc:charcoal_forge[heat_level=7]", "G", "tfc:rock/bricks/granite");
        return Entry.of("metalworking/crucible", CATEGORY, "tfc:crucible", 9, L.of("Crucible", "Тигель"))
            .text(
                L.of("A $(thing)Crucible$() is a ceramic pot for melting metal and mixing $(l:metalworking/crucible#advanced_alloying)Alloys$() in exact proportions: it is better for that than a $(l:getting_started/primitive_alloys)Small Vessel$(). It is made of $(l:metalworking/fire_clay)Fire Clay$(), knapped to shape and then fired.",
                    "$(thing)Тигель$() это керамический горшок для плавки металла и смешивания $(l:metalworking/crucible#advanced_alloying)сплавов$() в точных долях: для этого он лучше $(l:getting_started/primitive_alloys)малого сосуда$(). Его делают из $(l:metalworking/fire_clay)огнеупорной глины$(): выбивают форму и обжигают."))
            .knapping("tfc:knapping/ceramic/unfired_crucible", L.of("Knapping an unfired crucible.", "Выбивание необожжённого тигля."))
            .showsOnLastPage("tfc:ceramic/unfired_crucible")
            .heating("tfc:heating/crucible",
                L.of("Fire it at 1300 °C: in a $(l:metalworking/charcoal_forge)Charcoal Forge$() with bellows, not in a pit kiln. At work it needs heat from below, usually a forge, or from above, from a $(l:metalworking/blast_furnace)Blast Furnace$().", "Обжигайте при 1300 °C: в $(l:metalworking/charcoal_forge)угольной кузнице$() с мехами, не в земляной печи. В работе ему нужен жар снизу, обычно от кузницы, или сверху, от $(l:metalworking/blast_furnace)доменной печи$()."))
            .showsOnLastPage("tfc:crucible")
            .multiblock(forge, L.of("", ""), L.of("A crucible on a charcoal forge.", "Тигель на угольной кузнице."))
            .titled(L.of("Alloying", "Сплавы"), L.of("Using the crucible opens its interface. The top shows the metal inside: the first named is what you would draw off now, the others make up the mix.",
                    "Использование тигля открывает его интерфейс. Вверху показан металл внутри: первый в списке выйдет, если забрать сейчас, остальные составляют смесь."))
            .anchor("advanced_alloying")
            .image(L.of("The crucible's interface", "Интерфейс тигля"), false, "tfc:textures/gui/book/gui/crucible.png")
            .text(
                L.of("Nine slots take items to melt, and the liquid goes into the pot. Molds and vessels of molten metal drain into it slowly, which gives exact mixes: hold $(item)$(k:key.sneak)$() over one to hurry it. A mold or vessel in the output slot is filled from the pot.",
                    "Девять ячеек принимают предметы для плавки, жидкость стекает в горшок. Формы и сосуды с расплавом медленно сливаются в него, так получаются точные смеси: удерживайте $(item)$(k:key.sneak)$() над ними, чтобы ускорить. Форма или сосуд в выходной ячейке наполняются из горшка."))
            .titled(
                L.of("What melts", "Что плавится"),
                L.of("Native metals, malachite and cassiterite melt into metal. Sulfide ores roast instead, and give off fumes where the pot stands: tetrahedrite gives arsenical copper, bismuthinite gives bismuth, sphalerite gives a calcine and no zinc, which needs a retort. Iron ore melts only into an oxide that cannot be cast.",
                    "Самородные металлы, малахит и касситерит плавятся в металл. Сульфидные руды вместо этого обжигаются и выпускают дым там, где стоит горшок: тетраэдрит даёт мышьяковистую медь, висмутин висмут, сфалерит огарок без цинка, цинк требует реторты. Железная руда плавится только в оксид, который не отлить."))
            .text(
                L.of("The temperature gauge follows the heat source below or above. Metal can be drawn off while it is still molten. A broken crucible keeps its contents, so a full one can be carried. Wrought iron melts at 1535 °C and steel at 1520 °C.",
                    "Шкала температуры следует за источником жара снизу или сверху. Металл можно забирать, пока он расплавлен. Разбитый тигель сохраняет содержимое, так что полный можно переносить. Кованое железо плавится при 1535 °C, сталь при 1520 °C."));
    }

    private static Entry bellows()
    {
        return Entry.of("metalworking/bellows", CATEGORY, "tfc:bellows", 10, L.of("Bellows", "Кузнечные меха"))
            .text(
                L.of("A $(thing)Bellows$() pushes air into the device it faces, or into one a block lower than that: a $(l:getting_started/firepit)Firepit$(), a $(l:metalworking/charcoal_forge)Charcoal Forge$(), a blast furnace. More air makes the fuel burn hotter and twice as fast, and gives off twice the gas.$(br2)The blast adds at most 500 °C to the fuel. A forge on charcoal tops out at 1600 °C, so melting steel is hard, as it should be.",
                    "$(thing)Меха$() вдувают воздух в устройство перед собой или перед собой на блок ниже: $(l:getting_started/firepit)костёр$(), $(l:metalworking/charcoal_forge)угольную кузницу$(), доменную печь. Больше воздуха значит жарче и вдвое быстрее горение, и вдвое больше газа.$(br2)Дутьё добавляет к топливу не больше 500 °C. Кузница на древесном угле упирается в 1600 °C, и расплавить сталь трудно, как и должно быть."))
            .crafting("tfc:crafting/bellows",
                L.of("Set the bellows facing the device and use it. Each push raises the highest temperature for a short while. A crankshaft can work it. On a mold table it cools the metal faster.", "Поставьте меха лицом к устройству и используйте их. Каждый толчок ненадолго поднимает предел температуры. Их может качать коленвал. На литейном столе они быстрее остужают металл."));
    }

    private static Entry anvils()
    {
        return Entry.of("metalworking/anvils", CATEGORY, "tfc:metal/anvil/copper", 0, L.of("Anvils", "Наковальни"))
            .text(
                L.of("An anvil is the main tool of the smith: it shapes ingots and welds them. Anvils serve two jobs, $(l:metalworking/anvils#working)Working$(), which forms one piece of metal into another, and $(l:metalworking/anvils#welding)Welding$(), which fuses two pieces into one.",
                    "Наковальня главный инструмент кузнеца: на ней формуют слитки и сваривают их. У неё две работы: $(l:metalworking/anvils#working)ковка$(), из одного куска металла получается другой, и $(l:metalworking/anvils#welding)сварка$(), два куска сплавляются в один."))
            .showsOnLastPage("tag:tfc:anvils")
            .multiblock(Structure.block("#tfc:anvils"), L.of("", ""), L.of("Anvils of every metal.", "Наковальни из любых металлов."))
            .crafting("tfc:crafting/metal/anvil/copper",
                L.of("An anvil is crafted from $(thing)Double Ingots$() of its metal. For the first one, $(l:metalworking/anvils#welding)weld$() the double ingots on a $(l:getting_started/primitive_anvils)Stone Anvil$().", "Наковальню делают из $(thing)двойных слитков$() её металла. Для первой двойные слитки $(l:metalworking/anvils#welding)сваривают$() на $(l:getting_started/primitive_anvils)каменной наковальне$()."))
            .text(
                L.of("Every anvil has a $(thing)Tier$(), which says what it can work and weld. It works metals of its own tier and welds those one tier up.$(li)$(bold)VI$(): Blue and Red Steel$(li)$(bold)V$(): Black Steel$(li)$(bold)IV$(): Steel$(li)$(bold)III$(): Wrought Iron$(li)$(bold)II$(): Bronze, Black Bronze, Bismuth Bronze$(li)$(bold)I$(): Copper, also arsenical$(li)$(bold)0$(): Stone",
                    "У каждой наковальни есть $(thing)ярус$(): он говорит, что на ней можно ковать и сваривать. Она кует металлы своего яруса и сваривает металлы на ярус выше.$(li)$(bold)VI$(): синяя и красная сталь$(li)$(bold)V$(): чёрная сталь$(li)$(bold)IV$(): сталь$(li)$(bold)III$(): кованое железо$(li)$(bold)II$(): бронза, чёрная бронза, висмутовая бронза$(li)$(bold)I$(): медь, в том числе мышьяковистая$(li)$(bold)0$(): камень"))
            .anchor("tiers")
            .titled(L.of("Working", "Ковка"), L.of("To work an item, use the anvil to open its interface. The item goes in the right input slot. You also need a hammer, in the hammer slot or in your main hand; it wears as you work.",
                    "Чтобы ковать, используйте наковальню: откроется интерфейс. Предмет кладут в правую входную ячейку. Нужен ещё молот, в его ячейке или в основной руке: он изнашивается при работе."))
            .anchor("working")
            .image(L.of("The anvil interface.", "Интерфейс наковальни."), false, "tfc:textures/gui/book/gui/anvil_empty.png")
            .text(
                L.of("Then choose a $(thing)Plan$(), which says what item you make. $(item)$(k:key.attack)$() the scroll button and pick the item. The interface returns with the scroll showing your plan, and the $(thing)Rules$() and $(thing)Target$() filled in.",
                    "Затем выберите $(thing)чертёж$(): что вы делаете. Нажмите $(item)$(k:key.attack)$() на кнопке свитка и выберите предмет. Интерфейс вернётся со свитком, а $(thing)правила$() и $(thing)цель$() будут заполнены."))
            .image(L.of("After choosing a pickaxe.", "После выбора кирки."), false, "tfc:textures/gui/book/gui/anvil_in_use.png")
            .titled(L.of("Targets", "Цель"), L.of("The middle bar has two markers. The $(2)green$() one is your progress, the $(4)red$() one is the target. Bring progress onto the target with the green and red action buttons, each of which moves it by a set step.",
                    "В середине полоса с двумя метками. $(2)Зелёная$() это ваш прогресс, $(4)красная$() цель. Подведите прогресс к цели зелёными и красными кнопками действий, каждая сдвигает его на свой шаг."))
            .text(
                L.of("$(2)Green$() actions always move the progress $(bold)right$() and $(4)red$() ones $(bold)left$(). If it leaves the bar, the item is overworked and lost. And at the end the $(thing)rules$() must hold as well.",
                    "$(2)Зелёные$() действия всегда двигают прогресс $(bold)вправо$(), $(4)красные$() $(bold)влево$(). Если он выйдет за полосу, заготовка перекована и пропала. И в конце должны выполняться $(thing)правила$()."))
            .titled(L.of("Working Rules", "Правила ковки"), L.of("Rules are the two or three symbols over the bar: actions that must fall at set moments. For example $(2)Bend Second Last$() means the second action from the end has to be a $(2)Bend$().",
                    "$(thing)Правила$() это две или три иконки над полосой: действия, которые должны прийти в заданный момент. Например, $(2)Изгиб предпоследним$() значит, что предпоследнее действие должно быть $(2)изгибом$()."))
            .text(
                L.of("Your last three actions show under the rules, and a rule turns green when met. You succeed when all are met. Mind the $(l:core_mechanics/heating)temperature$() too: metal can be worked only while the tooltip says $(bold)Can Work$(). Take it out and reheat it as often as needed.",
                    "Три последних действия видны под правилами, выполненное правило зеленеет. Вы справились, когда выполнены все. Следите и за $(l:core_mechanics/heating)температурой$(): металл куётся, только пока в подсказке есть $(bold)Можно ковать$(). Вынимайте и догревайте сколько нужно."))
            .titled(
                L.of("Forging bonuses", "Бонусы ковки"),
                L.of("Working takes many steps, and efficiency is rewarded. Some items, such as tool heads, made in few steps carry a $(thing)Forging Bonus$() into the tool built from them.",
                    "Ковка требует многих шагов, и аккуратность вознаграждается. Некоторые заготовки, например головки орудий, сделанные за малое число шагов, передают $(thing)бонус ковки$() орудию, собранному из них."))
            .spotlight("tfc:metal/pickaxe/wrought_iron[tfc:forging_bonus={type:\"perfect\"}]", L.of("Perfectly Forged", "Идеальная ковка"),
                L.of("Best first:$(li)perfectly forged$(li)expertly forged$(li)well forged$(li)poorly forged$(br2)A bonus makes a tool last longer, dig faster or hit harder, depending on the tool.", "Сначала лучшая:$(li)идеальная ковка$(li)мастерская ковка$(li)хорошая ковка$(li)плохая ковка$(br2)Бонус делает орудие долговечнее, быстрее или сильнее в бою, смотря какое."))
            .titled(
                L.of("What is not forged", "Что не куётся"),
                L.of("$(thing)Cast Iron$() is brittle: it cannot be forged into rods or sheets nor welded, only cast.$(br2)Copper from tetrahedrite is $(thing)arsenical$(). It is forged and welded as copper, on a copper anvil made of its own double ingot, but what is made of it carries a mark, and melts back into arsenical copper, never into pure copper.",
                    "$(thing)Литейный чугун$() хрупок: из него не выковать прутья и листы и не сварить, его только отливают.$(br2)Медь из тетраэдрита $(thing)мышьяковистая$(). Её куют и сваривают как медь, на медной наковальне из её же двойного слитка, но сделанное из неё несёт метку и переплавляется в мышьяковистую медь, а не в чистую."))
            .titled(
                L.of("Welding", "Сварка"),
                L.of("Welding fuses two items into a new one. It is the same on a stone anvil as on a metal one.$(br2)Put both items on the anvil, using them on it or by the interface, in the two left slots.",
                    "Сварка сплавляет два предмета в новый. На каменной наковальне она такая же, как на металлической.$(br2)Положите оба предмета на наковальню, использовав их на ней или через интерфейс, в две левые ячейки."))
            .anchor("welding")
            .text(
                L.of("You also need at least one $(l:metalworking/flux)Flux$() on the anvil, plain or borax. Then, while both items are $(l:core_mechanics/heating)hot enough$() and the tooltip says $(bold)Can Weld$(), strike the anvil with any $(thing)Hammer$(). The items fuse. Take them out with an empty hand and $(item)$(k:key.use)$().",
                    "Нужен ещё хотя бы один $(l:metalworking/flux)флюс$() на наковальне, обычный или из буры. Затем, пока оба предмета $(l:core_mechanics/heating)достаточно горячи$() и в подсказке есть $(bold)Можно сваривать$(), ударьте по наковальне любым $(thing)молотом$(). Предметы сплавятся. Заберите их пустой рукой, $(item)$(k:key.use)$()."));
    }

    private MetalworkingGuide() {}
}
