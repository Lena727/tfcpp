package tfcpp.compat.guide;

import java.util.List;

/**
 * The chapters of the TFC++ category about metals: sulfide ores and the retort, then iron and phosphorus.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code Sulfide}, {@code MeltContext}, {@code RetortBlockEntity}, {@code TowerCondensation}, {@code SettlingBaffleBlockEntity} and the generated recipes for the ores; {@code IronSmelting}, {@code IronBlend}, {@code ConverterBath}, {@code ConverterBlockEntity} and {@code MetallurgyData} for iron.
 */
final class SulfidesGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(sulfides(), ironPhosphorus());
    }

    private static Entry sulfides()
    {
        return Entry.of("tfcpp/sulfides", CATEGORY, "tfcpp:retort", 4, L.of("Sulfide Ores and the Retort", "Сульфидные руды и реторта"))
            .titled(
                L.of("Roast, do not smelt", "Обжигают, а не плавят"),
                L.of("In a sulfide ore the metal is bound to sulfur, and heat in air tears them apart: the sulfur burns to $(thing)sulfur dioxide$() and the metal is left as metal or as oxide. That is the real chemistry of $(l:the_world/ores_and_minerals#tetrahedrite)tetrahedrite$(), $(l:the_world/ores_and_minerals#sphalerite)sphalerite$(), $(l:the_world/ores_and_minerals#bismuthinite)bismuthinite$(), cinnabar and galena.$(br2)The gas comes off in the moment the ore is converted in a fire, a crucible or a forge, into the air above that device. A piece gives the metal of its size: small 10 mB, poor 15, normal 25, rich 35.",
                    "В сульфидной руде металл связан с серой, и жар на воздухе их разрывает: сера сгорает в $(thing)сернистый газ$(), а металл остаётся металлом или оксидом. Так на самом деле идёт химия $(l:the_world/ores_and_minerals#tetrahedrite)тетраэдрита$(), $(l:the_world/ores_and_minerals#sphalerite)сфалерита$(), $(l:the_world/ores_and_minerals#bismuthinite)висмутина$(), киновари и галенита.$(br2)Газ выходит в тот миг, когда руда превращается в костре, тигле или горне, в воздух над этим прибором. Кусок даёт металла по своему размеру: мелкий 10 мБ, бедный 15, обычный 25, богатый 35."))
            .anchor("roasting")
            .titled(
                L.of("How much gas", "Сколько газа"),
                L.of("A normal piece of tetrahedrite gives 3.8 moles of sulfur dioxide at once, which is 9% of the air of a whole cell: 92 000 ppm, against 1000 ppm that kills. In a closed hall of 100 cubic metres it is still 920 ppm.$(br2)So roast in the open air, where the gas goes upward and away, or under a chimney. In a closed room it is a death trap, and the poison does not drop below the deadly level for a long while (see $(l:tfcpp/gases)Gases$() and $(l:tfcpp/poisons#lungs)Poisons$()).",
                    "Обычный кусок тетраэдрита сразу даёт 3,8 моля сернистого газа, это 9% воздуха целой клетки: 92 000 ppm против 1000 ppm, которые убивают. В закрытом зале в 100 кубометров это всё равно 920 ppm.$(br2)Поэтому обжигайте на открытом воздухе, где газ уходит вверх и прочь, или под дымоходом. В закрытой комнате это ловушка смерти, и яд долго не опускается ниже смертельного уровня (см. $(l:tfcpp/gases)Газы$() и $(l:tfcpp/poisons#lungs)Яды$())."))
            .heating("tfc:heating/ore/normal_tetrahedrite",
                L.of("$(thing)Tetrahedrite$() melts at 1080 °C into arsenical copper. The pit kiln cannot reach it: use a forge with bellows, or a crucible.",
                    "$(thing)Тетраэдрит$() плавится при 1080 °C в мышьяковистую медь. Ямная печь до этого не доходит: нужен горн с мехами или тигель."))
            .showsOnLastPage("tfc:ore/normal_tetrahedrite")
            .titled(
                L.of("Arsenic and antimony fume", "Дым мышьяка и сурьмы"),
                L.of("Tetrahedrite is Cu12(As,Sb)4S13. For each mole of copper it gives 13/12 mole of sulfur dioxide and 1/12 mole each of arsenic oxide and antimony oxide: from a normal piece 3.8 moles of the gas, 58 g of white arsenic and 86 g of antimony oxide.$(br2)In a hall of 100 cubic metres that is 580 mg of arsenic oxide in each cubic metre: six times the level that kills in under three hours. The fumes settle as white dust (see below), which is raw material, but while it floats it is poison.",
                    "Тетраэдрит это Cu12(As,Sb)4S13. На каждый моль меди он даёт 13/12 моля сернистого газа и по 1/12 моля оксида мышьяка и оксида сурьмы: из обычного куска 3,8 моля газа, 58 г белого мышьяка и 86 г оксида сурьмы.$(br2)В зале в 100 кубометров это 580 мг оксида мышьяка в каждом кубометре: в шесть раз выше уровня, убивающего меньше чем за три часа. Дым оседает белой пылью (см. ниже), и она сырьё, но пока она летает, это яд."))
            .anchor("tetrahedrite")
            .titled(
                L.of("Arsenical copper", "Мышьяковистая медь"),
                L.of("The metal from tetrahedrite is $(thing)arsenical copper$(), melting at 1050 °C. It forges tools and mixes into bronze as copper does, but things made of it carry a mark: they melt back into arsenical copper, and clean copper mixed with it turns arsenical, for the arsenic does not thin out.$(br2)Copper from malachite and native copper stays clean, and stays clean when forged and melted again. Keep the two apart, because the mark cannot be washed off.",
                    "Металл из тетраэдрита это $(thing)мышьяковистая медь$(), плавящаяся при 1050 °C. Из неё куют орудия, и в бронзу она идёт как медь, но вещи из неё несут метку: они плавятся обратно в мышьяковистую медь, а чистая медь, смешанная с ней, становится мышьяковистой, потому что мышьяк не разбавляется.$(br2)Медь из малахита и самородная остаётся чистой и после ковки и переплавки. Держите их порознь: метку не смыть."))
            .anchor("arsenical_copper")
            .heating("tfc:heating/ore/normal_sphalerite",
                L.of("$(thing)Sphalerite$() no longer melts. At 900 °C it roasts to a $(thing)calcine$(), the zinc oxide of the piece, with 1 mole of sulfur dioxide for each mole of zinc.",
                    "$(thing)Сфалерит$() больше не плавится. При 900 °C он обжигается в $(thing)огарок$(), оксид цинка из этого куска, и на каждый моль цинка выходит 1 моль сернистого газа."))
            .showsOnLastPage("tfc:ore/normal_sphalerite")
            .anchor("sphalerite")
            .crafting("tfcpp:crafting/retort", "tfcpp:crafting/retort_condenser",
                L.of("A $(thing)Retort$() is eight fire bricks round a hollow, and its $(thing)Condenser$() has two copper sheets in the ring.",
                    "$(thing)Реторта$() это восемь огнеупорных кирпичей вокруг пустоты, а её $(thing)конденсатор$() имеет два медных листа в кольце."))
            .showsOnLastPage("tfcpp:retort", "tfcpp:retort_condenser")
            .anchor("retort")
            .titled(
                L.of("Zinc: why a retort", "Цинк: зачем реторта"),
                L.of("Carbon takes the oxygen from zinc oxide only above about 1000 °C, and zinc boils at 907 °C. So zinc comes off as vapour, and in an open crucible burns at once to a white smoke of zinc oxide, which gives metal fume fever. A closed retort leads the vapour to a cool $(thing)condenser$(), where it runs off as liquid zinc.$(br2)A calcine needs 1000 °C, charcoal for its carbon (a normal piece takes 2.7 moles, and one charcoal holds 200) and gives zinc by its size, with carbon monoxide.",
                    "Углерод забирает кислород у оксида цинка только выше 1000 °C, а цинк кипит при 907 °C. Поэтому цинк уходит паром, а в открытом тигле тут же сгорает в белый дым оксида цинка, дающий литейную лихорадку. Закрытая реторта ведёт пар в холодный $(thing)конденсатор$(), где он стекает жидким цинком.$(br2)Огарку нужно 1000 °C и древесный уголь как источник углерода (обычному куску 2,7 моля, а в одном куске угля 200). Цинка выходит по размеру куска, и ещё угарный газ."))
            .titled(
                L.of("Mercury from cinnabar", "Ртуть из киновари"),
                L.of("Mercury boils at 357 °C, so cinnabar roasted in the open loses all its mercury as vapour, and the room stays poisoned for hours (see $(l:tfcpp/poisons#mercury)Poisons$()). In the retort it is held at 600 °C beside a condenser and runs off as $(thing)liquid mercury$(): 25 mB for each piece of cinnabar, with 1.7 moles of sulfur dioxide that escape anyway, so ventilate.$(br2)Carry mercury in a jug or a bottle. A $(thing)glass bulb$() (a glass pane) takes 5 mB for a thermometer or manometer.",
                    "Ртуть кипит при 357 °C, поэтому киноварь, обожжённая на открытом воздухе, теряет всю ртуть паром, и комната остаётся отравленной часами (см. $(l:tfcpp/poisons#mercury)Яды$()). В реторте её держат при 600 °C рядом с конденсатором, и она стекает $(thing)жидкой ртутью$(): 25 мБ с каждого куска киновари, а сернистый газ, 1,7 моля, уходит всё равно, так что проветривайте.$(br2)Носите ртуть в кувшине или бутылке. $(thing)Стеклянная колба$() (стеклянная панель) берёт 5 мБ для термометра или манометра."))
            .anchor("mercury")
            .titled(
                L.of("Bismuth and galena", "Висмут и галенит"),
                L.of("$(thing)Bismuthinite$() roasts at 600 °C into liquid bismuth and 1.5 moles of sulfur dioxide for each mole of bismuth: 1.8 moles from a normal piece.$(br2)$(thing)Galena$(), the lead ore of TFC++, roasts at 800 °C into work lead (lead with 0.3% silver in it) and sulfur dioxide, and 3% of its lead goes up as an oxide smoke (see $(l:tfcpp/poisons#chronic)Poisons$()). Refining lead and silver is the chapter $(l:tfcpp/lead_silver)Lead and Silver$().",
                    "$(thing)Висмутин$() обжигается при 600 °C в жидкий висмут и 1,5 моля сернистого газа на каждый моль висмута: 1,8 моля из обычного куска.$(br2)$(thing)Галенит$(), свинцовая руда TFC++, обжигается при 800 °C в веркблей (свинец с 0,3% серебра) и сернистый газ, а 3% его свинца уходят дымом оксида (см. $(l:tfcpp/poisons#chronic)Яды$()). Очистка свинца и серебра это глава $(l:tfcpp/lead_silver)Свинец и серебро$()."))
            .crafting("tfcpp:crafting/settling_baffle", "tfcpp:crafting/condensation_tower",
                L.of("The $(thing)Settling Baffle$() is fire brick and copper plates. The $(thing)Condensation Tower$() is fire brick round lead sheet.",
                    "$(thing)Осадительная перегородка$() это огнеупорный кирпич и медные пластины. $(thing)Конденсационная башня$() это огнеупорный кирпич вокруг свинцового листа."))
            .showsOnLastPage("tfcpp:settling_baffle", "tfcpp:condensation_tower")
            .titled(
                L.of("Catching the fumes", "Улавливание дыма"),
                L.of("Dust of arsenic, antimony, zinc and lead oxide settles by itself when the gas cools. A $(thing)baffle$() in a closed chamber that the fumes cross takes 5% of the dust in the gas each second. Scrape it with an empty hand: it gives powder (0.25 mole, about 50 g), but 5% of what you scrape rises into the air again.$(br2)A $(thing)tower$() sits in a run of pipe and does the same below 250 °C, and also condenses mercury below 100 °C into its tank of 100 mB. Sulfur dioxide passes through both. Several in a row catch more.",
                    "Пыль оксидов мышьяка, сурьмы, цинка и свинца оседает сама, когда газ остывает. $(thing)Перегородка$() в закрытой камере, через которую идёт дым, забирает 5% пыли из газа каждую секунду. Соскоблите её пустой рукой: получится порошок (0,25 моля, около 50 г), но 5% соскоблённого снова поднимается в воздух.$(br2)$(thing)Башня$() стоит в линии труб и делает то же ниже 250 °C, а ещё конденсирует ртуть ниже 100 °C в свой бак на 100 мБ. Сернистый газ проходит сквозь обе. Несколько подряд ловят больше."))
            .anchor("catching")
            .titled(
                L.of("Drawing the fumes in", "Как загнать дым в линию"),
                L.of("A baffle needs no pipe: it takes dust from the six cells around it. A tower stands in a line of pipe, and nothing carries the fume into the line by itself.$(br2)A $(thing)piston pump$() with its back to the cell over the furnace or the crucible draws that cell's air into the pipe in front of it: smoke, the oxides of arsenic, antimony, zinc and lead, mercury vapour and sulfur dioxide, though not coal dust. It enters at room temperature, and the tower can take it. See $(l:tfcpp/lead_silver#intake)Lead and Silver$().",
                    "Перегородке труба не нужна: она берёт пыль из шести клеток вокруг себя. Башня стоит в линии труб, и сам по себе дым в линию не попадёт.$(br2)$(thing)Поршневой насос$() спиной к клетке над печью или тиглем втягивает воздух этой клетки в трубу перед собой: дым, оксиды мышьяка, сурьмы, цинка и свинца, пары ртути и сернистый газ, но не угольную пыль. Он входит с комнатной температурой, и башня может его взять. См. $(l:tfcpp/lead_silver#intake)Свинец и серебро$()."))
            .titled(
                L.of("Working a retort", "Работа с ретортой"),
                L.of("$(li)Set the retort over a forge or a campfire: it takes their heat.$(li)Put a condenser against its side.$(li)Right-click with charcoal for the carbon, then with the charge (up to eight pieces).$(li)Once hot enough it works one piece a second.$(li)An empty hand shows its state.$(li)Run the liquid into a mold, jug or bottle with a right click.",
                    "$(li)Поставьте реторту над горном или костром: она берёт их жар.$(li)Приставьте к ней сбоку конденсатор.$(li)Правым кликом закладывайте уголь для углерода, затем шихту (до восьми кусков).$(li)Достигнув нужной температуры, она перерабатывает кусок в секунду.$(li)Пустая рука показывает состояние.$(li)Жидкость сливайте в форму, кувшин или бутыль правым кликом."))
            .titled(
                L.of("What it keeps and what it spills", "Что она хранит и что проливает"),
                L.of("A fraction of a millibucket stays in the retort as liquid until a whole one has gathered, so nothing is lost to rounding. If the tank is full or holds another liquid, the charge waits.$(br2)Without a condenser a recipe that needs one still runs, but its vapour goes into the room: mercury, or zinc that burns to smoke. Breaking the retort spills it: mercury evaporates, zinc and lead set into ingots, and the last of them burns away as smoke.",
                    "Дробь миллибакета остаётся в реторте жидкостью, пока не наберётся целый, так что при округлении ничего не теряется. Если бак полон или в нём другая жидкость, шихта ждёт.$(br2)Без конденсатора рецепт, которому он нужен, всё равно идёт, но пар уходит в комнату: ртуть или цинк, сгорающий дымом. Если сломать реторту, она разольётся: ртуть испарится, цинк и свинец застынут слитками, а их остаток сгорит дымом."));
    }

    private static Entry ironPhosphorus()
    {
        return Entry.of("tfcpp/iron_phosphorus", CATEGORY, "tfcpp:converter", 5, L.of("Iron and Phosphorus", "Железо и фосфор"))
            .titled(
                L.of("Iron is not made in one melt", "Железо делается не одной плавкой"),
                L.of("Iron ore is an oxide, and heat alone will not make metal of it: carbon has to take its oxygen away. A piece of hematite, magnetite or limonite melted in a forge or a crucible gives only liquid $(thing)iron oxide$() at 1565 °C. It cannot be cast into an ingot. Poured into a mold of 100 mB it sets as four pieces of $(thing)slag$(), 25 mB each, which go back into the $(l:metalworking/bloomery)bloomery$() or the $(l:metalworking/blast_furnace)blast furnace$() to be reduced.$(br2)The ore must meet carbon, and not only fire.",
                    "Железная руда это оксид, и один жар металла из неё не сделает: углерод должен забрать у неё кислород. Кусок гематита, магнетита или лимонита, расплавленный в горне или тигле, даёт лишь жидкий $(thing)оксид железа$() при 1565 °C. Слитком он не отливается. Влитый в форму на 100 мБ, он застывает четырьмя кусками $(thing)шлака$() по 25 мБ, которые возвращают в $(l:metalworking/bloomery)сыродутную печь$() или $(l:metalworking/blast_furnace)доменную печь$() на восстановление.$(br2)Руде нужна встреча с углеродом, а не только с огнём."))
            .anchor("oxide")
            .titled(
                L.of("Phosphorus in brown ore", "Фосфор в бурой руде"),
                L.of("Brown ironstone, the $(thing)limonite$(), holds 0.5 to 2% phosphorus. Its oxide and its slag stay marked as phosphoric. In the bloomery the phosphorus goes into the slag, and the bloom is plain iron. In the blast furnace it goes into the iron: the pig iron is $(thing)phosphoric$() (4% carbon, 1% silicon, 1.5% phosphorus, melting at 1200 °C), and one piece of limonite in the charge makes all the iron of the hearth phosphoric.$(br2)It is good for casting and for the converter.",
                    "Бурый железняк, $(thing)лимонит$(), содержит от 0,5 до 2% фосфора. Его оксид и шлак остаются помеченными как фосфористые. В сыродутной печи фосфор уходит в шлак, и крица чистое железо. В доменной печи он уходит в железо: чугун получается $(thing)фосфористым$() (4% углерода, 1% кремния, 1,5% фосфора, плавится при 1200 °C), и один кусок лимонита в шихте делает фосфористым всё железо горна.$(br2)Он годится для литья и для конвертера."))
            .anchor("phosphoric")
            .titled(
                L.of("Cold short iron", "Хладноломкое железо"),
                L.of("Phosphorus above 0.1% makes iron crack under the hammer when it is cold: smiths called it cold short. Melt phosphoric pig iron with wrought iron, steel or cast iron in a crucible, and you get $(thing)Cold Short Iron$(), which can only be cast or poured again, never forged.$(br2)A crucible does not thin the phosphorus out, but a converter counts the bath by percent: one part of phosphoric pig iron in fifteen of clean iron is only 0.09%, which is clean.",
                    "Фосфор выше 0,1% делает железо трескающимся под молотом на холоде: кузнецы звали его хладноломким. Сплавьте фосфористый чугун со сварочным железом, сталью или литым чугуном в тигле, и получится $(thing)хладноломкое железо$(), которое можно лишь отлить или залить снова, но не ковать.$(br2)Тигель фосфор не разбавляет, а конвертер считает ванну в процентах: одна часть фосфористого чугуна на пятнадцать частей чистого железа даёт всего 0,09%, а это чисто."))
            .titled(
                L.of("The kinds of iron", "Сорта железа"),
                L.of("Iron is named by its carbon, and each kind melts at its own heat:$(li)wrought iron up to 0.2% C, 1535 °C$(li)steel 0.2 to 1%, 1520 °C$(li)high carbon steel 1 to 2.1%, 1450 °C$(li)cast iron 2.1 to 4%, 1350 °C$(li)pig iron over 4%, 1200 °C$(br)An ingot does not remember its exact carbon, silicon and phosphorus, so a kind counts as the least a tap of it can hold: pouring can lose them, never gain. Pig iron that has lost its silicon is cast iron.",
                    "Железо называют по углероду, и у каждого сорта своя температура плавления:$(li)сварочное железо до 0,2% C, 1535 °C$(li)сталь от 0,2 до 1%, 1520 °C$(li)высокоуглеродистая сталь от 1 до 2,1%, 1450 °C$(li)литой чугун от 2,1 до 4%, 1350 °C$(li)чугун больше 4%, 1200 °C$(br)Слиток не помнит точного углерода, кремния и фосфора, поэтому сорт считается по наименьшему, что может быть в выпуске: переливание может это потерять, но не прибавит. Чугун, потерявший кремний, это литой чугун."))
            .anchor("grades")
            .titled(
                L.of("Alloying in the crucible", "Сплавление в тигле"),
                L.of("A $(l:metalworking/crucible)crucible$() averages the carbon by weight. With wrought iron: pig iron 4 to 24% of the melt gives steel, 24 to 52% high carbon steel, 52 to 99% cast iron. Cast iron as the second metal, 8 to 47%, gives steel, and the rest high carbon steel.$(br2)A hammer cannot take carbon out: what the crucible cannot dilute, only the $(l:metalworking/steel)converter$() burns out. High carbon steel is also made steel by diluting it with wrought iron. Cast iron cannot be forged or welded at all, only cast.",
                    "$(l:metalworking/crucible)Тигель$() усредняет углерод по весу. С кованым железом: чугуна от 4 до 24% плавки даёт сталь, от 24 до 52% высокоуглеродистую сталь, от 52 до 99% литой чугун. Литой чугун вторым металлом, от 8 до 47%, даёт сталь, а остальное высокоуглеродистую сталь.$(br2)Молот углерод не выбьет: чего тигель не разбавит, то выжигает только $(l:metalworking/steel)конвертер$(). Высокоуглеродистую сталь тоже делают сталью разбавлением кованым железом. Литой чугун не куётся и не сваривается совсем, его только отливают."))
            .crafting("tfcpp:crafting/converter",
                L.of("The $(thing)Converter$() is seven wrought iron double sheets round a fire brick. Air from a piston pump is blown up through the iron by pipe.",
                    "$(thing)Конвертер$() это семь двойных листов кованого железа вокруг огнеупорного кирпича. Воздух от поршневого насоса по трубе продувается сквозь железо."))
            .showsOnLastPage("tfcpp:converter")
            .anchor("converter")
            .titled(
                L.of("A blow without fuel", "Продувка без топлива"),
                L.of("The vessel holds 2000 mB of iron, about 16 kg. Air burns the silicon first (911 kJ a mole), then the carbon (110 kJ a mole as monoxide), and the iron when the carbon runs short. Those reactions are the only heat, and the vessel loses 0.01 kW for each kelvin: 15 kW at 1500 °C. A blast under one mole of air a second cannot keep the bath hot: one pump (0.2 mole) chills it till the iron sets.$(br2)The blast must beat the weight of the iron: 0.7 atm over a full vessel, and a tenth more.",
                    "Сосуд вмещает 2000 мБ железа, около 16 кг. Воздух сжигает сначала кремний (911 кДж на моль), затем углерод (110 кДж на моль до монооксида), а когда углерода мало, и железо. Эти реакции единственный жар, а сосуд теряет 0,01 кВт на каждый кельвин: 15 кВт при 1500 °C. Дутьё меньше моля воздуха в секунду не держит ванну горячей: один насос (0,2 моля) её остужает, пока железо не застынет.$(br2)Дутьё должно преодолеть вес железа: 0,7 атм над полным сосудом и ещё десятая."))
            .titled(
                L.of("The acid lining cannot", "Кислая футеровка не может"),
                L.of("A new vessel is lined with fire clay, which is silica: an $(thing)acid$() lining. It takes carbon and silicon out of the iron, but the phosphorus stays, and a tap with more than 0.1% of it is cold short iron. Lime thrown into such a vessel would only dissolve the lining, so the game refuses it.$(br2)This was the failure of Bessemer: his converter worked only on pure ores. Thomas and Gilchrist solved it in 1878 with a lining that lime does not dissolve.",
                    "Новый сосуд футерован шамотом, а это кремнезём: $(thing)кислая$() футеровка. Она выводит из железа углерод и кремний, но фосфор остаётся, и выпуск с ним больше 0,1% это хладноломкое железо. Известь, брошенная в такой сосуд, лишь растворила бы футеровку, поэтому игра её не принимает.$(br2)Это была неудача Бессемера: его конвертер работал только на чистых рудах. Томас и Гилкрист решили её в 1878 году футеровкой, которую известь не растворяет."))
            .heating("tfcpp:heating/burnt_dolomite",
                L.of("Dolomite powder (a $(thing)Dolomite Cobble$() ground on a quern gives two) fired at 1500 °C is $(thing)burnt dolomite$(): lime and magnesia that do not slake.",
                    "Порошок доломита (из $(thing)булыжника доломита$() на жёрнове выходит два) при обжиге в 1500 °C становится $(thing)обожжённым доломитом$(): известь и магнезия, которые не гасятся."))
            .showsOnLastPage("tfcpp:powder/dolomite")
            .instantBarrel("tfcpp:barrel/basic_brick",
                L.of("Four burnt dolomite powders and a litre of coal tar make a $(thing)Basic Brick$() in a barrel. It is not fired.",
                    "Четыре порошка обожжённого доломита и литр каменноугольной смолы дают в бочке $(thing)основной кирпич$(). Его не обжигают."))
            .showsOnLastPage("tfcpp:basic_brick")
            .titled(
                L.of("The basic lining", "Основная футеровка"),
                L.of("Right-click an empty converter with basic bricks. Eight of them lay a new $(thing)basic$() lining, and until the eighth it is still the acid one. A basic lining is repaired only with basic bricks. It lasts about 12 000 seconds of blowing, and wears faster above 1650 °C. A worn lining drops with the vessel as an item, and a vessel with no lining left breaks out.$(br2)It is the only lining that lime does not dissolve, and the only one that takes phosphorus out.",
                    "Правый клик основными кирпичами по пустому конвертеру. Восемь штук кладут новую $(thing)основную$() футеровку, а до восьмого она остаётся кислой. Основную футеровку чинят только основными кирпичами. Она служит около 12 000 секунд продувки и изнашивается быстрее выше 1650 °C. Изношенная футеровка выпадает вместе с сосудом предметом, а сосуд без футеровки прорывается.$(br2)Это единственная футеровка, которую известь не растворяет, и единственная, которая выводит фосфор."))
            .anchor("basic_lining")
            .titled(
                L.of("Lime and the afterblow", "Известь и передувка"),
                L.of("Throw $(l:metalworking/flux)lime powder$() into the bath (7.5 moles of CaO each). Each mole of silicon takes 2 of CaO and each mole of phosphorus another 2: a full vessel of phosphoric pig iron wants about 27 moles, four powders.$(br2)Phosphorus burns after the carbon, in the $(thing)afterblow$(), with thick white smoke over the mouth, and gives 745 kJ a mole. Blow on until the smoke ends. With too little lime the phosphorus stays, and silica without lime eats the lining away.",
                    "Бросайте в ванну $(l:metalworking/flux)порошок извести$() (по 7,5 моля CaO). Каждый моль кремния забирает 2 CaO и каждый моль фосфора ещё 2: на полный сосуд фосфористого чугуна нужно около 27 молей, четыре порошка.$(br2)Фосфор горит после углерода, в $(thing)передувке$(), с густым белым дымом над горловиной, и даёт 745 кДж на моль. Дуйте, пока дым не кончится. Если извести мало, фосфор остаётся, а кремнезём без извести разъедает футеровку."))
            .anchor("afterblow")
            .quern("tfcpp:quern/thomas_meal",
                L.of("The slag of the blow comes off the top as $(thing)Thomas slag$() (2 moles of phosphorus a piece). Ground, it gives four powders of $(thing)Thomas meal$(): a phosphate fertiliser.",
                    "Шлак продувки снимается сверху как $(thing)томасов шлак$() (2 моля фосфора на кусок). Размолотый, он даёт четыре порошка $(thing)томасовой муки$(): фосфатное удобрение."))
            .showsOnLastPage("tfcpp:slag/thomas")
            .titled(
                L.of("Wet iron kills", "Мокрое железо убивает"),
                L.of("A litre of water in the blast pipe turns to 1700 litres of steam in a bath of iron, and the vessel is torn apart. Water must never reach the pipe, and the first drop must be gone before the blow. Hot iron from a broken vessel sets the floor alight and burns anyone near.$(br2)The smoke over the mouth is carbon monoxide and nitrogen, and in the afterblow the fume of phosphorus oxide: blow in the open or under a chimney (see $(l:tfcpp/gases)Gases$()). To read the flame, see $(l:metalworking/steel)Steel$().",
                    "Литр воды в трубе дутья превращается в ванне железа в 1700 литров пара, и сосуд разрывает. Вода не должна попадать в трубу, и первая капля должна уйти до продувки. Горячее железо из сломанного сосуда поджигает пол и обжигает всех рядом.$(br2)Дым над горловиной это угарный газ и азот, а в передувке ещё дым оксида фосфора: дуйте на открытом месте или под дымоходом (см. $(l:tfcpp/gases)Газы$()). О чтении пламени см. $(l:metalworking/steel)Сталь$()."));
    }

    private SulfidesGuide() {}
}
