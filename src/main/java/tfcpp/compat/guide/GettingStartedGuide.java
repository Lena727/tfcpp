package tfcpp.compat.guide;

import java.util.List;
import com.google.gson.JsonObject;

import tfcpp.compat.guide.Pages.Structure;

/**
 * TFC's Getting Started chapters that TFC++ made wrong or incomplete: the firepit, the pit kiln, pottery, primitive alloys and the first ores.
 * <p>
 * The main change is heat. A pit kiln reaches about 900 C and not the 1400 C that TFC's own text reads out of its config, so copper is first melted in a forge, and every temperature given here is the one the code uses.
 */
final class GettingStartedGuide
{
    private static final String CATEGORY = "tfc:getting_started";

    static List<Entry> entries()
    {
        return List.of(findingOres(), firepit(), pitKiln(), pottery(), primitiveAlloys());
    }

    private static Entry firepit()
    {
        final JsonObject lit = Structure.block("tfc:firepit[lit=true]");
        return Entry.of("getting_started/firepit", CATEGORY, "tfc:firepit", 1, L.of("Pits of Fire", "Ямы с огнём"))
            .text(
                L.of("$(thing)Fire$() is the first great advance. You need a $(thing)Firestarter$(). Press and keep pressing $(item)$(k:key.use)$() against the ground: after a short wait smoke appears, then flame. A few tries may be needed.",
                    "$(thing)Огонь$() первое великое достижение. Чтобы добыть его, нужно $(thing)огниво$(): зажмите $(item)$(k:key.use)$() на земле. Через несколько мгновений пойдёт дым, затем огонь. Иногда нужно несколько попыток."))
            .anchor("firestarter")
            .showsOnLastPage("tfc:firepit")
            .crafting("tfc:crafting/firestarter", L.of("A firestarter is crafted from two sticks.", "Огниво делают из двух палок."))
            .titled(L.of("Firepit", "Костёр"), L.of("With a firestarter you can build a $(thing)Firepit$(): one $(thing)log$(), three $(thing)sticks$() and, if you like, up to five pieces of $(thing)kindling$() (paper, straw, pinecones), each adding 10% to the chance of success. Throw all of it down onto a single block ($(item)$(k:key.drop)$()), then strike the firestarter on that block while the items hang over it.",
                    "С огнивом можно построить $(thing)костёр$(): одно $(thing)бревно$(), три $(thing)палки$() и по желанию до пяти $(thing)растопок$() (бумага, солома, шишки), каждая прибавляет 10% к шансу успеха. Бросьте всё на один блок ($(item)$(k:key.drop)$()), затем используйте огниво на блок, над которым висят предметы."))
            .multiblock(lit, L.of("", ""), L.of("If it worked, a firepit appears.", "Если получилось, появится костёр."))
            .text(
                L.of("Using the firepit opens its screen. Four $(thing)fuel$() slots run down its left side. Feed them from the top slot with logs, peat or stick bundles; the lowest slot is the one burning. A gauge shows the $(thing)temperature$(), and on the right is a slot for an item to be $(l:core_mechanics/heating)heated$().",
                    "Использование костра открывает его экран. Слева четыре ячейки $(thing)топлива$(): брёвна, торф и вязанки хвороста кладут в верхнюю, горят они с нижней. Шкала показывает $(thing)температуру$(), справа ячейка для предмета, который $(l:core_mechanics/heating)нагревают$()."))
            .image(L.of("The firepit screen", "Экран костра"), false, "tfc:textures/gui/book/gui/firepit.png")
            .heating("tfc:heating/torch_from_stick",
                L.of("Much can be made by heating things in a firepit. A stick gives two $(thing)Torches$(), which burn out in time and are lit again with a firestarter or another torch.", "В костре многое делается нагревом. Из палки выходят два $(thing)факела$(), они со временем прогорают, и их зажигают огнивом или другим факелом."))
            .showsOnLastPage("tfc:torch", "minecraft:torch")
            .heating("tfc:heating/food/cooked_cod",
                L.of("The firepit also cooks food. All raw meat and dough can be cooked on it, and cooked food keeps longer (see $(l:food/decay)here$()).", "На костре ещё и готовят. Можно жарить любое сырое мясо и тесто, готовое хранится дольше (смотрите $(l:food/decay)здесь$())."))
            .text(
                L.of("A shovel puts the fire out. A $(thing)Wrought Iron Grill$() turns the firepit into a $(l:food/grill)Grill$(), and a $(thing)Ceramic Pot$() into a $(l:food/pot)Pot$(). To take either off, $(item)$(k:key.use)$() while holding $(item)$(k:key.sneak)$(), and do not reach for a hot one.",
                    "Лопата тушит огонь. $(thing)Решётка из кованого железа$() превращает костёр в $(l:food/grill)гриль$(), а $(thing)керамический горшок$() в $(l:food/pot)котёл$(). Чтобы снять любую из них, $(item)$(k:key.use)$() с зажатым $(item)$(k:key.sneak)$(), и не хватайтесь за горячую."))
            .cycle(L.of("A firepit, with a grill or a pot added.", "Костёр с решёткой или горшком."),
                Structure.block("tfc:firepit"), Structure.block("tfc:grill"), Structure.block("tfc:pot"))
            .text(
                L.of("Fuels differ in purity. An impure fuel makes a smoky fire, and a very impure one sends its smoke high into the air. Logs are the purest (pine the least pure among them). Pinecones and fallen leaves are very impure and not hot enough to cook much.",
                    "Топливо бывает разной чистоты. Нечистое даёт дымный огонь, а совсем нечистое пускает дым высоко в небо. Чище всего брёвна (хуже всех сосна). Шишки и опавшие листья очень нечисты и недостаточно горячи, чтобы много приготовить."))
            .titled(
                L.of("Air and gas", "Воздух и газ"),
                L.of("A fire burns the oxygen of its room and fills it with gas: carbon dioxide, and carbon monoxide, which has no colour or smell and kills quietly. A firepit gives off about 15 kW of heat; a bellows doubles that. Under open sky or up a flue the gas leaves. In a shut room the fire dies when the oxygen falls below 12%, and you may die before it does.",
                    "Огонь сжигает кислород комнаты и наполняет её газом: углекислым и угарным, у которого нет ни цвета, ни запаха и который убивает тихо. Костёр даёт около 15 кВт тепла, меха удваивают это. Под открытым небом или в дымоходе газ уходит. В закрытой комнате огонь гаснет при кислороде ниже 12%, и вы можете умереть раньше него."))
            .crafting("tfc:crafting/flint_and_pyrite",
                L.of("A better firestarter is made of flint and pyrite, or later of flint and steel.", "Огниво получше делают из кремня и пирита, а позже из кремня и стали."))
            .showsOnLastPage("tfc:flint_and_pyrite", "tfc:ore/pyrite");
    }

