package tfcpp.compat.guide;

import java.util.List;

/**
 * The first chapters of the TFC++ category, about the air: gases and ventilation, poisons and mine safety.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code GasMath}, {@code FireSource}, {@code Combustion}, {@code GasEmission}, {@code MineFanBlockEntity} and {@code ShaftPhysics} for the gases; {@code Physiology}, {@code Breathing} and {@code SentinelCageItem} for the poisons; {@code SeamGas}, {@code Flammability}, {@code Deflagration}, {@code DustSources} and {@code DavyLamp} for the mine.
 */
final class GasesGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(gases(), poisons(), mineSafety());
    }

    private static Entry gases()
    {
        return Entry.of("tfcpp/gases", CATEGORY, "tfcpp:mine_fan", 1, L.of("Gases and Ventilation", "Газы и вентиляция"))
            .titled(
                L.of("Air by the cubic metre", "Воздух по кубометрам"),
                L.of("The air of the world is counted in cells of one cubic metre. At 20 °C and one atmosphere a cell holds 41.6 moles of gas ($(bold)pV = nRT$()), and each gas in it is counted apart, in parts per million. The grid lives in the chunks and costs nothing where the air is clean.$(br2)Gases move as real ones do: they mix, they sink or rise by their weight, and fires, rock and opened coal faces add them. What drifts out into the open sky above is gone.",
                    "Воздух мира считается клетками по кубометру. При 20 °C и одной атмосфере в клетке 41,6 моля газа ($(bold)pV = nRT$()), и каждый газ в ней учтён отдельно, в частях на миллион. Сетка хранится в чанках и ничего не стоит там, где воздух чист.$(br2)Газы ведут себя как настоящие: смешиваются, тонут и поднимаются по весу, а добавляют их огонь, порода и вскрытые угольные забои. Что ушло в открытое небо, то пропало."))
            .anchor("grid")
            .titled(
                L.of("Light and heavy", "Лёгкие и тяжёлые"),
                L.of("A gas is lighter or heavier than air (29 g per mole) by its molar mass.$(li)$(bold)Lighter:$() hydrogen 2, methane 16, ammonia 17. They rise and lie under the roof.$(li)$(bold)About as heavy:$() carbon monoxide and the nitrogen of smoke, 28. They spread through the room, up to the height of your face.$(li)$(bold)Heavier:$() hydrogen sulfide 34, hydrogen chloride 36, carbon dioxide 44, nitrous fumes 46, sulfur dioxide 64, chlorine 71, arsenic oxide 198, mercury vapour 201. They pour down stairs and shafts and fill the low places.",
                    "Газ легче или тяжелее воздуха (29 г на моль) по своей молярной массе.$(li)$(bold)Легче:$() водород 2, метан 16, аммиак 17. Они поднимаются и лежат под сводом.$(li)$(bold)Почти как воздух:$() угарный газ и азот дыма, 28. Они расходятся по комнате и доходят до уровня лица.$(li)$(bold)Тяжелее:$() сероводород 34, хлороводород 36, углекислый газ 44, нитрозные газы 46, сернистый газ 64, хлор 71, оксид мышьяка 198, пары ртути 201. Они стекают по лестницам и стволам и заполняют низины."))
            .anchor("weight")
            .titled(
                L.of("Layers", "Слои"),
                L.of("Where a heavy cell lies over a lighter one, the two overturn and the gas falls as a body. Where a light layer lies over a heavier one it is stable, and the step in density holds the mixing back: a difference of 0.1% cuts it to a third. So firedamp lies along a roof for days, and carbon dioxide stays in a still cave for ages.$(br2)A gas that is already mixed evenly into the air never separates again. A layer is cleared only by moving air.",
                    "Если тяжёлая клетка лежит над лёгкой, они опрокидываются, и газ падает целиком. Если лёгкий слой лежит над тяжёлым, он устойчив, и скачок плотности удерживает смешение: разница в 0,1% снижает его втрое. Поэтому рудничный газ днями лежит под кровлей, а углекислый стоит в тихой пещере веками.$(br2)Газ, уже ровно смешанный с воздухом, больше не разделяется. Слой разгоняет только движение воздуха."))
            .titled(
                L.of("What a fire gives off", "Что даёт огонь"),
                L.of("Every fire burns fuel at a power of its own, and turns a share of its carbon into $(thing)carbon monoxide$(), the rest into carbon dioxide:$(li)campfire 15 kW, 5% as CO$(li)charcoal forge 30 kW, 10%; firebox 25 kW, 8%; pit kiln 15 kW, 15%$(li)charcoal pit 2 kW, 30%, with some methane$(li)bloomery 60 kW, 35%$(li)blast furnace 200 kW, 75%$(br)A fire that takes $(l:metalworking/bellows)bellows$() burns at twice the rate.",
                    "Каждый огонь сжигает топливо со своей мощностью и часть углерода превращает в $(thing)угарный газ$(), остальное в углекислый:$(li)костёр 15 кВт, 5% в CO$(li)угольная кузница 30 кВт, 10%; топка 25 кВт, 8%; ямная печь 15 кВт, 15%$(li)угольная яма 2 кВт, 30%, и немного метана$(li)сыродутная печь 60 кВт, 35%$(li)доменная печь 200 кВт, 75%$(br)Огонь с $(l:metalworking/bellows)мехами$() горит вдвое быстрее."))
            .anchor("fires")
            .titled(
                L.of("A fire needs air", "Огню нужен воздух"),
                L.of("A fire eats the oxygen of its room. Below 19% oxygen the burn turns dirty and the share of CO climbs, to four fifths of the carbon. Below 12% embers cannot go on: a campfire, a forge and a blast furnace go out. A bloomery, a firebox, a pit kiln and a charcoal pit cannot be put out so: their fuel smoulders on, but gives nothing to air like that.$(br2)So a fire in a shut room first poisons it, and only then goes out. See $(l:getting_started/firepit)the campfire$() and $(l:metalworking/charcoal_forge)the forge$().",
                    "Огонь съедает кислород комнаты. Ниже 19% кислорода горение становится грязным, и доля CO растёт до четырёх пятых углерода. Ниже 12% угли не держатся: гаснут костёр, горн и доменная печь. Сыродутную печь, топку, ямную печь и угольную яму так не погасить: топливо в них тлеет дальше, но такому воздуху ничего не отдаёт.$(br2)Значит, огонь в закрытой комнате сначала травит её и лишь потом гаснет. См. $(l:getting_started/firepit)костёр$() и $(l:metalworking/charcoal_forge)кузницу$()."))
            .titled(
                L.of("Where the smoke goes", "Куда уходит дым"),
                L.of("Hot flue gas rises in a plume until it meets a ceiling, then cools and spreads down from there, as smoke fills a room from the top. Under open sky, or up a chimney that ends in it, it is simply gone.$(br2)A device under two layers of earth or more cannot vent. A tenth of its gas gets through the cover, to the first open place above it, and the rest stays beside it. That is how charcoal pits have killed people who slept next to them. A $(l:metalworking/charcoal_pit)pit$() wants one layer of cover only.",
                    "Горячий дым поднимается шлейфом до потолка, потом остывает и расползается вниз, как дым заполняет комнату сверху. Под открытым небом или в трубе, которая выходит на него, он просто уходит.$(br2)Устройство под двумя слоями земли и толще не дымит наружу. Десятая часть газа проходит сквозь покрытие в первое открытое место над ним, остальное остаётся рядом. Так угольные ямы убивали тех, кто спал около них. Для $(l:metalworking/charcoal_pit)ямы$() нужен всего один слой покрытия."))
            .titled(
                L.of("Wells and shafts", "Колодцы и стволы"),
                L.of("Open air takes gas from the top of a column. A cell in an open pit loses $(bold)0.3 e^(-d/3)$() of its gas each second, d being the depth below the ground around it in metres: 30% a second at the surface, 11% at 3 m, half a percent at 12 m. A well, a prospecting pit and a mine shaft hold heavy gas as a cave does. A roof over a cell stops the exchange altogether.$(br2)Never go down into a pit you have not tested.",
                    "Открытый воздух забирает газ с верха столба. Клетка в открытой яме теряет $(bold)0,3 e^(-d/3)$() своего газа в секунду, где d глубина под землёй вокруг в метрах: 30% в секунду у поверхности, 11% на 3 м, полпроцента на 12 м. Колодец, шурф и шахтный ствол держат тяжёлый газ, как пещера. Крыша над клеткой останавливает обмен совсем.$(br2)Не спускайтесь в яму, которую не проверили."))
            .crafting("tfcpp:crafting/mine_fan",
                L.of("Only moving air clears a mine. The $(thing)Mine Fan$() is six copper sheets round two brass mechanisms. It stands on a $(l:mechanics/mechanical_power)shaft$() and blows out of its front.",
                    "Рудник проветривает только движущийся воздух. $(thing)Рудничный вентилятор$() это шесть медных листов вокруг двух латунных механизмов. Он стоит на $(l:mechanics/mechanical_power)валу$() и дует из своей передней стороны."))
            .showsOnLastPage("tfcpp:mine_fan")
            .anchor("fan")
            .titled(
                L.of("The fan and its shaft", "Вентилятор и его вал"),
                L.of("At the speed of a water wheel the fan moves about 4 cubic metres of air a second and takes about 1 kW off the shaft. The flow grows with the speed, the power with its cube, so a slow shaft moves air cheaply and a fast one loads the wheel hard.$(br2)The fan draws along the open spaces behind it. Gas moves about a cell a second, one shell of cells at a time, as fast as air moved in the old mines. A fan lifts no settled dust.",
                    "На скорости водяного колеса вентилятор гонит около 4 кубометров воздуха в секунду и берёт с вала около 1 кВт. Расход растёт со скоростью, мощность с её кубом, так что медленный вал гонит воздух дёшево, а быстрый сильно нагружает колесо.$(br2)Вентилятор тянет по открытым пространствам позади себя. Газ идёт примерно по клетке в секунду, слой клеток за слоем, так же быстро, как двигался воздух в старых шахтах. Осевшую пыль вентилятор не поднимает."))
            .titled(
                L.of("Air must come from somewhere", "Воздуху нужно откуда прийти"),
                L.of("A fan moves air only where air has somewhere to come from: the space it draws on must lead to open sky, or be larger than the 512 cells the fan can walk. A closed room or a dead end has no such way. There the fan only stirs, and a right click says so.$(br2)The law after the Hartley disaster of 1862 required a second shaft to every colliery for just this reason: one opening gives air nowhere to enter.",
                    "Вентилятор двигает воздух лишь там, где ему есть откуда прийти: пространство, из которого он тянет, должно выходить на открытое небо или быть больше 512 клеток, которые вентилятор обходит. У закрытой комнаты и тупика такого пути нет. Там вентилятор только мешает воздух, и правый клик это скажет.$(br2)Закон после катастрофы в Хартли в 1862 году потребовал второй ствол в каждой шахте именно поэтому: с одним выходом воздуху некуда входить."))
            .titled(
                L.of("Seeing the air", "Как увидеть воздух"),
                L.of("Most gases cannot be seen. Some announce themselves: a message on the screen tells of a sharp smell, and the $(l:tfcpp/mine_safety)Davy lamp$() shows firedamp. Carbon monoxide has no smell at all. Read $(l:tfcpp/poisons)Poisons$() before you work near fire in a closed place.$(br2)A pipe vent passes gas both ways, by the pressure on each side (see $(l:tfcpp/pipes)Pipes and Pressure$()). Operators can read a cell with $(bold)/tfcpp gas read$(), add gas with $(bold)/tfcpp gas add$() and count the chunks with $(bold)/tfcpp gas stats$().",
                    "Большинство газов не видно. Некоторые выдают себя сами: сообщение на экране говорит о резком запахе, а $(l:tfcpp/mine_safety)лампа Дэви$() показывает рудничный газ. У угарного газа запаха нет совсем. Прочтите $(l:tfcpp/poisons)Яды$(), прежде чем работать у огня в закрытом месте.$(br2)Выпуск трубы пропускает газ в обе стороны, по давлению с каждой стороны (см. $(l:tfcpp/pipes)Трубы и давление$()). Операторы читают клетку командой $(bold)/tfcpp gas read$(), добавляют газ $(bold)/tfcpp gas add$() и считают чанки $(bold)/tfcpp gas stats$()."));
    }

    private static Entry poisons()
    {
        return Entry.of("tfcpp/poisons", CATEGORY, "tfcpp:sentinel_cage", 2, L.of("Poisons", "Яды"))
            .titled(
                L.of("Two kinds of poison", "Два рода яда"),
                L.of("You breathe the air of the cell at your eyes, once a second. Some gases harm at once, as long as you breathe them. Others leave a $(bold)dose$() in the body, which the body clears slowly. A doctor would call them acute and chronic.$(br2)Poison damage goes through armour and shields, and it throws you nowhere. Nothing worn helps: what helps is clean air, and warning before you go in. Dying cures no slow poison (see the last pages).",
                    "Вы дышите воздухом клетки у ваших глаз, раз в секунду. Одни газы вредят сразу, пока вы ими дышите. Другие оставляют в теле $(bold)дозу$(), которую тело выводит медленно. Врач назвал бы их острыми и хроническими.$(br2)Урон от яда проходит сквозь броню и щит и никуда не отбрасывает. Надетое не помогает: помогает чистый воздух и предупреждение до того, как вы вошли. Смерть не лечит медленный яд (см. последние страницы)."))
            .titled(
                L.of("Carbon monoxide", "Угарный газ"),
                L.of("Haemoglobin binds $(thing)carbon monoxide$() 246 times harder than oxygen, so a little of it fills the blood. How much, by the law of Haldane, depends on the oxygen at the lungs as well: 100 ppm settles at about 15% of the blood, 400 ppm at 42%, 1000 ppm at 64%.$(li)From 20%: slowness and mining fatigue.$(li)From 35%: nausea and heavy slowness.$(li)From 50%: death.$(br)There is no smell, no colour, no warning.",
                    "Гемоглобин связывает $(thing)угарный газ$() в 246 раз прочнее кислорода, поэтому немного газа заполняет кровь. Сколько именно, по закону Холдейна зависит и от кислорода в лёгких: 100 ppm дают около 15% крови, 400 ppm 42%, 1000 ppm 64%.$(li)От 20%: замедление и усталость при копании.$(li)От 35%: тошнота и сильное замедление.$(li)От 50%: смерть.$(br)Ни запаха, ни цвета, ни предупреждения."))
            .anchor("carbon_monoxide")
            .titled(
                L.of("How fast CO works", "Как быстро действует CO"),
                L.of("The blood fills slowly from a thin mix and fast from a thick one. At 400 ppm the symptoms come after about 25 minutes and incapacity after more than an hour: it never reaches death. At 1000 ppm they come after 9 minutes and death after 36. At 12 800 ppm, a room thick with smoke, death comes in about two minutes.$(br2)In clean air the blood clears itself with a half-time of 5 hours of process time: 5 minutes of real time. Air thinned of oxygen raises every figure above.",
                    "Из жидкой смеси кровь заполняется медленно, из густой быстро. При 400 ppm симптомы приходят минут через 25, а потеря сил больше чем через час: до смерти дело не доходит. При 1000 ppm симптомы через 9 минут, смерть через 36. При 12 800 ppm, в комнате, густой от дыма, смерть наступает примерно за две минуты.$(br2)В чистом воздухе кровь очищается с периодом полувыведения 5 часов времени процесса, то есть 5 минут реального. Воздух с пониженным кислородом поднимает все цифры выше."))
            .titled(
                L.of("Gases that burn the lungs", "Газы, жгущие лёгкие"),
                L.of("Parts per million for slight, serious and deadly harm:$(li)sulfur dioxide 20, 100, 1000$(li)hydrogen sulfide 50, 100, 700$(li)chlorine 3, 10, 1000$(li)ammonia 100, 300, 5000$(li)nitrous fumes 5, 20, 200$(li)hydrogen chloride 5, 50, 1300$(li)carbon dioxide 4%, 10%, 17%$(br)Slight harm weakens you. Serious harm takes half a percent of your health every second, about three minutes in all. Deadly harm takes a tenth a second.",
                    "Частей на миллион для лёгкого, серьёзного и смертельного вреда:$(li)сернистый газ 20, 100, 1000$(li)сероводород 50, 100, 700$(li)хлор 3, 10, 1000$(li)аммиак 100, 300, 5000$(li)нитрозные газы 5, 20, 200$(li)хлороводород 5, 50, 1300$(li)углекислый газ 4%, 10%, 17%$(br)Лёгкий вред ослабляет. Серьёзный отнимает полпроцента здоровья в секунду, около трёх минут в сумме. Смертельный отнимает десятую долю в секунду."))
            .anchor("lungs")
            .titled(
                L.of("Smell and its limits", "Запах и его пределы"),
                L.of("Most of these announce themselves, and a message tells you of the smell: sulfur dioxide from 1 ppm, nitrous fumes and hydrogen chloride from 1, chlorine from 0.5, ammonia from 5. You hear it at most once in ten seconds.$(br2)$(bold)Hydrogen sulfide$() smells of rotten eggs from 0.05 ppm, but above 100 ppm it numbs the nerve of smell. The worst air gives no warning, and kills those who go in after a friend. Carbon monoxide, methane, hydrogen, nitrogen and carbon dioxide have no smell.",
                    "Большинство выдаёт себя, и сообщение расскажет о запахе: сернистый газ с 1 ppm, нитрозные газы и хлороводород с 1, хлор с 0,5, аммиак с 5. Сообщение приходит не чаще раза в десять секунд.$(br2)$(bold)Сероводород$() пахнет тухлыми яйцами с 0,05 ppm, но выше 100 ppm он парализует обоняние. Самый тяжёлый воздух не даёт предупреждения и убивает тех, кто идёт за другом. Угарный газ, метан, водород, азот и углекислый газ запаха не имеют."))
            .titled(
                L.of("No air to breathe", "Нечем дышать"),
                L.of("Smoke and heavy gases push the oxygen out. Below 16% oxygen you slow down. Below 10% you are sick and lose half a percent of your health every second, and below 6% a tenth. Fresh air is 20.95%.$(br2)A fire warns sooner than a lung does: a flame burns badly from 19% and goes out at 12%, long before the air is fatal (see $(l:tfcpp/gases#fires)Gases$()). A smothered fire warns only those who watch it.",
                    "Дым и тяжёлые газы вытесняют кислород. Ниже 16% кислорода вы замедляетесь. Ниже 10% вам плохо, и вы теряете полпроцента здоровья в секунду, а ниже 6% десятую долю. В свежем воздухе 20,95%.$(br2)Огонь предупредит раньше лёгких: пламя горит плохо с 19% и гаснет на 12%, задолго до смертельного воздуха (см. $(l:tfcpp/gases#fires)Газы$()). Задушенный огонь предупреждает лишь тех, кто на него смотрит."))
            .titled(
                L.of("Slow poisons", "Медленные яды"),
                L.of("You breathe 20 litres a minute and keep 80% of what is in them. The poison stays in the body as a dose in milligrams, which falls by half in a set time. These times are process time: a day is 24 real minutes (see $(l:tfcpp/introduction)the introduction$()).$(li)arsenic: half in 4 days; symptoms at 20 mg, incapacity 70, death 150$(li)mercury: 60 days; 5, 20, 50 mg$(li)lead: 30 days; 30, 100, 300 mg$(li)zinc fume: 6 hours; 15, 60 mg, no death",
                    "Вы дышите 20 литров в минуту и задерживаете 80% того, что в них. Яд остаётся в теле дозой в миллиграммах, которая убывает вдвое за определённое время. Это время процесса: сутки равны 24 реальным минутам (см. $(l:tfcpp/introduction)введение$()).$(li)мышьяк: вдвое за 4 суток; симптомы с 20 мг, потеря сил 70, смерть 150$(li)ртуть: 60 суток; 5, 20, 50 мг$(li)свинец: 30 суток; 30, 100, 300 мг$(li)дым цинка: 6 часов; 15, 60 мг, без смерти"))
            .anchor("chronic")
            .titled(
                L.of("Arsenic and antimony", "Мышьяк и сурьма"),
                L.of("The white fume of $(thing)arsenic oxide$() comes from tetrahedrite (see $(l:tfcpp/sulfides)Sulfide ores$()). Arsenic leaves the body slowly. At the level of incapacity you are weak and sick, and from 150 mg you lose two percent of your health a second.$(br2)Antimony oxide does the same harm at a third of the strength, and is counted with it. Air with 100 mg of arsenic oxide in a cubic metre brings symptoms in 13 minutes and death in under three hours. The fume settles as dust, and then it is no longer breathed.",
                    "Белый дым $(thing)оксида мышьяка$() идёт от тетраэдрита (см. $(l:tfcpp/sulfides)Сульфидные руды$()). Мышьяк выводится из тела медленно. На уровне потери сил вы слабы, вас тошнит, а с 150 мг вы теряете два процента здоровья в секунду.$(br2)Оксид сурьмы вредит так же при трети силы и считается вместе с ним. Воздух со 100 мг оксида мышьяка в кубометре даёт симптомы за 13 минут и смерть меньше чем за три часа. Дым оседает пылью, и тогда им уже не дышат."))
            .titled(
                L.of("Mercury", "Ртуть"),
                L.of("Air at 20 °C holds at most 13 mg of mercury vapour a cubic metre (1.6 ppm). The rest settles as a film, which evaporates again within about 20 seconds: a spill keeps the room at that level for long, and one airing does not clear it. In such air a tremor comes after 24 minutes, incapacity after an hour and a half, death after four hours.$(br2)Swallowed mercury is taken up through the gut at 0.01%: a millilitre is 1.35 mg, a 100 mB drink from a jug 135 mg, felt only over the next day.",
                    "Воздух при 20 °C держит не больше 13 мг паров ртути на кубометр (1,6 ppm). Остальное оседает плёнкой, которая снова испаряется секунд за 20: разлив долго держит комнату на этом уровне, и одно проветривание её не очистит. В таком воздухе дрожь приходит через 24 минуты, потеря сил через полтора часа, смерть через четыре.$(br2)Проглоченная ртуть усваивается через кишечник на 0,01%: миллилитр это 1,35 мг, глоток из кувшина в 100 мБ 135 мг, и чувствуется это лишь в течение суток."))
            .anchor("mercury")
            .titled(
                L.of("Lead and zinc fume", "Свинец и дым цинка"),
                L.of("$(thing)Lead$() fume (the smoke of litharge) gives weakness, then palsy of the wrists (mining fatigue), and at 300 mg death at half a percent of health a second. It leaves the body over weeks of process time, so the dose of a day at the hearth is carried a long while.$(br2)$(thing)Zinc oxide fume$() gives metal fume fever: hunger and weakness from 15 mg. It is awful and passes within hours, and it never kills. Brass founders knew it as the Monday fever.",
                    "Дым $(thing)свинца$() (дым глёта) даёт слабость, затем паралич кистей (усталость при копании), а на 300 мг смерть при потере полпроцента здоровья в секунду. Выводится он неделями времени процесса, поэтому доза одного дня у горна носится долго.$(br2)Дым $(thing)оксида цинка$() даёт литейную лихорадку: голод и слабость от 15 мг. Это тяжело и проходит за часы, но не убивает никогда. Литейщики латуни знали её как понедельничную лихорадку."))
            .titled(
                L.of("A death does not cure", "Смерть не лечит"),
                L.of("A player who dies keeps his slow poisons, up to the level of incapacity and no higher: death cannot be used as a cure, and a body is not killed again on rising. The blood is fresh, and mercury still in the gut is lost.$(br2)A poisoned man is helped only by clean air and time. Learn the signs: the weakness and the tremor, the icons of the effects, the message of a smell. Then build ventilation and condensers, and warn others.",
                    "Умерший игрок сохраняет медленные яды, но не выше уровня потери сил: смертью нельзя лечиться, и тело не убивает повторно. Кровь свежая, а ртуть, ещё лежавшая в кишечнике, теряется.$(br2)Отравленному помогают только чистый воздух и время. Выучите признаки: слабость и дрожь, значки эффектов, сообщение о запахе. Затем стройте вентиляцию и конденсаторы и предупреждайте других."))
            .crafting("tfcpp:crafting/animal_cage",
                L.of("A $(thing)Cage$() of eight copper rods holds a rat or a quail: right-click the animal with it.",
                    "$(thing)Клетка$() из восьми медных прутьев вмещает крысу или перепела: правый клик клеткой по животному."))
            .showsOnLastPage("tfcpp:animal_cage", "tfcpp:sentinel_cage")
            .titled(
                L.of("The animal in the cage", "Животное в клетке"),
                L.of("John Scott Haldane proposed it after the mine disasters of the 1890s. A small animal takes carbon monoxide in twenty times faster than you do, so it falls long before you feel anything. Carry the cage with you.$(br2)It is uneasy at 10% of its blood, collapsed at 20%, dead at 50%. It also dies of a gas at the serious level, or of starving air. It recovers in clean air. Feed it grain from the other hand at least every three days.",
                    "Её предложил Джон Скотт Холдейн после шахтных катастроф 1890-х. Мелкое животное набирает угарный газ в двадцать раз быстрее вас, поэтому падает задолго до того, как вы что-то почувствуете. Носите клетку с собой.$(br2)Оно беспокоится на 10% крови, падает на 20% и гибнет на 50%. Гибнет и от газа серьёзного уровня, и от задушенного воздуха. В чистом воздухе оно оправляется. Кормите зерном из другой руки не реже раза в три дня."));
    }

    private static Entry mineSafety()
    {
        return Entry.of("tfcpp/mine_safety", CATEGORY, "tfcpp:davy_lamp", 3, L.of("Mine Safety", "Шахтная безопасность"))
            .titled(
                L.of("Gas in coal", "Газ в угле"),
                L.of("Coal holds methane on its inner surface, and the deeper it lies the more it holds (an isotherm of Langmuir). Bituminous coal holds up to 25 cubic metres a tonne, nearly all methane. Lignite holds 6, only a fifth of it methane, the rest carbon dioxide.$(br2)A block of coal (1.3 tonnes) gives a tenth of its gas at once when broken: at 30 m deep about 0.4 cubic metres of methane, at 100 m more than one. That is enough to bring 20 cubic metres of air up to the lower limit of an explosion.",
                    "Уголь держит метан на внутренней поверхности, и чем глубже он лежит, тем больше (изотерма Ленгмюра). Каменный уголь держит до 25 кубометров на тонну, почти всё метан. Лигнит 6, и метана в нём лишь пятая часть, остальное углекислый газ.$(br2)Блок угля (1,3 тонны) при ломке сразу отдаёт десятую часть своего газа: на глубине 30 м около 0,4 кубометра метана, на 100 м больше кубометра. Этого хватит, чтобы довести 20 кубометров воздуха до нижнего предела взрыва."))
            .anchor("seam_gas")
            .titled(
                L.of("Pockets and slow faces", "Карманы и медленные забои"),
                L.of("One region in 64 (4 by 4 by 4 blocks) of rock beside a seam holds a pocket of free gas, 4 to 16 cubic metres, squeezed by the rock above it: at 30 m depth it lets out 16 to 64 cubic metres of methane at once, with a hiss, when you break into it.$(br2)A newly bared face of coal bleeds out the same tenth slowly, with a time constant of 24 minutes. Gas keeps collecting in a working no one is in, and an old working is full of it.",
                    "Каждая шестьдесят четвёртая область (4 на 4 на 4 блока) породы у пласта хранит карман свободного газа, от 4 до 16 кубометров, сжатый породой над ним: на глубине 30 м он выпускает от 16 до 64 кубометров метана сразу, с шипением, когда в него попадаешь.$(br2)Только что вскрытый угольный забой отдаёт ту же десятую долю медленно, с постоянной времени 24 минуты. Газ копится в выработке, где никого нет, и старая выработка полна им."))
            .titled(
                L.of("Blackdamp", "Чёрный рудничный газ"),
                L.of("Near the surface, above 15 m, air has reached the coal and has been burning its oxygen away for ages. What comes out there is $(thing)blackdamp$(): 13% carbon dioxide, the rest nitrogen, and no methane at all.$(br2)It smothers a flame at 16% oxygen and a person soon after: slowness below 16%, collapse below 10% (see $(l:tfcpp/poisons)Poisons$()). It is heavy and fills the low galleries and shaft bottoms. Pockets beside lignite hold it too.",
                    "Вблизи поверхности, выше 15 м, воздух добрался до угля и века выжигал его кислород. Там выходит $(thing)чёрный рудничный газ$(): 13% углекислого газа, остальное азот, метана совсем нет.$(br2)Он гасит пламя при 16% кислорода, а человека вскоре после: замедление ниже 16%, обморок ниже 10% (см. $(l:tfcpp/poisons)Яды$()). Он тяжёл и заполняет нижние штреки и дно стволов. Его дают и карманы возле лигнита."))
            .titled(
                L.of("What lights it", "Что его зажигает"),
                L.of("An open flame lights a burnable mixture: a $(thing)torch$(), even in the hand, a campfire, a burning furnace, charcoal pit or bloomery, lava, a candle, an oil lamp, a gas burner. Carry none of them into a coal seam.$(br2)Iron and steel tools throw a spark when they break rock: 5% of the blows, if there is gas beside the block. Bronze and copper never spark, which is why miners kept tools of them. A $(thing)Davy lamp$(), the one flame you may carry, is shown on the pages after.",
                    "Открытое пламя зажигает горючую смесь: $(thing)факел$(), даже в руке, костёр, горящая печь, угольная яма или сыродутная печь, лава, свеча, масляная лампа, газовая горелка. Не вносите ничего из этого в угольный пласт.$(br2)Железные и стальные орудия при ломке породы дают искру: 5% ударов, если рядом с блоком есть газ. Бронза и медь не искрят никогда, поэтому шахтёры держали орудия из них. $(thing)Лампа Дэви$(), единственный огонь, который можно нести, показана на следующих страницах."))
            .anchor("ignition")
            .titled(
                L.of("When the gas burns", "Когда газ горит"),
                L.of("Methane burns only between 5% and 15% of the air, and not at all below 12% oxygen. Below 5% it is too thin, above 15% too rich: but a rich cloud that mixes with air burns as it goes. Burnable gases add by the rule of Le Chatelier (the sum of each over its lower limit must reach one).$(li)hydrogen 4 to 75%$(li)carbon monoxide 12.5 to 74%$(li)hydrogen sulfide 4.3 to 46%$(li)ammonia 15 to 28%",
                    "Метан горит только при 5-15% в воздухе и совсем не горит ниже 12% кислорода. Ниже 5% он слишком беден, выше 15% слишком богат, но богатое облако, смешиваясь с воздухом, горит на ходу. Горючие газы складываются по правилу Ле Шателье (сумма долей каждого от своего нижнего предела должна дойти до единицы).$(li)водород 4-75%$(li)угарный газ 12,5-74%$(li)сероводород 4,3-46%$(li)аммиак 15-28%"))
            .titled(
                L.of("The blast", "Взрыв"),
                L.of("The flame burns through every connected cell of burnable gas, and its heat raises the pressure by 0.25 kPa for each kilojoule in a cubic metre. The blast stays strong far along a narrow gallery and is worst in a sealed room.$(br2)35 kPa injures, 400 kPa kills. Glass breaks at about 9 kPa, wood at 40, stone at 77. From 100 kPa a roof without shoring falls in. After it comes $(thing)afterdamp$(): nitrogen, carbon dioxide and carbon monoxide, which has killed more miners than the blast.",
                    "Пламя прожигает все связанные клетки горючего газа, и его тепло поднимает давление на 0,25 кПа на каждый килоджоуль в кубометре. Взрыв сильным остаётся далеко по узкому штреку и хуже всего в запертой комнате.$(br2)35 кПа ранят, 400 кПа убивают. Стекло ломается около 9 кПа, дерево около 40, камень около 77. От 100 кПа обрушивается кровля без крепи. Следом идут $(thing)послевзрывные газы$(): азот, углекислый и угарный газ, убивший больше шахтёров, чем сам взрыв."))
            .anchor("blast")
            .titled(
                L.of("Coal dust", "Угольная пыль"),
                L.of("Coal dust burns too: from 50 grams in a cubic metre to 2 kilograms, and not below 12% oxygen. Breaking a block of coal puts 5 grams in the air and lays 50 on the floor, a quern grinding it 2 grams. Half the cloud settles in about 70 seconds. Settled dust is not moved by the air.$(br2)A little firedamp makes a thin cloud of dust explode, and a little dust does the same for lean firedamp. A blast lifts the dust from the floor when more than 35% of it is coal: so a small fire grows into a disaster.",
                    "Угольная пыль тоже горит: от 50 граммов на кубометр до 2 килограммов, и не ниже 12% кислорода. Ломка блока угля поднимает в воздух 5 граммов и кладёт на пол 50, помол на жёрнове даёт 2 грамма. Половина облака оседает секунд за 70. Осевшую пыль воздух не двигает.$(br2)Немного рудничного газа взорвёт жидкое облако пыли, а немного пыли то же сделает с бедным рудничным газом. Взрыв поднимает пыль с пола, если угля в ней больше 35%: так маленький огонь вырастает в катастрофу."))
            .anchor("dust")
            .titled(
                L.of("Stone dusting", "Осланцевание"),
                L.of("Since the 1920s mines have been made safe from dust by spreading ground limestone through the galleries, until at least 65% of the dust on the floor cannot burn. Use $(l:metalworking/flux)flux powder$() on the face of a wall: it spreads over the open cells within two blocks, and a message gives the share of dust that cannot burn, against the 65% needed.$(br2)A powder is 0.75 kg, so it goes far in a short gallery and thin in a long one. Do it again after mining: coal dust settles on top of the stone.",
                    "С 1920-х шахты защищают от пыли, рассыпая по выработкам молотый известняк, пока не менее 65% пыли на полу не станет негорючим. Используйте $(l:metalworking/flux)порошок флюса$() на грань стены: он ляжет на открытые клетки в двух блоках вокруг, и сообщение скажет долю негорючей пыли против нужных 65%.$(br2)Порошок весит 0,75 кг, так что в коротком штреке его хватит надолго, а в длинном получится тонко. Повторяйте после добычи: угольная пыль оседает поверх камня."))
            .crafting("tfcpp:crafting/davy_lamp",
                L.of("A $(thing)Davy Lamp$() is a copper gauze on an unfinished copper lamp. The gauze is four copper rods. The lamp gives light 9.",
                    "$(thing)Лампа Дэви$() это медная сетка на недоделанной медной лампе. Сетка делается из четырёх медных прутьев. Лампа даёт свет 9."))
            .showsOnLastPage("tfcpp:davy_lamp", "tfcpp:wire_gauze")
            .titled(
                L.of("Reading the lamp", "Как читать лампу"),
                L.of("A copper gauze draws the heat out of a flame faster than the flame can light the gas outside. Carry the lit lamp, filled with oil, in either hand. Firedamp burns on the flame as a blue cap:$(li)from 1%: a pale cap$(li)2%: blue, 3%: tall$(li)5%: the flame fills the gauze: get out$(li)above 15%: the flame is smothered$(br)The flame also burns low below 18% oxygen and dies at 16%. It does not show carbon monoxide.",
                    "Медная сетка отводит тепло от пламени быстрее, чем оно может зажечь газ снаружи. Носите зажжённую лампу с маслом в любой руке. Рудничный газ горит на пламени голубым колпачком:$(li)с 1%: бледный колпачок$(li)2%: голубой, 3%: высокий$(li)5%: пламя заполняет сетку: уходите$(li)выше 15%: пламя задушено$(br)Пламя горит тускло ниже 18% кислорода и гаснет на 16%. Угарный газ лампа не показывает."))
            .anchor("lamp")
            .titled(
                L.of("A lamp that went out", "Лампа погасла"),
                L.of("A flame that the air put out stays out. It does not light itself again: light it with a flint and steel, a fire starter or flint and pyrite from the other hand, in air where a flame can burn. A lamp standing on the ground is lit the same way.$(br2)If the flame fills the gauze for two minutes, the gauze glows red and lights the gas outside. A lamp is not safe forever: leave when the flame fills it.",
                    "Пламя, которое погасил воздух, остаётся погасшим. Само оно не зажжётся: зажгите кремнем и сталью, огнивом или кремнем с пиритом из другой руки, в воздухе, где пламя горит. Лампа, стоящая на земле, зажигается так же.$(br2)Если пламя заполняет сетку две минуты, сетка раскаляется докрасна и поджигает газ снаружи. Лампа не безопасна вечно: уходите, когда пламя заполнило её."))
            .titled(
                L.of("Going into a coal mine", "Вход в угольную шахту"),
                L.of("$(li)Work with bronze or copper tools. Light no torch.$(li)Carry a lit Davy lamp and a $(l:tfcpp/poisons#carbon_monoxide)caged animal$().$(li)Drive a $(l:tfcpp/gases#fan)fan$() on a second shaft, so that there is air to draw.$(li)Dust the galleries with stone.$(li)Mine a little and wait: a face bleeds for an hour.$(li)Never go back into a sealed working without testing the air.",
                    "$(li)Работайте бронзовыми или медными орудиями. Не зажигайте факелов.$(li)Берите зажжённую лампу Дэви и $(l:tfcpp/poisons#carbon_monoxide)клетку с животным$().$(li)Поставьте $(l:tfcpp/gases#fan)вентилятор$() на второй ствол, чтобы воздуху было откуда идти.$(li)Осланцуйте выработки.$(li)Добывайте понемногу и ждите: забой выделяет газ час.$(li)Не входите в закрытую выработку, не проверив воздух."));
    }

    private GasesGuide() {}
}
