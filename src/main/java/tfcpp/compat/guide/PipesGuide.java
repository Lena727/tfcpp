package tfcpp.compat.guide;

import java.util.List;

/**
 * The chapter of the TFC++ category about pipes and pressure.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code PipeMaterial} (strength, wall, temperature limit, corrosion), {@code PipePhysics} (Barlow, pV = nRT, Antoine, flash energy), {@code PipeFittingBlock}, {@code PipeNetwork}, {@code PipeTracker} and {@code ShaftPhysics}. Ratings in atmospheres are computed from them by Barlow's formula with the wall and the strength of the material and the limit of its joints.
 */
final class PipesGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(pipes());
    }

    private static Entry pipes()
    {
        return Entry.of("tfcpp/pipes", CATEGORY, "tfcpp:pipe/wrought_iron", 6, L.of("Pipes and Pressure", "Трубы и давление"))
            .titled(
                L.of("A run of pipe", "Линия труб"),
                L.of("Connected pipes and fittings make one $(thing)network$(): a single volume at a single pressure. A pipe has a bore of 100 mm, about 8 litres to the metre. Gas obeys $(bold)pV = nRT$(), so its pressure follows its absolute temperature. Liquid weighs: a column of water presses on its foot with 1 atm for each 10 m. How fast anything flows follows Darcy and Weisbach: a long rough pipe passes less.$(br2)A network bursts at its weakest wall or joint, and breaking a pipe lets out everything in the run. Close a valve first.",
                    "Соединённые трубы и фитинги это одна $(thing)сеть$(): единый объём с единым давлением. Проход трубы 100 мм, около 8 литров на метр. Газ подчиняется $(bold)pV = nRT$(), поэтому давление следует за абсолютной температурой. Жидкость весома: столб воды давит на основание 1 атм на каждые 10 м. Скорость потока определяют Дарси и Вейсбах: длинная шероховатая труба пропускает меньше.$(br2)Сеть рвётся в самом слабом месте стенки или стыка, а сломав трубу, вы выпускаете всё содержимое линии. Закройте задвижку заранее."))
            .anchor("network")
            .crafting("tfcpp:crafting/pipe/wrought_iron", "tfcpp:crafting/pipe/lead",
                L.of("Three lumber or three fire bricks make three pipes of wood or fired clay. Two sheets of copper, lead, wrought iron or steel make two pipes of that metal.",
                    "Три бруса или три огнеупорных кирпича дают три трубы из дерева или обожжённой глины. Два листа меди, свинца, кованого железа или стали дают две трубы из этого металла."))
            .showsOnLastPage("tfcpp:pipe/wrought_iron", "tfcpp:pipe/lead", "tfcpp:pipe/steel", "tfcpp:pipe/copper", "tfcpp:pipe/ceramic", "tfcpp:pipe/wood")
            .titled(
                L.of("What a wall holds", "Что держит стенка"),
                L.of("Barlow's formula gives the bursting pressure of a thin wall: p = 2 S t / D.$(li)wood 8.9 atm, to 177 °C$(li)fired clay 3.5 atm, to 1127 °C$(li)copper 10 atm, to 427 °C$(li)lead 5.9 atm, to 150 °C$(li)wrought iron 10 atm, to 627 °C$(li)steel 10 atm, to 727 °C$(br)Joints of hemp and tallow hold 10 atm at most, whatever the metal: steel gains nothing over iron until rubber gaskets come. Hotter than its limit a pipe holds nothing.",
                    "Формула Барлоу даёт давление разрыва тонкой стенки: p = 2 S t / D.$(li)дерево 8,9 атм, до 177 °C$(li)обожжённая глина 3,5 атм, до 1127 °C$(li)медь 10 атм, до 427 °C$(li)свинец 5,9 атм, до 150 °C$(li)кованое железо 10 атм, до 627 °C$(li)сталь 10 атм, до 727 °C$(br)Стыки на пеньке с салом держат не больше 10 атм, каким бы ни был металл: сталь ничего не выигрывает у железа, пока нет резиновых прокладок. Горячее предела труба не держит ничего."))
            .anchor("walls")
            .titled(
                L.of("Heat, steam and water", "Жар, пар и вода"),
                L.of("Heat a closed pipe and its gas gains pressure with the absolute temperature. Water adds the pressure of its vapour (the Antoine equation): 1 atm at 100 °C, 4.7 at 150 °C and 10 at 180 °C, which is the limit of the joints.$(br2)Worst is a pipe filled to the brim with liquid. Water expands 0.021% a kelvin and is hardly compressible, so each kelvin of warming adds 4.6 atm: two kelvin burst an iron pipe at once. Leave room for the liquid to expand, or fit a safety valve.",
                    "Нагрейте закрытую трубу, и давление её газа вырастет вместе с абсолютной температурой. Вода добавляет давление своего пара (уравнение Антуана): 1 атм при 100 °C, 4,7 при 150 °C и 10 при 180 °C, это предел стыков.$(br2)Хуже всего труба, налитая жидкостью до краёв. Вода расширяется на 0,021% на кельвин и почти не сжимается, поэтому каждый кельвин нагрева даёт 4,6 атм: два кельвина разорвут железную трубу сразу. Оставьте жидкости место для расширения или поставьте предохранительный клапан."))
            .anchor("heat")
            .crafting("tfcpp:crafting/pressure_vessel",
                L.of("The $(thing)Pressure Vessel$() is eight wrought iron double sheets round a hollow: half a cubic metre that joins every pipe touching it.",
                    "$(thing)Сосуд высокого давления$() это восемь двойных листов кованого железа вокруг пустоты: полкубометра, соединяющийся со всеми касающимися труб."))
            .showsOnLastPage("tfcpp:pressure_vessel")
            .titled(
                L.of("Boilers", "Котлы"),
                L.of("A fire below a pressure vessel heats the whole network joined to it, and a vessel with water and a closed outlet is a boiler. Its pressure climbs with the temperature until something gives way. Nothing makes it safe but a $(thing)safety valve$() and a hand to watch the gauge.$(br2)Its walls count as a pipe's do: Barlow, the joints, the temperature limit and the corrosion of what it holds. See $(l:tfcpp/pipes#walls)the table$().",
                    "Огонь под сосудом высокого давления нагревает всю соединённую с ним сеть, а сосуд с водой и закрытым выходом это котёл. Его давление растёт с температурой, пока что-нибудь не сдастся. Безопасным его делают только $(thing)предохранительный клапан$() и рука, следящая за манометром.$(br2)Его стенки считаются как у трубы: Барлоу, стыки, предел температуры и коррозия от содержимого. См. $(l:tfcpp/pipes#walls)таблицу$()."))
            .crafting("tfcpp:crafting/safety_valve", "tfcpp:crafting/safety_valve_lead",
                L.of("The $(thing)Safety Valve$() is three ingots under a brass mechanism: wrought iron, or lead. A fired clay one is made of fire bricks.",
                    "$(thing)Предохранительный клапан$() это три слитка под латунным механизмом: кованого железа или свинца. Глиняный делают из огнеупорных кирпичей."))
            .showsOnLastPage("tfcpp:safety_valve", "tfcpp:safety_valve_lead", "tfcpp:safety_valve_ceramic")
            .titled(
                L.of("The safety valve", "Предохранительный клапан"),
                L.of("A valve has to lift before its own body gives way, so its weight is set to four fifths of the rating of the body, up to 8 atm. An iron one lifts at 8 atm above the air outside and seats again at 7, with a loud hiss; one of lead lifts at 4.7 and seats at 3.7; one of fired clay at 2.8, seat 1.8. It cannot be held shut.$(br2)Drowned in liquid, it still lifts, and the gas bubbles out through the liquid. The hiss tells you that the pressure is high.",
                    "Клапан должен подняться раньше, чем сдастся корпус, поэтому груз выставлен на четыре пятых допустимого давления корпуса, но не выше 8 атм. Железный поднимается при 8 атм сверх воздуха снаружи и садится снова при 7, с громким шипением; свинцовый поднимается при 4,7 и садится при 3,7; из обожжённой глины при 2,8, седло 1,8. Его нельзя удержать закрытым.$(br2)Утопленный в жидкости, он всё равно поднимается газом, и тот уходит пузырями сквозь жидкость. Шипение говорит, что давление высоко."))
            .anchor("safety")
            .titled(
                L.of("Fittings", "Фитинги"),
                L.of("$(li)$(thing)Gate valve$(): a hand wheel. Closed, it splits the network in two. It cannot be turned again for a second.$(li)$(thing)Vent$(): an open pipe end that passes gas either way, by the pressure on each side. Into liquid it lets gas only out.$(li)$(thing)Tap$(): lets liquid out into a barrel or any holder in front of it.$(br)Each comes in iron, lead and fired clay, and takes the strength and the corrosion of its body. Two liquids never join: pipes that carry them touch without opening to each other.",
                    "$(li)$(thing)Задвижка$(): маховик. Закрытая, она делит сеть надвое. Её нельзя повернуть снова в течение секунды.$(li)$(thing)Выпуск$(): открытый конец трубы, который пропускает газ в обе стороны, по давлению с каждой стороны. В жидкость он выпускает только наружу.$(li)$(thing)Кран$(): выпускает жидкость в бочку или другую ёмкость перед собой.$(br)Каждый бывает из железа, свинца и обожжённой глины и берёт прочность и коррозию своего корпуса. Две жидкости никогда не соединяются: трубы с ними касаются, не открываясь друг в друга."))
            .anchor("fittings")
            .crafting("tfcpp:crafting/piston_pump",
                L.of("The $(thing)Piston Pump$() is three wrought iron double sheets under a brass mechanism. A shaft drives it from the side.",
                    "$(thing)Поршневой насос$() это три двойных листа кованого железа под латунным механизмом. Вал приводит его в движение сбоку."))
            .showsOnLastPage("tfcpp:piston_pump")
            .titled(
                L.of("The pump", "Насос"),
                L.of("The pump takes in from its back (a pipe, a barrel, standing water or the air) and delivers into a pipe at its front. Each turn sweeps 0.1 cubic metre, and the shaft pays for the pressure difference and the lift of the liquid in the front pipe.$(br2)The air pushes water up a suction pipe 10.3 m at most, acid of density 1800 only 5.7 m. A pump takes no lava or molten metal, and delivers into no line that runs on into unloaded land. Right-click it to see why it stands. See $(l:mechanics/pumps)Pumps$().",
                    "Насос берёт со своей спины (труба, бочка, стоячая вода или воздух) и подаёт в трубу перед собой. Каждый оборот захватывает 0,1 кубометра, а вал платит за разность давлений и за подъём жидкости до высоты её поверхности в передней трубе.$(br2)Воздух поднимает воду по всасывающей трубе не выше 10,3 м, кислоту плотностью 1800 лишь на 5,7 м. Насос не берёт лаву и расплав металла и не подаёт в линию, уходящую в незагруженную землю. Правый клик покажет, почему он стоит. См. $(l:mechanics/pumps)Насосы$()."))
            .anchor("pump")
            .titled(
                L.of("When it bursts", "Когда она рвётся"),
                L.of("A burst frees the energy of the gas as it expands to the air, and of the hot water that flashes to steam. A vessel of 500 litres of water at 180 °C gives about 28 MJ. The shock works as a gas blast does (see $(l:tfcpp/mine_safety#blast)Mine Safety$()), and steam scalds everyone within four blocks.$(br2)Liquid of a thousand litres or more spills as blocks, and any corrosive liquid splashes the people near. A pipe whose wall has lost more than a twentieth gives nothing back when you pick it up.",
                    "Разрыв освобождает энергию газа, расширяющегося до воздуха, и горячей воды, вскипающей в пар. Сосуд с 500 литрами воды при 180 °C даёт около 28 МДж. Удар действует как взрыв газа (см. $(l:tfcpp/mine_safety#blast)Шахтную безопасность$()), а пар обваривает всех в четырёх блоках.$(br2)Тысяча литров жидкости и больше разливается блоками, а любая едкая жидкость обрызгивает людей рядом. Труба, у которой стенка потеряла больше двадцатой доли, при снятии ничего не возвращает."))
            .crafting("tfcpp:crafting/mercury_manometer",
                L.of("The $(thing)Mercury Manometer$() is glass panes round a bulb of mercury, from $(l:tfcpp/sulfides#mercury)cinnabar$().",
                    "$(thing)Ртутный манометр$() это стеклянные панели вокруг колбы с ртутью из $(l:tfcpp/sulfides#mercury)киновари$()."))
            .showsOnLastPage("tfcpp:mercury_manometer")
            .titled(
                L.of("Reading the gauge", "Как читать манометр"),
                L.of("Right-click it to read the pressure in millimetres of mercury: 760 is the air outside. It also says when the line runs on past loaded land.$(br2)Above 3 atm over the air outside the mercury is blown out of the tube into the room, once (and mercury is poison: see $(l:tfcpp/poisons#mercury)Poisons$()). The tube then leaks a hundredth of the bore, and the pipes keep their pressure otherwise.",
                    "Правый клик покажет давление в миллиметрах ртутного столба: 760 это воздух снаружи. Он говорит и о том, что линия уходит за пределы загруженной земли.$(br2)Выше 3 атм над воздухом снаружи ртуть выбрасывает из трубки в комнату, один раз (а ртуть это яд: см. $(l:tfcpp/poisons#mercury)Яды$()). Затем трубка пропускает сотую долю прохода, а в остальном трубы сохраняют давление."))
            .titled(
                L.of("Corrosion by liquids", "Коррозия от жидкостей"),
                L.of("Corrosion eats a share of the wall each process day (24 minutes) in a full pipe. Iron or steel, copper, lead, wood:$(li)chamber acid: 0.5, 0.2, 0.001, 0.5$(li)sulfuric acid: 0.01, 0.1, 0.1, 1.0$(li)nitric acid: 0.02, 2.0, 0.5, 1.0$(li)hydrochloric acid: 2.0, 0.1, 0.1, 0.02$(br)Fired clay takes none of these. Strong acid passivates iron, but dilute eats it, and chloride never lets it. Lead holds sulfuric acid under a film of sulfate until 80 °C, then loses 20 times faster.",
                    "Коррозия съедает долю стенки за сутки процесса (24 минуты) в полной трубе. Железо или сталь, медь, свинец, дерево:$(li)камерная кислота: 0,5, 0,2, 0,001, 0,5$(li)серная кислота: 0,01, 0,1, 0,1, 1,0$(li)азотная кислота: 0,02, 2,0, 0,5, 1,0$(li)соляная кислота: 2,0, 0,1, 0,1, 0,02$(br)Обожжённая глина не берёт ничего из этого. Крепкая кислота пассивирует железо, слабая ест, хлорид не даёт пассивации. Свинец держит серную кислоту под плёнкой сульфата до 80 °C, выше в 20 раз хуже."))
            .anchor("corrosion")
            .titled(
                L.of("Other corrosion", "Прочая коррозия"),
                L.of("Per process day, in a full pipe:$(li)mercury in copper: 1.0 (iron flasks carried it)$(li)vinegar: lead 0.1, iron and copper 0.05$(li)brine: iron 0.03, copper 0.005$(li)water: iron 0.002$(li)hot lye above 80 °C: fired clay 0.05$(br)In pure gas: sulfur dioxide takes 0.1 from iron and nothing from lead, whose film it feeds. Ammonia takes 0.5 from copper, chlorine 0.3.$(br2)A worn wall bursts: look at your pipes.",
                    "За сутки процесса, в полной трубе:$(li)ртуть в меди: 1,0 (её носили в железных флягах)$(li)уксус: свинец 0,1, железо и медь 0,05$(li)рассол: железо 0,03, медь 0,005$(li)вода: железо 0,002$(li)горячий щёлок выше 80 °C: обожжённая глина 0,05$(br)В чистом газе: сернистый газ берёт 0,1 у железа и ничего у свинца, чью плёнку питает. Аммиак берёт 0,5 у меди, хлор 0,3.$(br2)Истончённая стенка рвётся: смотрите на свои трубы."))
            .titled(
                L.of("Loaded land", "Загруженная земля"),
                L.of("A network is worked out while its chunks are loaded. A run that ends in unloaded land is marked as running on: the pump will not deliver into it, and the gauge says so. A liquid that has overflowed is kept until it makes a whole block, and does not vanish.$(br2)Corrosion is counted for each section of pipe from when it was last looked at, so a section left in unloaded land for days pays for them all when it returns.",
                    "Сеть считается, пока её чанки загружены. Линия, оканчивающаяся в незагруженной земле, помечается как уходящая дальше: насос в неё не подаёт, и манометр это скажет. Перелившаяся жидкость хранится, пока не наберётся целый блок, и не исчезает.$(br2)Коррозия считается для каждого участка трубы с того времени, когда на него смотрели в последний раз, поэтому участок, пролежавший в незагруженной земле дни, платит за все разом, когда возвращается."));
    }

    private PipesGuide() {}
}