    private static Entry pitKiln()
    {
        return Entry.of("getting_started/pit_kiln", CATEGORY, "tfc:textures/gui/book/icons/pit_kiln.png", 3, L.of("Pit Kilns", "Земляные печи"))
            .text(
                L.of("A pit kiln is an early way to $(l:core_mechanics/heating)heat$() things, for example to $(thing)fire$() clay into ceramic. Over about $(cfg:pitKilnTicks) it heats what lies in it, but only to about 900 °C: a heap of straw and logs without a draught gets no hotter.$(br2)That is enough for clay ware (850 °C), and not for fire clay ware (1300 °C) or for melting copper (1080 °C).",
                    "Земляная печь ранний способ $(l:core_mechanics/heating)нагреть$() вещи, например $(thing)обжечь$() глину в керамику. За $(cfg:pitKilnTicks) она греет то, что лежит в ней, но лишь до 900 °C: куча соломы и брёвен без тяги жарче не становится.$(br2)Этого хватает для глиняной посуды (850 °C), но не для шамота (1300 °C) и не для плавки меди (1080 °C)."))
            .text(
                L.of("To build a pit kiln you need:$(li)up to four items to fire$(li)eight pieces of $(thing)Straw$()$(li)eight $(thing)Logs$()$(li)something to light a fire, a $(l:getting_started/firepit#firestarter)Firestarter$() or a $(thing)Torch$().$(br2)$(bold)Note:$() a torch thrown on the kiln lights it within seconds.",
                    "Для земляной печи нужно:$(li)до четырёх предметов для обжига$(li)восемь $(thing)соломы$()$(li)восемь $(thing)брёвен$()$(li)что-то для огня, $(l:getting_started/firepit#firestarter)огниво$() или $(thing)факел$().$(br2)$(bold)Заметка:$() брошенный на печь факел зажигает её за несколько секунд."))
            .text(
                L.of("$(bold)1.$() Put up to four items into a hole one block wide with $(item)$(k:tfc.key.place_block)$().$(br)$(bold)2.$() Cover them with eight $(thing)Straw$() or two $(thing)Thatch$() blocks.$(br)$(bold)3.$() Fill the pit with eight $(thing)Logs$().$(br)$(bold)4.$() Light the top.$(br2)The kiln burns for $(cfg:pitKilnTicks) and slowly heats what is inside.",
                    "$(bold)1.$() Положите до четырёх предметов в яму шириной в блок через $(item)$(k:tfc.key.place_block)$().$(br)$(bold)2.$() Прикройте их восемью $(thing)соломы$() или двумя блоками $(thing)соломенной кровли$().$(br)$(bold)3.$() Заполните яму восемью $(thing)брёвнами$().$(br)$(bold)4.$() Подожгите верх.$(br2)Печь горит $(cfg:pitKilnTicks) и медленно греет то, что внутри."))
            .image(L.of("Tutorial: creating a pit kiln.", "Как построить земляную печь."), true,
                "tfc:textures/gui/book/tutorial/pit_kiln_1.png", "tfc:textures/gui/book/tutorial/pit_kiln_2.png", "tfc:textures/gui/book/tutorial/pit_kiln_3.png",
                "tfc:textures/gui/book/tutorial/pit_kiln_4.png", "tfc:textures/gui/book/tutorial/pit_kiln_5.png")
            .titled(
                L.of("What it can do", "Что она может"),
                L.of("$(li)Clay ware, molds, bricks, terracotta: yes, at 850 °C.$(li)Fire clay ware: no. It needs 1300 °C, see $(l:metalworking/fire_clay)Fire Clay$().$(li)Copper, gold, silver, brass, bronze: they do not melt here. Tin, bismuth, zinc and lead do. A metal item that would melt is left as it was.$(li)The kiln reaches 900 °C after about two thirds of its burning time.",
                    "$(li)Глиняная посуда, формы, кирпичи, терракота: да, при 850 °C.$(li)Шамот: нет, ему нужно 1300 °C, смотрите $(l:metalworking/fire_clay)огнеупорную глину$().$(li)Медь, золото, серебро, латунь, бронза здесь не плавятся. Олово, висмут, цинк и свинец плавятся. Металлический предмет, который расплавился бы, остаётся как был.$(li)900 °C печь набирает примерно за две трети времени горения."));
    }

