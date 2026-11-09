package tfcpp.compat.guide;

import java.util.List;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import tfcpp.compat.guide.Pages.Structure;

/**
 * TFC's chapter on ores and minerals, written again because TFC++ changed what most of its ores do in the fire: sulfides roast and poison, iron oxide needs carbon, cinnabar gives mercury and not redstone.
 * <p>
 * The anchors are the same as TFC's ({@code hematite}, {@code cinnabar} and so on), since other chapters link to them.
 */
final class WorldGuide
{
    private static final String EXTRUSIVE = "$(l:the_world/geology#igneous_extrusive)";
    private static final String INTRUSIVE = "$(l:the_world/geology#igneous_intrusive)";

    static List<Entry> entries()
    {
        return List.of(oresAndMinerals());
    }

    private static Entry oresAndMinerals()
    {
        final Entry entry = Entry.of("the_world/ores_and_minerals", "tfc:the_world", "tfc:ore/normal_hematite", 1, L.of("Ores and Minerals", "Руды и минералы"));
        entry.text(
            L.of("Ore is scarce in TFC, and unlike vanilla's it lies in huge, sparse veins, so you need $(l:metalworking/prospecting)prospecting$() to find it. Every ore keeps to certain rocks and certain heights: the right rock at the right depth is the whole secret.",
                "Руда в TFC скудна, и, в отличие от ванильной, лежит огромными редкими жилами, так что искать её нужно $(l:metalworking/prospecting)разведкой$(). Каждая держится определённых пород и высот: вся хитрость в нужной породе на нужной глубине."));
        entry.text(
            L.of("Some ores are $(thing)graded$(): a block is poor, normal or rich, and each vein has its own mix. A richer vein pays more.$(br2)TFC++ treats an ore as the compound it is, and not as a lump of metal: what a fire makes of it depends on what else is in it. The next pages show each ore, where it lies, and what it gives.",
                "Некоторые руды $(thing)сортовые$(): блок бывает бедным, обычным или богатым, и в каждой жиле своя смесь. Богатая жила выгоднее.$(br2)TFC++ видит в руде химическое соединение, а не кусок металла: что получится в огне, зависит от того, что в ней есть ещё. На следующих страницах каждая руда, где она лежит и что даёт."));

        graded(entry, "native_copper", L.of("Native Copper", "Самородная медь"), "dacite", L.of("dacite", "дацит"),
            L.of("Native Copper is an ore of $(thing)Copper$(). It lies in " + EXTRUSIVE + "Igneous Extrusive$() rock above y=40, and as deposits in $(thing)rivers$(), which can be $(l:getting_started/panning)panned$(). Copper melts at 1080 °C: it wants a forge, not a pit kiln.",
                "Самородная медь руда $(thing)меди$(). Она лежит в " + EXTRUSIVE + "излившихся магматических$() породах выше y=40 и в отложениях $(thing)рек$(), которые можно $(l:getting_started/panning)промывать$(). Медь плавится при 1080 °C: нужна кузница, а не земляная печь."));
        graded(entry, "native_gold", L.of("Native Gold", "Самородное золото"), "diorite", L.of("diorite", "диорит"),
            L.of("Native Gold is an ore of $(thing)Gold$(). It lies below y=70, larger and richer the deeper you go, in " + EXTRUSIVE + "Igneous Extrusive$() and " + INTRUSIVE + "Igneous Intrusive$() rock, and in $(thing)rivers$(), where it can be $(l:getting_started/panning)panned$(). Gold melts at 1060 °C.",
                "Самородное золото руда $(thing)золота$(). Оно лежит ниже y=70, чем глубже, тем крупнее и богаче, в " + EXTRUSIVE + "излившихся$() и " + INTRUSIVE + "интрузивных$() породах и в $(thing)реках$(), где его можно $(l:getting_started/panning)промывать$(). Золото плавится при 1060 °C."));
        graded(entry, "native_silver", L.of("Native Silver", "Самородное серебро"), "granite", L.of("granite", "гранит"),
            L.of("Native Silver is an ore of $(thing)Silver$(). Small poor veins lie in $(thing)Granite$() or $(thing)Diorite$() of uplifts above y=90. Larger, richer ones lie in granite, diorite, $(thing)Schist$() and $(thing)Gneiss$() deep below y=20. It is also in $(thing)rivers$(), where it can be panned. Silver melts at 961 °C.",
                "Самородное серебро руда $(thing)серебра$(). Мелкие бедные жилы лежат в $(thing)граните$() или $(thing)диорите$() поднятий выше y=90. Крупные богатые лежат в граните, диорите, $(thing)кристаллическом сланце$() и $(thing)гнейсе$() глубоко ниже y=20. Есть оно и в $(thing)реках$(), где его промывают. Серебро плавится при 961 °C."));
        graded(entry, "tetrahedrite", L.of("Tetrahedrite", "Тетраэдрит"), "schist", L.of("schist", "кристаллический сланец"),
            L.of("Tetrahedrite is an ore of $(thing)Copper$(): at any height, richer deeper, in $(l:the_world/geology#metamorphic)Metamorphic$() rock.$(br2)It is a sulfide with arsenic and antimony. It melts into $(thing)arsenical copper$() at 1050 °C, good for tools and bronze, and its smoke is sulfur dioxide with the oxides of arsenic and antimony: poison. Smelt it in the open or under a flue. See $(l:tfcpp/sulfides#tetrahedrite)Sulfide Ores and the Retort$().",
                "Тетраэдрит руда $(thing)меди$(): на любой высоте, глубже богаче, в $(l:the_world/geology#metamorphic)метаморфических$() породах.$(br2)Это сульфид с мышьяком и сурьмой. Он плавится в $(thing)мышьяковистую медь$() при 1050 °C, годную на орудия и бронзу, а его дым это сернистый газ с оксидами мышьяка и сурьмы: яд. Плавьте его на открытом месте или под дымоходом. См. $(l:tfcpp/sulfides#tetrahedrite)Сульфидные руды и реторта$()."));
        graded(entry, "malachite", L.of("Malachite", "Малахит"), "marble", L.of("marble", "мрамор"),
            L.of("Malachite is an ore of $(thing)Copper$(). It lies mostly in $(thing)Marble$(), $(thing)Limestone$(), $(thing)Chalk$() and $(thing)Dolomite$(), at most heights, larger and richer deeper. Unlike tetrahedrite it holds no sulfur and no arsenic: it melts into clean copper.",
                "Малахит руда $(thing)меди$(). Он лежит в основном в $(thing)доломите$(), $(thing)меле$(), $(thing)известняке$() и $(thing)мраморе$(), на большинстве высот, глубже крупнее и богаче. В отличие от тетраэдрита в нём нет ни серы, ни мышьяка: он плавится в чистую медь."));
        graded(entry, "cassiterite", L.of("Cassiterite", "Касситерит"), "diorite", L.of("diorite", "диорит"),
            L.of("Cassiterite is an ore of $(thing)Tin$(). It lies in " + INTRUSIVE + "Igneous Intrusive$() rock high up, above y=80 in uplifts or in dikes, and in $(thing)rivers$(), where it can be $(l:getting_started/panning)panned$(). Tin melts at 230 °C.",
                "Касситерит руда $(thing)олова$(). Он лежит в " + INTRUSIVE + "интрузивных$() породах высоко, выше y=80 в поднятиях или в дайках, и в $(thing)реках$(), где его можно $(l:getting_started/panning)промывать$(). Олово плавится при 230 °C."));
        graded(entry, "bismuthinite", L.of("Bismuthinite", "Висмутин"), "shale", L.of("shale", "сланец"),
            L.of("Bismuthinite is an ore of $(thing)Bismuth$(). It lies in $(l:the_world/geology#sedimentary)Sedimentary$() rock near the surface, and in larger, richer veins in intrusive rock deep down. It is a sulfide: at 600 °C it roasts to bismuth and gives off sulfur dioxide.",
                "Висмутин руда $(thing)висмута$(). Он лежит в $(l:the_world/geology#sedimentary)осадочных$() породах у поверхности и в более крупных и богатых жилах в интрузивных породах глубоко. Это сульфид: при 600 °C он обжигается до висмута и выпускает сернистый газ."));
        graded(entry, "garnierite", L.of("Garnierite", "Гарниерит"), "gabbro", L.of("gabbro", "габбро"),
            L.of("Garnierite is an ore of $(thing)Nickel$(). It lies below y=0, mostly in $(thing)Gabbro$() deep down, and in smaller, rarer veins in any intrusive rock. Nickel melts at 1453 °C, so it needs a forge with a blast.",
                "Гарниерит руда $(thing)никеля$(). Он лежит ниже y=0, в основном в $(thing)габбро$() глубоко, и в более мелких, редких жилах в любой интрузивной породе. Никель плавится при 1453 °C, поэтому нужна кузница с дутьём."));
        graded(entry, "hematite", L.of("Hematite", "Гематит"), "andesite", L.of("andesite", "андезит"),
            L.of("Hematite is an iron oxide and an ore of $(thing)Iron$(): large veins in any " + EXTRUSIVE + "extrusive$() rock near the surface.$(br2)No heat alone turns an iron oxide into metal. In a forge or crucible it melts only into iron oxide (1565 °C), which cannot be cast. Carbon must take the oxygen away: in a $(l:metalworking/bloomery)Bloomery$() or a $(l:metalworking/blast_furnace)Blast Furnace$().",
                "Гематит оксид железа и руда $(thing)железа$(): большие жилы в любой " + EXTRUSIVE + "излившейся$() породе у поверхности.$(br2)Один жар не превращает оксид железа в металл. В кузнице или тигле он плавится лишь в оксид железа (1565 °C), который не отлить. Кислород должен забрать углерод: в $(l:metalworking/bloomery)сыродутной печи$() или $(l:metalworking/blast_furnace)доменной печи$()."));
        graded(entry, "magnetite", L.of("Magnetite", "Магнетит"), "limestone", L.of("limestone", "известняк"),
            L.of("Magnetite is an ore of $(thing)Iron$(): large veins in any $(l:the_world/geology#sedimentary)sedimentary$() rock near the surface. It is an oxide like hematite and is smelted in the same way.",
                "Магнетит руда $(thing)железа$(): большие жилы в любой $(l:the_world/geology#sedimentary)осадочной$() породе у поверхности. Это оксид, как гематит, и плавится так же."));
        graded(entry, "limonite", L.of("Limonite", "Лимонит"), "chalk", L.of("chalk", "мел"),
            L.of("Limonite is an ore of $(thing)Iron$(): large veins in any $(l:the_world/geology#sedimentary)sedimentary$() rock near the surface.$(br2)Brown iron ores carry 0.5 to 2% phosphorus. In a bloomery it goes into the slag. In a blast furnace it goes into the iron, which then breaks in the cold: it is cold short.",
                "Лимонит руда $(thing)железа$(): большие жилы в любой $(l:the_world/geology#sedimentary)осадочной$() породе у поверхности.$(br2)Бурые железняки несут от 0,5 до 2% фосфора. В сыродутной печи он уходит в шлак. В доменной он идёт в железо, и оно ломается на холоде: оно хладноломко."));
        graded(entry, "sphalerite", L.of("Sphalerite", "Сфалерит"), "quartzite", L.of("quartzite", "кварцит"),
            L.of("Sphalerite is an ore of $(thing)Zinc$(). Small poor veins lie in " + EXTRUSIVE + "Igneous Extrusive$() rock near the surface, large rich ones in " + INTRUSIVE + "Igneous Intrusive$() rock deep down.$(br2)Galena, the lead ore, lies beside it: see $(l:tfcpp/lead_silver#galena)Lead and Silver$().$(br2)It does not melt into zinc: at 900 °C it roasts into a calcine and gives off sulfur dioxide. Zinc comes only from a retort at 1000 °C. It boils at 907 °C, and in the open it burns to white smoke that gives metal fume fever.",
                "Сфалерит руда $(thing)цинка$(). Мелкие бедные жилы лежат в " + EXTRUSIVE + "излившихся$() породах у поверхности, крупные богатые в " + INTRUSIVE + "интрузивных$() глубоко.$(br2)Рядом с ним лежит галенит, свинцовая руда: см. $(l:tfcpp/lead_silver#galena)Свинец и серебро$().$(br2)В цинк он не плавится: при 900 °C он обжигается в огарок и выпускает сернистый газ. Цинк получают только в реторте при 1000 °C. Он кипит при 907 °C и на открытом воздухе сгорает белым дымом, от которого бывает литейная лихорадка."));

        mineral(entry, "lignite", "tfc:ore/lignite", "tfc:lignite", L.of("Lignite", "Лигнит"), L.of("Lignite.", "Лигнит."),
            L.of("Lignite is a low-grade $(thing)Coal$(): very large flat beds in $(l:the_world/geology#sedimentary)Sedimentary$() rock near the surface. It gives only 750 °C on a forge's own draught and 1500 °C with a full bellows, carries sulfur, and its beds hold gas that comes out when they are mined.",
                "Лигнит низкосортный $(thing)уголь$(): очень большие плоские пласты в $(l:the_world/geology#sedimentary)осадочных$() породах у поверхности. На своей тяге кузницы даёт лишь 750 °C, с полными мехами 1500 °C, содержит серу, и его пласты держат газ, который выходит при добыче."));
        mineral(entry, "bituminous_coal", "tfc:ore/bituminous_coal", "tfc:bituminous_coal", L.of("Bituminous Coal", "Каменный уголь"), L.of("Bituminous Coal.", "Каменный уголь."),
            L.of("Bituminous Coal is a medium-grade $(thing)Coal$(): very large flat beds in $(l:the_world/geology#sedimentary)Sedimentary$() rock near the surface. It gives 950 °C on a forge's own draught and 1700 °C with a full bellows. Its seams hold methane, which explodes, and its dust burns too.",
                "Каменный уголь среднесортный $(thing)уголь$(): очень большие плоские пласты в $(l:the_world/geology#sedimentary)осадочных$() породах у поверхности. На своей тяге кузницы даёт 950 °C, с полными мехами 1700 °C. Его пласты держат метан, который взрывается, и его пыль тоже горит."));

        final JsonElement[] kaolin = new JsonElement[] {kaolinBed("tfc:kaolin_clay_grass"), kaolinBed("tfc:red_kaolin_clay"), kaolinBed("tfc:white_kaolin_clay"), kaolinBed("tfc:pink_kaolin_clay")};
        entry.spotlight("tfc:kaolin_clay", L.of("Kaolinite", "Каолинит"),
            L.of("Kaolinite is a soft $(thing)Mineral$() for $(l:metalworking/fire_clay)Fire Clay$(). It forms at height, on Highlands, Rolling Hills, Old Mountains and Plateaus, where the $(l:core_mechanics/climate#temperature)temperature$() is at least $(cfg:temperature:18) and the $(l:core_mechanics/climate#rainfall)rainfall$() at least 300 mm. $(thing)Blood Lily$() grows on it.",
                "Каолинит мягкий $(thing)минерал$() для $(l:metalworking/fire_clay)огнеупорной глины$(). Он образуется на высоте, на нагорьях, холмах, в старых горах и на плато, где $(l:core_mechanics/climate#temperature)температура$() не ниже $(cfg:temperature:18), а $(l:core_mechanics/climate#rainfall)осадки$() не меньше 300 мм. На нём растёт $(thing)кровавая лилия$()."));
        entry.anchor("kaolinite").showsOnLastPage("tfc:kaolin_clay", "tfc:red_kaolin_clay", "tfc:pink_kaolin_clay", "tfc:white_kaolin_clay", "tfc:kaolin_clay_grass");
        entry.cycle(L.of("Kinds of kaolin clay.", "Виды каолиновой глины."), kaolin);

        mineral(entry, "graphite", "tfc:ore/graphite", "tfc:ore/graphite/gneiss", L.of("Graphite", "Графит"), L.of("Graphite in gneiss.", "Графит в гнейсе."),
            L.of("Graphite is a $(thing)Mineral$() used for $(l:metalworking/fire_clay)Fire Clay$() and gunpowder. It lies in $(thing)Gneiss$(), $(thing)Marble$(), $(thing)Quartzite$() and $(thing)Schist$() below y=60.",
                "Графит $(thing)минерал$() для $(l:metalworking/fire_clay)огнеупорной глины$() и пороха. Он лежит в $(thing)гнейсе$(), $(thing)мраморе$(), $(thing)кварците$() и $(thing)кристаллическом сланце$() ниже y=60."));
        mineral(entry, "cinnabar", "tfc:ore/cinnabar", "tfc:ore/cinnabar/quartzite", L.of("Cinnabar", "Киноварь"), L.of("Cinnabar in quartzite.", "Киноварь в кварците."),
            L.of("Cinnabar is the ore of $(thing)Mercury$(), not of redstone. It lies in veins deep down or high in the mountains, in gneiss, schist, phyllite, granite and quartzite. Fired in the open it loses its mercury as poisonous vapour. A retort with a condenser at 600 °C catches it as liquid metal.",
                "Киноварь руда $(thing)ртути$(), а не редстоуна. Она лежит в жилах глубоко или высоко в горах, в гнейсе, кристаллическом сланце, филлите, граните и кварците. При обжиге на открытом месте ртуть уходит ядовитыми парами. Реторта с конденсатором при 600 °C ловит её жидким металлом."));
        mineral(entry, "cryolite", "tfc:ore/cryolite", "tfc:ore/cryolite/granite", L.of("Cryolite", "Криолит"), L.of("Cryolite in granite.", "Криолит в граните."),
            L.of("Cryolite is a mineral of aluminium and fluorine, found in veins deep down, in granite and diorite. Redstone cannot be made of it, and it has no use in the game yet.",
                "Криолит минерал алюминия и фтора, он лежит в жилах глубоко, в граните и диорите. Редстоуна из него не сделать, и пока в игре ему нет применения."));
        mineral(entry, "saltpeter", "tfc:ore/saltpeter", "tfc:ore/saltpeter/shale", L.of("Saltpeter", "Селитра"), L.of("Saltpeter in shale.", "Селитра в сланце."),
            L.of("Saltpeter is a $(thing)Mineral$() ground in the $(l:crafting/quern)Quern$() for $(thing)Gunpowder$(), for fertilizer, and for the nitrous fumes of acid making. It lies in very large flat beds near the surface in $(l:the_world/geology#sedimentary)Sedimentary$() rock.",
                "Селитра $(thing)минерал$(), который мелют в $(l:crafting/quern)жёрнове$() на $(thing)порох$(), на удобрение и на азотные пары для получения кислот. Она лежит очень большими плоскими пластами у поверхности в $(l:the_world/geology#sedimentary)осадочных$() породах."));
        mineral(entry, "sulfur", "tfc:ore/sulfur", "tfc:ore/sulfur/gabbro", L.of("Sulfur", "Сера"), L.of("Sulfur in gabbro.", "Сера в габбро."),
            L.of("Sulfur is a $(thing)Mineral$() ground in the $(l:crafting/quern)Quern$() for $(thing)Gunpowder$(), and burnt to sulfur dioxide as the start of sulfuric acid. It lies near lava level deep down, in sparse, large, rich veins, in any metamorphic or intrusive rock.",
                "Сера $(thing)минерал$(), который мелют в $(l:crafting/quern)жёрнове$() на $(thing)порох$() и сжигают до сернистого газа как начало серной кислоты. Она лежит у уровня лавы глубоко, в любой интрузивной или метаморфической породе, редкими, но большими и богатыми жилами."));
        mineral(entry, "sylvite", "tfc:ore/sylvite", "tfc:ore/sylvite/chert", L.of("Sylvite", "Сильвин"), L.of("Sylvite in chert.", "Сильвин в кремне."),
            L.of("Sylvite is a $(thing)Mineral$() ground in the quern for $(l:food/fertilizers)Fertilizer$(). It lies in very large flat beds near the surface in $(thing)Chert$(), $(thing)Claystone$() and $(thing)Shale$().",
                "Сильвин $(thing)минерал$(), который мелют в жёрнове на $(l:food/fertilizers)удобрение$(). Он лежит очень большими плоскими пластами у поверхности в $(thing)сланце$(), $(thing)аргиллите$() и $(thing)кремне$()."));
        mineral(entry, "borax", "tfc:ore/borax", "tfc:ore/borax/shale", L.of("Borax", "Бура"), L.of("Borax in shale.", "Бура в сланце."),
            L.of("Borax is a $(thing)Mineral$() ground in the quern into six $(thing)Borax Flux$(), a flux for welding (see $(l:metalworking/flux)Flux$()), and it is the boron of borosilicate glass. It lies in very large flat beds near the surface in $(thing)Shale$(), $(thing)Limestone$() and $(thing)Claystone$().",
                "Бура $(thing)минерал$(), который мелют в жёрнове в шесть $(thing)флюсов из буры$(), флюс для сварки (смотрите $(l:metalworking/flux)флюс$()), и это бор борсиликатного стекла. Она лежит очень большими плоскими пластами у поверхности в $(thing)аргиллите$(), $(thing)известняке$() и $(thing)сланце$()."));
        mineral(entry, "gypsum", "tfc:ore/gypsum", "tfc:ore/gypsum/chalk", L.of("Gypsum", "Гипс"), L.of("Gypsum in chalk.", "Гипс в меле."),
            L.of("Gypsum is a decorative $(thing)Mineral$() for making $(l:mechanics/advanced_building_materials#plaster)Plaster$(). It lies in very large flat beds near the surface in $(l:the_world/geology#sedimentary)Sedimentary$() rock.",
                "Гипс декоративный $(thing)минерал$() для $(l:mechanics/advanced_building_materials#plaster)штукатурки$(). Он лежит очень большими плоскими пластами у поверхности в $(l:the_world/geology#sedimentary)осадочных$() породах."));
        mineral(entry, "halite", "tfc:ore/halite", "tfc:halite", L.of("Halite", "Галит"), L.of("Halite.", "Галит."),
            L.of("Halite is a $(thing)Mineral$() ground in the $(l:crafting/quern)Quern$() into $(thing)Salt$(), an important $(l:food/decay#salting)Preservative$(). It lies in very large flat beds near the surface in $(l:the_world/geology#sedimentary)Sedimentary$() rock.",
                "Галит $(thing)минерал$(), который мелют в $(l:crafting/quern)жёрнове$() в $(thing)соль$(), важный $(l:food/decay#salting)консервант$(). Он лежит очень большими плоскими пластами у поверхности в $(l:the_world/geology#sedimentary)осадочных$() породах."));
        mineral(entry, "emerald", "tfc:ore/emerald", "tfc:ore/emerald/diorite", L.of("Emerald", "Изумруд"), L.of("Emerald in diorite.", "Изумруд в диорите."),
            L.of("Emerald is a $(l:mechanics/gems)Gemstone$(), prized for trade. It forms thin vertical veins up to a hundred blocks tall, in " + INTRUSIVE + "Igneous Intrusive$() rock or by hot springs.",
                "Изумруд $(l:mechanics/gems)самоцвет$(), ценный в торговле. Он образует тонкие вертикальные жилы до ста блоков высотой в " + INTRUSIVE + "интрузивных$() породах или у горячих источников."));
        mineral(entry, "diamond", "tfc:ore/diamond", "tfc:ore/diamond/gabbro", L.of("Kimberlite", "Кимберлит"), L.of("Kimberlite in gabbro.", "Кимберлит в габбро."),
            L.of("Kimberlite is a priceless, purely decorative $(l:mechanics/gems)Gemstone$(). It forms in thin vertical $(l:https://en.wikipedia.org/wiki/Volcanic_pipe)kimberlite pipes$() up to a hundred blocks tall, only in $(thing)Gabbro$(), and rarely in volcanic fissures.",
                "Кимберлит декоративный и бесценный $(l:mechanics/gems)самоцвет$(). Он образуется в тонких вертикальных $(l:https://en.wikipedia.org/wiki/Volcanic_pipe)кимберлитовых трубках$() до ста блоков высотой, только в $(thing)габбро$(), и редко в вулканических трещинах."));
        mineral(entry, "lapis_lazuli", "tfc:ore/lapis_lazuli", "tfc:ore/lapis_lazuli/marble", L.of("Lapis Lazuli", "Лазурит"), L.of("Lapis lazuli in marble.", "Лазурит в мраморе."),
            L.of("Lapis Lazuli is a decorative $(l:mechanics/gems)Gemstone$() for $(l:mechanics/dye)blue dye$(). Its veins are large but far apart, in $(thing)Marble$() and $(thing)Limestone$(), from y=-20 up to y=80.",
                "Лазурит декоративный $(l:mechanics/gems)самоцвет$() для $(l:mechanics/dye)синего красителя$(). Его жилы большие, но редкие, в $(thing)мраморе$() и $(thing)известняке$(), от y=-20 до y=80."));
        mineral(entry, "amethyst", "tfc:ore/amethyst", "tfc:ore/amethyst/marble", L.of("Amethyst", "Аметист"), L.of("Amethyst in marble.", "Аметист в мраморе."),
            L.of("Amethyst is a decorative $(l:mechanics/gems)Gemstone$() that also serves $(l:crafting/glassworking)Glass$(). It lies in geodes below y=30, or under rivers above y=40, in $(thing)Metamorphic$() and $(thing)Sedimentary$() rock.",
                "Аметист декоративный $(l:mechanics/gems)самоцвет$(), идущий и на $(l:crafting/glassworking)стекло$(). Он лежит в жеодах ниже y=30 или под реками выше y=40, в $(thing)метаморфических$() и $(thing)осадочных$() породах."));
        mineral(entry, "opal", "tfc:ore/opal", "tfc:ore/opal/basalt", L.of("Opal", "Опал"), L.of("Opal in basalt.", "Опал в базальте."),
            L.of("Opal is a decorative $(l:mechanics/gems)Gemstone$(). It lies under rivers above y=40, in $(thing)Igneous Extrusive$() and $(thing)Sedimentary$() rock.",
                "Опал декоративный $(l:mechanics/gems)самоцвет$(), он встречается в $(thing)осадочных$() и $(thing)излившихся$() породах под реками выше y=40."));
        mineral(entry, "pyrite", "tfc:ore/pyrite", "tfc:ore/pyrite/granite", L.of("Pyrite", "Пирит"), L.of("Pyrite in granite.", "Пирит в граните."),
            L.of("Pyrite, fool's gold, is a $(l:mechanics/gems)Gemstone$() that strikes sparks from flint, for $(l:getting_started/firepit#firestarter)fire$(). It lies in intrusive and extrusive rock below y=70.",
                "Пирит, золото дураков, $(l:mechanics/gems)самоцвет$(), высекающий искры из кремня для $(l:getting_started/firepit#firestarter)огня$(). Он лежит в интрузивных и излившихся породах ниже y=70."));
        mineral(entry, "ruby", "tfc:ore/ruby", "tfc:ore/ruby/marble", L.of("Ruby", "Рубин"), L.of("Ruby in marble.", "Рубин в мраморе."),
            L.of("Ruby is a decorative $(l:mechanics/gems)Gemstone$(), rare in $(thing)Marble$() below y=-10, and commoner in marble veins of collisional mountains.",
                "Рубин декоративный $(l:mechanics/gems)самоцвет$(), редкий в $(thing)мраморе$() ниже y=-10 и частый в мраморных жилах гор столкновения."));
        mineral(entry, "sapphire", "tfc:ore/sapphire", "tfc:ore/sapphire/gabbro", L.of("Sapphire", "Сапфир"), L.of("Sapphire in gabbro.", "Сапфир в габбро."),
            L.of("Sapphire is a decorative $(l:mechanics/gems)Gemstone$(), found in the fissures of $(thing)Hot Springs$().",
                "Сапфир декоративный $(l:mechanics/gems)самоцвет$(), встречается в трещинах $(thing)горячих источников$()."));
        mineral(entry, "topaz", "tfc:ore/topaz", "tfc:ore/topaz/schist", L.of("Topaz", "Топаз"), L.of("Topaz in schist.", "Топаз в кристаллическом сланце."),
            L.of("Topaz is a decorative $(l:mechanics/gems)Gemstone$(), found in the fissures of volcanoes.",
                "Топаз декоративный $(l:mechanics/gems)самоцвет$(), встречается в трещинах вулканов."));
        return entry;
    }

