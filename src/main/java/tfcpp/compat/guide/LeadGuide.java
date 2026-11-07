package tfcpp.compat.guide;

import java.util.List;

/**
 * The chapter of the TFC++ category about lead and silver: galena, work lead, cupellation, the reduction of litharge, lead sheet, and how the fumes of a hearth or a furnace are drawn into a line that ends in a tower.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code OreData} (where galena lies), {@code MetallurgyData} (roasting, casting, sheet), {@code CupellationBath} and {@code CupelHearthBlockEntity} (hearth), the retort recipe {@code tfcpp:retort/lead}, {@code Physiology.BodyBurden.LEAD} and {@code Breathing} (poisoning), {@code PistonPumpBlockEntity.fromAir} and {@code PipeNetwork.drawFromCell} (intake of the pump), {@code TowerCondensation} (tower) and {@code PipeMaterial.LEAD}. The yield of a full charge is worked out from them: 1000 mB is 54.7 mol of lead, 3% of it fume (1.64 mol of PbO, 366 g), the rest 970 mB of litharge, which at 4.57 mB a powder is 212 powders.
 */
final class LeadGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(leadAndSilver());
    }

    private static Entry leadAndSilver()
    {
        return Entry.of("tfcpp/lead_silver", CATEGORY, "tfcpp:cupel_hearth", 8, L.of("Lead and Silver", "Свинец и серебро"))
            .titled(
                L.of("Galena", "Галенит"),
                L.of("$(thing)Galena$() is lead sulfide, the lead ore of TFC++. It lies with $(l:the_world/ores_and_minerals#sphalerite)sphalerite$() and barite in limestone, dolomite and marble between y=-30 and 70, and in veins through quartzite, slate, phyllite, schist and gneiss between y=-70 and 40. Prospect it as any ore.$(br2)Lead and silver go together: galena carries 0.3% of silver by the mass of its lead. It was for this silver that Athens mined lead at Laurion.",
                    "$(thing)Галенит$() это сульфид свинца, свинцовая руда TFC++. Он лежит вместе со $(l:the_world/ores_and_minerals#sphalerite)сфалеритом$() и баритом в известняке, доломите и мраморе между y=-30 и 70 и в жилах сквозь кварцит, аспидный сланец, филлит, кристаллический сланец и гнейс между y=-70 и 40. Ищите его, как любую руду.$(br2)Свинец и серебро ходят вместе: в галените 0,3% серебра по массе его свинца. Ради этого серебра Афины добывали свинец в Лаврионе."))
            .anchor("galena")
            .heating("tfcpp:heating/ore/normal_galena",
                L.of("Galena roasts at $(bold)800 °C$() into $(thing)work lead$(), lead with its silver still in it. A piece gives metal by its size: small 10 mB, poor 15, normal 25, rich 35.",
                    "Галенит обжигается при $(bold)800 °C$() в $(thing)веркблей$(), свинец, в котором ещё сидит серебро. Кусок даёт металла по своему размеру: мелкий 10 мБ, бедный 15, обычный 25, богатый 35."))
            .showsOnLastPage("tfcpp:ore/normal_galena", "tfcpp:ore/small_galena", "tfcpp:ore/poor_galena", "tfcpp:ore/rich_galena")
            .anchor("roast")
            .titled(
                L.of("What the roast gives off", "Что даёт обжиг"),
                L.of("The sulfur of galena burns to sulfur dioxide, 1.4 mol from a normal piece, and 3% of its lead goes up as a smoke of lead oxide. Both are poison.$(br2)Roast in the open air or under a flue, and see $(l:tfcpp/sulfides#roasting)Sulfide Ores$() for what sulfur dioxide does in a closed room. The smoke is the slow one, and it is next.",
                    "Сера галенита сгорает в сернистый газ, 1,4 моля из обычного куска, а 3% его свинца уходят дымом оксида свинца. Оба ядовиты.$(br2)Обжигайте на открытом воздухе или под дымоходом, а что сернистый газ делает в закрытой комнате, смотрите в главе $(l:tfcpp/sulfides#roasting)Сульфидные руды$(). Дым свинца действует медленно, о нём дальше."))
            .titled(
                L.of("The smoke that kills slowly", "Дым, убивающий медленно"),
                L.of("Lead oxide fume does not choke. It collects in the body, with a half-life of 30 days of process time, 12 real hours. Symptoms begin at 30 mg, incapacity at 100 mg, death at 300 mg. Air with 100 mg of fume in each cubic metre adds 1.6 mg a minute to whoever breathes it.$(br2)A full charge in the cupellation hearth makes 370 g of it. See $(l:tfcpp/poisons#chronic)Poisons$().",
                    "Дым оксида свинца не душит. Он копится в теле с периодом полувыведения 30 суток процесса, то есть 12 реальных часов. Симптомы с 30 мг, потеря трудоспособности со 100 мг, смерть с 300 мг. Воздух со 100 мг дыма в каждом кубометре прибавляет дышащему 1,6 мг в минуту.$(br2)Полная загрузка в купеляционном поду даёт его 370 г. См. $(l:tfcpp/poisons#chronic)Яды$()."))
            .anchor("smoke")
            .titled(
                L.of("Cupellation", "Купеляция"),
                L.of("Silver does not burn and lead does. Hot air over molten lead turns it to $(thing)litharge$() (PbO), which melts at 888 °C and is soaked up by a porous floor of bone ash. The silver stays behind, and when the last film of litharge leaves it, the bright metal flashes: the $(bold)blick$().$(br2)Assayers and mints worked so for thousands of years.",
                    "Серебро не горит, а свинец горит. Горячий воздух над расплавленным свинцом превращает его в $(thing)глёт$() (PbO), плавящийся при 888 °C и впитываемый пористой подиной из костяной золы. Серебро остаётся, и когда с него уходит последняя плёнка глёта, яркий металл вспыхивает: это $(bold)блик$().$(br2)Так работали пробирщики и монетные дворы тысячи лет."))
            .anchor("cupel")
            .crafting("tfcpp:crafting/cupel_hearth",
                L.of("The $(thing)Cupellation Hearth$() is five fire bricks. It takes the heat of the fire below it, as the coke oven does.",
                    "$(thing)Купеляционный под$() это пять огнеупорных кирпичей. Он берёт жар огня под собой, как коксовая печь."))
            .showsOnLastPage("tfcpp:cupel_hearth")
            .heating("tfcpp:heating/bone_ash",
                L.of("Bone meal heated to 700 °C is $(thing)Bone Ash$(), the floor of the hearth. A powder soaks up the litharge of 500 mB of lead. With no ash the hearth does nothing.",
                    "Костная мука, прокалённая при 700 °C, это $(thing)костяная зола$(), подина пода. Порошок впитывает глёт от 500 мБ свинца. Без золы под ничего не делает."))
            .showsOnLastPage("tfcpp:powder/bone_ash")
            .titled(
                L.of("Working the hearth", "Работа пода"),
                L.of("$(li)Lay the floor: right-click with bone ash, a powder for each 500 mB of lead.$(li)Light a fire under it: the hearth must reach 960 °C, to keep litharge liquid.$(li)Pour in work lead from a vessel or a mold, up to 1000 mB.$(li)It burns 1 mB of lead a second while the air above it holds over 12% oxygen. A full charge takes 17 minutes.$(li)Litharge drops as powder above the hearth. An empty hand reads its state.",
                    "$(li)Выложите подину: правый клик костяной золой, порошок на каждые 500 мБ свинца.$(li)Разожгите огонь под ним: под должен достичь 960 °C, чтобы глёт оставался жидким.$(li)Влейте веркблей из сосуда или формы, до 1000 мБ.$(li)Он выжигает 1 мБ свинца в секунду, пока в воздухе над ним больше 12% кислорода. Полная загрузка идёт 17 минут.$(li)Глёт падает порошком над подом. Пустая рука показывает его состояние."))
            .titled(
                L.of("The silver", "Серебро"),
                L.of("When the last lead has gone the silver flashes, with a chime and a message. Right-click with an empty mold to pour it out. Only whole millibuckets come out, and the fraction stays for the next charge.$(br2)A full charge of 1000 mB gives 212 powders of litharge, 3.24 mB of silver and 370 g of lead fume. An ingot of silver (100 mB) takes 31 charges. Plain lead is refused: its silver is gone already.",
                    "Когда уходит последний свинец, серебро вспыхивает со звоном и сообщением. Правый клик пустой формой выльет его. Выходят только целые миллибакеты, а дробь остаётся до следующей загрузки.$(br2)Полная загрузка в 1000 мБ даёт 212 порошков глёта, 3,24 мБ серебра и 370 г свинцового дыма. На слиток серебра (100 мБ) нужно 31 загрузка. Чистый свинец под не берёт: серебра в нём уже нет."))
            .titled(
                L.of("How fumes reach a line", "Как дым попадает в линию"),
                L.of("The fume rises into the room, and only moving air takes it into a pipe. A $(l:tfcpp/pipes#pump)piston pump$() draws in the air of the one cell behind its back and delivers it into the pipe in front. It takes whatever that air holds: smoke, the fumes of arsenic, antimony, zinc, lead and mercury, sulfur dioxide. Coal dust it leaves.$(br2)So set the pump with its back to the cell where the fume comes out, the air over the hearth or the furnace, or beside it. A shaft must turn it.",
                    "Дым поднимается в комнату, и в трубу его уводит только движущийся воздух. $(l:tfcpp/pipes#pump)Поршневой насос$() втягивает воздух одной клетки позади своей спины и подаёт его в трубу перед собой. Он берёт всё, что в этом воздухе есть: дым, пары мышьяка, сурьмы, цинка, свинца и ртути, сернистый газ. Угольную пыль он оставляет.$(br2)Поэтому ставьте насос спиной к клетке, откуда выходит дым, к воздуху над подом или печью либо рядом с ними. Его должен вращать вал."))
            .anchor("intake")
            .titled(
                L.of("The line and the tower", "Линия и башня"),
                L.of("A turn of the shaft sweeps 20 litres, a fiftieth of a cell. The pump works slowly, so keep it where the fume is thickest, and fit more than one for a big fire.$(br2)The gas enters the line at room temperature, whatever the heat of the fire. A $(l:tfcpp/sulfides#catching)condensation tower$() in the line takes 5% of the oxide fume a second below 250 °C, and mercury below 100 °C. Sulfur dioxide passes through: end the line in a vent in the open air, away from where anyone works, or the pressure builds and the pump stalls.",
                    "Оборот вала захватывает 20 литров, пятидесятую часть клетки. Насос работает медленно, поэтому держите его там, где дым гуще, и ставьте больше одного для большого огня.$(br2)Газ входит в линию с комнатной температурой, каким бы жарким ни был огонь. $(l:tfcpp/sulfides#catching)Конденсационная башня$() в линии забирает 5% оксидного дыма в секунду ниже 250 °C и ртуть ниже 100 °C. Сернистый газ проходит насквозь: выведите линию выпуском на открытый воздух, подальше от тех, кто работает, иначе давление растёт и насос встаёт."))
            .titled(
                L.of("Litharge back to lead", "Глёт обратно в свинец"),
                L.of("Litharge goes back to metal in the $(l:tfcpp/sulfides#retort)retort$() at 800 °C with carbon: PbO and C give Pb and carbon dioxide. A powder with 0.125 mol of carbon gives 4.57 mB of lead. A charcoal holds 200 mol, enough for 1600 powders.$(br2)Lead does not boil, so no condenser is needed, and the carbon dioxide goes into the room. Of 1000 mB of work lead you get back about 970 mB of clean lead: 3% went up as smoke.",
                    "Глёт возвращается в металл в $(l:tfcpp/sulfides#retort)реторте$() при 800 °C с углеродом: PbO и C дают Pb и углекислый газ. Порошок с 0,125 моля углерода даёт 4,57 мБ свинца. В куске древесного угля 200 молей, хватит на 1600 порошков.$(br2)Свинец не кипит, поэтому конденсатор не нужен, а углекислый газ уходит в комнату. Из 1000 мБ веркблея выходит около 970 мБ чистого свинца: 3% ушло дымом."))
            .anchor("reduction")
            .anvil("tfcpp:anvil/lead_sheet",
                L.of("Lead is beaten into $(thing)sheet$() cold: it recrystallises at room temperature, so it needs no forge. Work lead makes a sheet of work lead the same way, but that sheet melts back into work lead, and no pipe is made of it.",
                    "Свинец бьют в $(thing)лист$() холодным: он перекристаллизуется при комнатной температуре и в горне не нуждается. Веркблей даёт таким же образом лист веркблея, но тот плавится обратно в веркблей, и труб из него не делают."))
            .showsOnLastPage("tfcpp:metal/sheet/lead")
            .anchor("sheet")
            .titled(
                L.of("What lead is for", "Для чего нужен свинец"),
                L.of("Lead sheet makes pipes, the $(l:tfcpp/acids#chamber)lead chamber$(), the condensation tower and the lead fittings. Lead is the metal that holds sulfuric acid, under a film of its own sulfate.$(br2)It is soft. Its pipe is rated at 5.9 atm and gives at 150 °C, and the metal melts at 327 °C (see $(l:tfcpp/pipes#walls)Pipes and Pressure$()). A sheet melts back into 100 mB of lead.",
                    "Из свинцового листа делают трубы, $(l:tfcpp/acids#chamber)свинцовую камеру$(), конденсационную башню и свинцовые фитинги. Свинец это металл, который держит серную кислоту под плёнкой её же сульфата.$(br2)Он мягок. Его труба рассчитана на 5,9 атм и сдаётся при 150 °C, а сам металл плавится при 327 °C (см. $(l:tfcpp/pipes#walls)Трубы и давление$()). Лист переплавляется обратно в 100 мБ свинца."));
    }

    private LeadGuide() {}
}