    private static Entry pottery()
    {
        final JsonObject indicators = Structure.layers(new String[][] {{"   ", " C ", "   "}, {"XXX", "X0X", "XXX"}}, "0", "tfc:clay_grass/aridisol", "X", "tfc:clay_grass/aridisol", "C", "#tfc:clay_indicators");
        return Entry.of("getting_started/pottery", CATEGORY, "tfc:ceramic/vessel", 2, L.of("Pottery", "Гончарное дело"))
            .text(
                L.of("$(thing)Clay$() is a handy and balanced material for pottery, but hard to find at first. It is usually under grass, in two kinds of place. Where the $(l:core_mechanics/climate#rainfall)annual rainfall$() is at least 175 mm, clay lies in patches everywhere, often marked by certain $(thing)plants$().",
                    "$(thing)Глина$() удобный и неприхотливый материал для гончарного дела, но поначалу её трудно найти. Обычно она под травой, в двух видах мест. Там, где $(l:core_mechanics/climate#rainfall)годовые осадки$() не меньше 175 мм, глина лежит пятнами повсюду, часто с приметными $(thing)растениями$()."))
            .showsOnLastPage("minecraft:clay")
            .multiblock(indicators, L.of("Clay Indicators", "Признаки глины"), L.of("Clay, and one of the plants that may show it.", "Глина и одно из растений, которое на неё указывает."))
            .text(
                L.of("Clay is betrayed by $(thing)Goldenrod$(), $(thing)Perovskia$(), $(thing)Canna$(), $(thing)Water Canna$(), $(thing)Pampas Grass$() and $(thing)Athyrium Fern$() growing close to it. There is also clay in small beds by water: rivers, lakes, ponds.$(br2)Clay can be knapped like stone, with five pieces in hand. If you err, you can close the screen, reshape the clay and try again.",
                    "$(thing)Кочедыжник$(), $(thing)канна$(), $(thing)золотарник$(), $(thing)пампасная трава$(), $(thing)перовския$() и $(thing)водная канна$() указывают на глину рядом. Есть и мелкие залежи у воды: рек, озёр, прудов.$(br2)Глину выбивают как камень, пять кусков в руке. Если ошиблись, закройте экран, снова слепите глину и попробуйте ещё раз."))
            .anchor("knapping")
            .showsOnLastPage("tag:tfc:clay_indicators")
            .image(L.of("The knapping screen.", "Экран выбивания."), false, "tfc:textures/gui/book/gui/clay_knapping.png")
            .titled(L.of("Small Vessel", "Малый сосуд"), L.of("The small vessel is the first thing to make. Like all pottery it must be fired before use. Firing heats the item until the clay becomes hard $(thing)ceramic$(): 850 °C. Early on that is done in a $(l:getting_started/pit_kiln)Pit Kiln$(). Ware of $(l:metalworking/fire_clay)Fire Clay$() needs 1300 °C instead.",
                    "Малый сосуд первое, что стоит сделать. Как и всю керамику, его надо обжечь. Обжиг греет изделие, пока глина не станет твёрдой $(thing)керамикой$(): 850 °C. Сначала это делают в $(l:getting_started/pit_kiln)земляной печи$(). Изделиям из $(l:metalworking/fire_clay)огнеупорной глины$() нужно 1300 °C."))
            .anchor("vessel")
            .showsOnLastPage("tfc:ceramic/unfired_vessel", "tfc:ceramic/vessel")
            .knapping("tfc:knapping/ceramic/unfired_vessel", L.of("Knapping a small vessel.", "Выбивание малого сосуда."))
            .titled(L.of("Large Vessel", "Большой сосуд"), L.of("The $(l:food/decay#large_vessels)Large Vessel$() is knapped the same way. It can be set down as a block and holds nine stacks. The linked chapter says more.",
                    "$(l:food/decay#large_vessels)Большой сосуд$() делают так же. Его можно ставить, у него девять ячеек. Смотрите связанную главу."))
            .knapping("tfc:knapping/ceramic/unfired_large_vessel", L.of("Knapping a large vessel.", "Выбивание большого сосуда."))
            .titled(L.of("Jug", "Кувшин"), L.of("Another useful piece is the $(thing)Jug$(). It picks up and $(thing)drinks$() liquids such as fresh water. $(item)$(k:key.use)$() it on the liquid in the world, then use it to drink. It holds $(thing)$(cfg:jugCapacity)$() of liquid.",
                    "Ещё одна полезная вещь $(thing)кувшин$(). Он набирает и $(thing)поит$() жидкостями, например пресной водой. Используйте его $(item)$(k:key.use)$() на жидкости в мире, затем пейте. В него входит $(thing)$(cfg:jugCapacity)$()."))
            .anchor("jug")
            .showsOnLastPage("tfc:ceramic/unfired_jug", "tfc:ceramic/jug")
            .knapping("tfc:knapping/ceramic/unfired_jug", L.of("Knapping a jug.", "Выбивание кувшина."))
            .titled(L.of("Molds", "Формы"), L.of("Clay also makes $(thing)Molds$(). Molten metal poured into a mold sets into its shape. Use the mold to take out the item, and sometimes the mold too. The simplest is the ingot mold, shown next.",
                    "Из глины делают и $(thing)формы$(). Расплавленный металл, залитый в форму, застывает в её виде. Используйте форму, чтобы достать изделие, а иногда и саму форму. Проще всех форма для слитка, она дальше."))
            .anchor("mold")
            .knapping("tfc:knapping/ceramic/unfired_ingot_mold", L.of("Knapping an ingot mold.", "Выбивание формы для слитка."))
            .showsOnLastPage("tfc:ceramic/unfired_ingot_mold")
            .heating("tfc:heating/ceramic/ingot_mold",
                L.of("Fire the mold like any clay ware at 850 °C, likely in a $(l:getting_started/pit_kiln)Pit Kiln$(). Then pour metal in, and take it out when it has cooled enough.", "Обожгите форму, как всякую глиняную посуду, при 850 °C, скорее всего в $(l:getting_started/pit_kiln)земляной печи$(). Затем заливайте металл и вынимайте, когда он остыл."))
            .showsOnLastPage("tfc:ceramic/ingot_mold")
            .spotlight("tfc:ceramic/ingot_mold[tfc:fluid={id:\"tfc:metal/copper\",amount:100}]", L.of("Casting", "Литьё"),
                L.of("The next pages show knapping patterns for several tools.", "На следующих страницах выбивание форм для разных орудий."))
            .knapping("tfc:knapping/ceramic/unfired_propick_head_mold", L.of("A $(l:metalworking/prospecting#propick)Prospector's Pick$() is the tool for finding rich ore.", "$(l:metalworking/prospecting#propick)Кирка разведчика$() инструмент поиска богатой руды."))
            .showsOnLastPage("tfc:ceramic/unfired_propick_head_mold")
            .knapping("tfc:knapping/ceramic/unfired_pickaxe_head_mold", L.of("A $(thing)Pickaxe$(): the everyday mining tool.", "$(thing)Кирка$(): повседневное орудие добычи."))
            .showsOnLastPage("tfc:ceramic/unfired_pickaxe_head_mold", "tfc:ceramic/pickaxe_head_mold")
            .knapping("tfc:knapping/ceramic/unfired_saw_blade_mold", L.of("A $(thing)Saw$() is needed for fine wooden parts, such as a $(thing)Workbench$() and many other devices, $(l:core_mechanics/support_beams)Supports$() among them.", "$(thing)Пила$() нужна для точных деревянных деталей, таких как $(thing)верстак$() и многие устройства, в том числе $(l:core_mechanics/support_beams)опоры$()."))
            .showsOnLastPage("tfc:ceramic/unfired_saw_blade_mold", "tfc:ceramic/saw_blade_mold")
            .knapping("tfc:knapping/ceramic/unfired_scythe_blade_mold", L.of("A $(thing)Scythe$() cuts plants and leaves in a 3x3x3 area.", "$(thing)Коса$() срезает растения и листву в области 3x3x3."))
            .showsOnLastPage("tfc:ceramic/unfired_scythe_blade_mold", "tfc:ceramic/scythe_blade_mold")
            .knapping("tfc:knapping/ceramic/unfired_chisel_head_mold", L.of("A $(l:metalworking/chisel)Chisel$() smooths blocks and carves many decorative ones.", "$(l:metalworking/chisel)Долото$() сглаживает блоки и вырезает из них много декоративных."))
            .showsOnLastPage("tfc:ceramic/unfired_chisel_head_mold", "tfc:ceramic/chisel_head_mold")
            .knapping("tfc:knapping/ceramic/unfired_axe_head_mold", L.of("An $(thing)Axe$() for felling trees. A stone axe is worse than a metal one.", "$(thing)Топор$() для рубки деревьев. Каменный хуже металлического."))
            .showsOnLastPage("tfc:ceramic/unfired_axe_head_mold", "tfc:ceramic/axe_head_mold")
            .knapping("tfc:knapping/ceramic/unfired_hammer_head_mold", L.of("A $(thing)Hammer$() is needed for making and working $(l:metalworking/anvils)Anvils$().", "$(thing)Молот$() нужен для постройки и работы на $(l:metalworking/anvils)наковальнях$()."))
            .showsOnLastPage("tfc:ceramic/unfired_hammer_head_mold", "tfc:ceramic/hammer_head_mold")
            .knapping("tfc:knapping/ceramic/unfired_knife_blade_mold", L.of("A $(thing)Knife$() is a weapon and a cutter of plants.", "$(thing)Нож$() оружие и резак для растений."))
            .showsOnLastPage("tfc:ceramic/unfired_knife_blade_mold", "tfc:ceramic/knife_blade_mold")
            .knapping("tfc:knapping/ceramic/unfired_hoe_head_mold", L.of("A $(thing)Hoe$() tills and tends $(l:food/crops)Crops$().", "$(thing)Мотыга$() рыхлит землю и ухаживает за $(l:food/crops)посевами$()."))
            .showsOnLastPage("tfc:ceramic/unfired_hoe_head_mold", "tfc:ceramic/hoe_head_mold")
            .knapping("tfc:knapping/ceramic/unfired_shovel_head_mold", L.of("A $(thing)Shovel$() for digging.", "$(thing)Лопата$() для копки."))
            .showsOnLastPage("tfc:ceramic/unfired_shovel_head_mold", "tfc:ceramic/shovel_head_mold");
    }