    /** A graded ore: a page of text, and a page that cycles poor, normal and rich blocks in a rock. */
    private static void graded(Entry entry, String id, L name, String rock, L rockName, L text)
    {
        entry.titled(name, text).anchor(id)
            .showsOnLastPage("tfc:ore/small_" + id, "tfc:ore/poor_" + id, "tfc:ore/normal_" + id, "tfc:ore/rich_" + id)
            .cycle(L.of(name.en() + " ore, in " + rockName.en() + ".", name.ru() + ": порода " + rockName.ru() + "."),
                Structure.block("tfc:ore/poor_" + id + "/" + rock), Structure.block("tfc:ore/normal_" + id + "/" + rock), Structure.block("tfc:ore/rich_" + id + "/" + rock));
    }

    /** A mineral without grades: an item shown large, and one block of it. */
    private static void mineral(Entry entry, String id, String item, String block, L name, L caption, L text)
    {
        entry.spotlight(item, name, text).anchor(id).showsOnLastPage(item)
            .multiblock(Structure.block(block), L.of("", ""), caption);
    }

    private static JsonObject kaolinBed(String clay)
    {
        return Structure.layers(new String[][] {{"X"}, {"Y"}, {"0"}}, "X", "tfc:plant/blood_lily", "Y", clay);
    }

    private WorldGuide() {}
}