    private static Entry primitiveAlloys()
    {
        return Entry.of("getting_started/primitive_alloys", CATEGORY, "tfc:ceramic/ingot_mold[tfc:fluid={id:\"tfc:metal/bronze\",amount:100}]", 6, L.of("Primitive Alloys", "Примитивные сплавы"))
            .text(
                L.of("An $(thing)Alloy$() mixes metals into a new, stronger one. Copper makes tools, but the next step up is one of three $(thing)Bronzes$(). An alloy is made of component $(thing)metals$(), each of which must stay within its share of the whole.",
                    "$(thing)Сплав$() смешивает металлы в новый, более прочный. Из меди делают орудия, но следующая ступень это одна из трёх $(thing)бронз$(). Сплав состоит из $(thing)металлов$(), каждый из которых должен укладываться в свою долю целого."))
            .text(
                L.of("Early on, alloys are made in a $(thing)Small Vessel$(), much as in $(l:getting_started/finding_ores#casting)Casting$(): put in enough ore pieces in the right ratio for a known alloy. Heat the vessel in a $(l:metalworking/charcoal_forge)Charcoal Forge$(), as a pit kiln (about 900 °C) cannot melt copper (1080 °C).",
                    "Сначала сплавы делают в $(thing)малом сосуде$(), так же как $(l:getting_started/finding_ores#casting)литьё$(): положите достаточно кусков руды в нужной пропорции для известного сплава. Сосуд греют в $(l:metalworking/charcoal_forge)угольной кузнице$(), потому что земляная печь (около 900 °C) медь (1080 °C) не расплавит."))
            .text(
                L.of("Take $(thing)Bronze$() (on the right): a full 1000 mB needs 880-920 mB of $(thing)Copper$() together with 80-120 mB of $(thing)Tin$().$(br2)The three bronzes follow, one to a page. All make tools, $(l:mechanics/armor)Armor$() and more, differing a little in durability, speed and damage.",
                    "Возьмём $(thing)бронзу$() (справа): полные 1000 мБ требуют 880-920 мБ $(thing)меди$() вместе с 80-120 мБ $(thing)олова$().$(br2)Три бронзы идут дальше, по странице на каждую. Из всех делают орудия, $(l:mechanics/armor)броню$() и прочее, а различаются они немного прочностью, скоростью и уроном."))
            .spotlight("tfc:metal/ingot/bronze", L.of("Bronze", "Бронза"),
                L.of("$(bold)Needs:$()$(br)$(li)$(thing)Copper$(), 88 to 92 %$(li)$(thing)Tin$(), 8 to 12 %", "$(bold)Нужно:$()$(br)$(li)$(thing)медь$(), 88–92 %$(li)$(thing)олово$(), 8–12 %"))
            .showsOnLastPage("tfc:metal/ingot/bronze")
            .spotlight("tfc:metal/ingot/bismuth_bronze", L.of("Bismuth Bronze", "Висмутовая бронза"),
                L.of("$(bold)Needs:$()$(br)$(li)$(thing)Zinc$(), 20 to 30 %$(li)$(thing)Copper$(), 50 to 65 %$(li)$(thing)Bismuth$(), 10 to 20 %", "$(bold)Нужно:$()$(br)$(li)$(thing)цинк$(), 20–30 %$(li)$(thing)медь$(), 50–65 %$(li)$(thing)висмут$(), 10–20 %"))
            .showsOnLastPage("tfc:metal/ingot/bismuth_bronze")
            .spotlight("tfc:metal/ingot/black_bronze", L.of("Black Bronze", "Чёрная бронза"),
                L.of("$(bold)Needs:$()$(br)$(li)$(thing)Copper$(), 50 to 70 %$(li)$(thing)Silver$(), 10 to 25 %$(li)$(thing)Gold$(), 10 to 25 %", "$(bold)Нужно:$()$(br)$(li)$(thing)медь$(), 50–70 %$(li)$(thing)серебро$(), 10–25 %$(li)$(thing)золото$(), 10–25 %"))
            .showsOnLastPage("tfc:metal/ingot/black_bronze")
            .titled(
                L.of("Utility Alloys", "Прикладные сплавы"),
                L.of("Three more alloys cannot make tools: $(thing)Brass$(), $(thing)Rose Gold$() and $(thing)Sterling Silver$().$(br2)Of these, $(thing)Brass$() goes into mechanisms, including $(l:mechanics/mechanical_power)Mechanical Power$(), and for the brass blowpipe of glassworking.$(br2)$(thing)Rose Gold$() and $(thing)Sterling Silver$() go into $(l:metalworking/steel#red_steel)Red Steel$() and $(l:metalworking/steel#blue_steel)Blue Steel$().",
                    "Ещё три сплава на орудия не идут: $(thing)латунь$(), $(thing)розовое золото$() и $(thing)стерлинговое серебро$().$(br2)Из них $(thing)латунь$() нужна для механизмов, в том числе $(l:mechanics/mechanical_power)механическую энергию$(), и на латунную трубку стеклодува.$(br2)$(thing)Розовое золото$() и $(thing)стерлинговое серебро$() нужны для $(l:metalworking/steel#red_steel)красной стали$() и $(l:metalworking/steel#blue_steel)синей стали$()."))
            .spotlight("tfc:metal/ingot/brass", L.of("Brass", "Латунь"),
                L.of("$(bold)Needs:$()$(br)$(li)$(thing)Copper$(), 62 to 72 %$(li)$(thing)Zinc$(), 28 to 38 %$(br2)Real brass for mechanisms holds about a third of zinc, and melts at 930 °C.", "$(bold)Нужно:$()$(br)$(li)$(thing)медь$(), 62–72 %$(li)$(thing)цинк$(), 28–38 %$(br2)Настоящая латунь для механизмов содержит около трети цинка и плавится при 930 °C."))
            .anchor("brass")
            .showsOnLastPage("tfc:metal/ingot/brass")
            .spotlight("tfc:metal/ingot/rose_gold", L.of("Rose Gold", "Розовое золото"),
                L.of("$(bold)Needs:$()$(br)$(li)$(thing)Copper$(), 15 to 30 %$(li)$(thing)Gold$(), 70 to 85 %", "$(bold)Нужно:$()$(br)$(li)$(thing)медь$(), 15–30 %$(li)$(thing)золото$(), 70–85 %"))
            .spotlight("tfc:metal/ingot/sterling_silver", L.of("Sterling Silver", "Стерлинговое серебро"),
                L.of("$(bold)Needs:$()$(br)$(li)$(thing)Silver$(), 90 to 95 %$(li)$(thing)Copper$(), 5 to 10 %$(br2)It melts at 890 °C.", "$(bold)Нужно:$()$(br)$(li)$(thing)серебро$(), 90–95 %$(li)$(thing)медь$(), 5–10 %$(br2)Плавится при 890 °C."))
            .titled(
                L.of("Arsenical copper", "Мышьяковистая медь"),
                L.of("Copper smelted from tetrahedrite holds arsenic and is $(thing)arsenical$(). It can stand in for copper in bronze, bismuth bronze, black bronze and brass. Mixed with pure copper it gives arsenical copper again: arsenic is not diluted away. Pure copper comes only from native copper and malachite.",
                    "Медь, выплавленная из тетраэдрита, содержит мышьяк и $(thing)мышьяковистая$(). Она может заменить медь в бронзе, висмутовой бронзе, чёрной бронзе и латуни. Смешанная с чистой медью она остаётся мышьяковистой: мышьяк не разбавляется. Чистая медь выходит только из самородной меди и малахита."));
    }

    private static Entry findingOres()
    {
        final JsonObject small = Structure.layers(new String[][] {{"    ", "  0 ", "    "}, {"ABCD", "EFGH", "IJKL"}, {"XXXX", "XXXX", "XXXX"}},
            "X", "tfc:grass/entisol", "A", "tfc:ore/small_native_copper", "B", "tfc:ore/small_native_gold", "C", "tfc:ore/small_hematite", "D", "tfc:ore/small_native_silver",
            "E", "tfc:ore/small_cassiterite", "F", "tfc:ore/small_bismuthinite", "G", "tfc:ore/small_garnierite", "H", "tfc:ore/small_malachite",
            "I", "tfc:ore/small_magnetite", "J", "tfc:ore/small_limonite", "K", "tfc:ore/small_sphalerite", "L", "tfc:ore/small_tetrahedrite");
        return Entry.of("getting_started/finding_ores", CATEGORY, "tfc:ore/normal_native_copper", 4, L.of("Ores, Metal, and Casting", "Руды, металлы и литьё"))
            .text(
                L.of("Besides sticks, twigs and stones, you may come across small pieces of ore lying about on the ground. They matter: before you have a pickaxe they are nearly the only source of ore and metal.",
                    "Помимо палок, веток и камней, на земле встречаются мелкие куски руды. Они важны: пока нет кирки, это почти единственный источник руды и металла."))
            .multiblock(small, L.of("", ""), L.of("All the small ore pieces", "Все мелкие куски руды"))
            .text(
                L.of("A small piece yields some metal and, more to the point, hints at a bigger vein close by, most likely buried not far below the surface. Mark the spot where you find one: it helps when you are $(l:metalworking/prospecting)prospecting$().$(br2)The twelve small ores, and what they give, are on the next page.",
                    "Мелкий кусок даёт металл и, что важнее, указывает на жилу побольше рядом, скорее всего под землёй неглубоко. Запоминайте, где нашли: это поможет при $(l:metalworking/prospecting)разведке$().$(br2)Двенадцать мелких руд и то, что они дают, на следующей странице."))
            .titled(
                L.of("Small Ores", "Мелкие руды"),
                L.of("$(li)Native Copper: $(thing)Copper$()$(li)Native Gold: $(thing)Gold$()$(li)Native Silver: $(thing)Silver$()$(li)Malachite: $(thing)Copper$()$(li)Cassiterite: $(thing)Tin$()$(li)Garnierite: $(thing)Nickel$()$(li)Bismuthinite: $(thing)Bismuth$(), roasted with fumes$(li)Tetrahedrite: arsenical $(thing)Copper$(), with poison fumes$(li)Sphalerite: only a calcine, no zinc$(li)Hematite, Magnetite, Limonite: iron oxide, no iron yet",
                    "$(li)Самородная медь: $(thing)медь$()$(li)Самородное золото: $(thing)золото$()$(li)Самородное серебро: $(thing)серебро$()$(li)Малахит: $(thing)медь$()$(li)Касситерит: $(thing)олово$()$(li)Гарниерит: $(thing)никель$()$(li)Висмутин: $(thing)висмут$(), обжиг с дымом$(li)Тетраэдрит: мышьяковистая $(thing)медь$(), ядовитый дым$(li)Сфалерит: лишь огарок, цинка нет$(li)Гематит, магнетит, лимонит: оксид железа, железа пока нет"))
            .titled(L.of("Casting", "Литьё"), L.of("Metal in TerraFirmaCraft is counted in $(thing)units$(), also called $(thing)mB$() (millibuckets), and each ore piece holds a fixed amount. The small ores lying on the surface are the poorest, $(thing)10 mB$() apiece. To free the metal, melt the ore and $(thing)cast$() it in the shape you want.",
                    "В TerraFirmaCraft каждая руда содержит определённое число $(thing)единиц$(), или $(thing)мБ (миллибакетов)$(), металла. Мелкие поверхностные руды беднее всех и дают $(thing)10 мБ$(). Чтобы получить металл, руду плавят и $(thing)отливают$() в нужную форму."))
            .anchor("casting")
            .text(
                L.of("You will need:$(li)a $(l:getting_started/pottery#vessel)Small Vessel$()$(li)a heat that melts the metal: a $(l:metalworking/charcoal_forge)Charcoal Forge$() for copper (1080 °C), gold and silver. A $(l:getting_started/pit_kiln)Pit Kiln$() (about 900 °C) melts only tin, bismuth, zinc and lead$(li)one or more $(l:getting_started/pottery#mold)Molds$()$(li)at least 100 mB of a castable metal: $(thing)Copper$() in any of its ore forms$(br2)$(italic)Some $(l:getting_started/primitive_alloys)Alloys$() can be cast too.$()",
                    "Понадобятся:$(li)$(l:getting_started/pottery#vessel)малый сосуд$()$(li)жар, плавящий металл: $(l:metalworking/charcoal_forge)угольная кузница$() для меди (1080 °C), золота и серебра. $(l:getting_started/pit_kiln)Земляная печь$() (около 900 °C) плавит только олово, висмут, цинк и свинец$(li)одна или несколько $(l:getting_started/pottery#mold)форм$()$(li)не меньше 100 мБ годного к литью металла: $(thing)меди$() в любом виде руды$(br2)$(italic)Отливать можно и некоторые $(l:getting_started/primitive_alloys)сплавы$().$()"))
            .text(
                L.of("Open the vessel and put the ore in. Count the metal in it carefully. Then set the filled vessel in the forge: as it heats, the ore melts and the vessel holds liquid metal.$(br2)Take the vessel out and use it to open the $(thing)Casting$() screen.",
                    "Откройте сосуд и положите руду. Внимательно сосчитайте металл в ней. Затем поставьте полный сосуд в кузницу: пока он греется, руда плавится, и в сосуде оказывается жидкий металл.$(br2)Достаньте сосуд и используйте его, чтобы открыть экран $(thing)литья$()."))
            .image(L.of("The casting screen.", "Экран литья."), false, "tfc:textures/gui/book/gui/casting.png")
            .text(
                L.of("With the screen open, put an empty fired mold in the middle slot. It fills while the vessel stays liquid. If the vessel sets, heat it again. Take the full mold out to cool. When cool, use the mold, or put it in the crafting grid, to get the item and sometimes the mold back.",
                    "На открытом экране положите пустую обожжённую форму в среднюю ячейку. Она наполняется, пока сосуд жидок. Если сосуд застыл, нагрейте его снова. Вынимайте полную форму остывать. Остывшую форму используйте или кладите в сетку крафта, чтобы получить изделие, а иногда и форму."))
            .crafting("tfc:crafting/metal/pickaxe/copper",
                L.of("With a tool head you can craft your first pickaxe. Find the copper for a head, fire a pickaxe mold, melt the ore in a forge and cast the head. Put it on a stick, and there it is.", "С головкой орудия можно сделать первую кирку. Найдите медь на головку, обожгите форму кирки, расплавьте руду в кузнице и отлейте головку. Наденьте её на палку, и готово."));
    }

    private GettingStartedGuide() {}
}
