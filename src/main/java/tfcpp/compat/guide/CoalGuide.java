package tfcpp.compat.guide;

import java.util.List;

/**
 * The chapter of the TFC++ category about coal chemistry: the coke oven, coke, tar and liquor, the gas and the gas burner.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code CarbonizationTable} (what each charge gives), {@code CokeOvenBlockEntity} and {@code CokeOvenBlock} (batch, tanks, condenser, gas), {@code FuelSulfur}, {@code ChemistryData} (fuel), {@code BurnerMath} and {@code GasBurnerBlock} (burner), {@code Flammability} (limits of burning) and {@code PipeMaterial} (limits of the pipe). The limits of burning of the oven gas, 5 to 33%, are worked out from those of its gases by Le Chatelier's rule.
 */
final class CoalGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(coalChemistry());
    }

    private static Entry coalChemistry()
    {
        return Entry.of("tfcpp/coal_chemistry", CATEGORY, "tfcpp:coke_oven", 7, L.of("Coal Chemistry", "Углехимия"))
            .titled(
                L.of("Coal without air", "Уголь без воздуха"),
                L.of("Coal heated in air burns to ash. Heated with the air shut out, it only gives up its $(thing)volatile matter$(): gas, tar and ammoniacal water. What is left is nearly pure carbon, $(thing)coke$(). This is $(bold)dry distillation$(), and the coke oven does it.$(br2)Darby ran his furnace on coke in 1709, because raw coal caked and carried sulfur into the iron. A century later the oven gas lit the streets, and its tar and liquor became the raw material of dyes, drugs and fertilizer.",
                    "Уголь, нагретый на воздухе, сгорает в золу. Нагретый без воздуха, он лишь отдаёт $(thing)летучие вещества$(): газ, смолу и аммиачную воду. Остаётся почти чистый углерод, $(thing)кокс$(). Это $(bold)сухая перегонка$(), и её ведёт коксовая печь.$(br2)Дарби в 1709 году стал плавить чугун на коксе: сырой уголь спекался и вносил в железо серу. Век спустя газ коксовых печей осветил улицы, а смола и вода стали сырьём для красителей, лекарств и удобрений."))
            .crafting("tfcpp:crafting/coke_oven", "tfcpp:crafting/retort_condenser",
                L.of("The $(thing)Coke Oven$() is eight fire bricks round a wrought iron sheet. Its $(thing)Condenser$() is the retort's own (see $(l:tfcpp/sulfides#retort)Sulfide Ores$()).",
                    "$(thing)Коксовая печь$() это восемь огнеупорных кирпичей вокруг листа кованого железа. $(thing)Конденсатор$() у неё тот же, что у реторты (см. $(l:tfcpp/sulfides#retort)Сульфидные руды$())."))
            .showsOnLastPage("tfcpp:coke_oven")
            .titled(
                L.of("Setting up the oven", "Как поставить печь"),
                L.of("$(li)Put heat under it: a forge, a firepit or a $(l:tfcpp/coal_chemistry#burner)gas burner$().$(li)Set a condenser against one of its four sides.$(li)Put a pipe above it, or its gas goes into the room.$(li)Right-click with coal, lignite or logs: up to 64 of one kind.$(li)An empty hand reads its state. A crouching empty hand takes what it made.",
                    "$(li)Подведите жар снизу: горн, костёр или $(l:tfcpp/coal_chemistry#burner)газовая горелка$().$(li)Приставьте конденсатор к одной из четырёх боковых сторон.$(li)Поставьте трубу сверху, иначе газ уйдёт в комнату.$(li)Правый клик углём, лигнитом или поленьями: до 64 штук одного вида.$(li)Пустая рука показывает состояние. Пустая рука при приседании забирает продукт."))
            .anchor("oven")
            .titled(
                L.of("A batch", "Партия"),
                L.of("The charge cooks as one batch. Once it has begun to change nothing can be added, and the product must be taken out before the next charge goes in.$(br2)The batch moves only while the oven is at its temperature or hotter: a cold charge keeps its place and loses nothing. It runs on process time, where an $(bold)hour$() is one real minute, and it stands still on an empty server, as the fuel of a fire does.",
                    "Загрузка варится одной партией. Когда она начала меняться, добавлять нельзя, а продукт надо забрать, прежде чем класть следующую загрузку.$(br2)Партия идёт, только пока печь не холоднее нужного, холодная загрузка стоит на месте и ничего не теряет. Время здесь процессное: $(bold)час$() это одна реальная минута. На пустом сервере оно стоит, как горит топливо костра."))
            .titled(
                L.of("Coal", "Каменный уголь"),
                L.of("Coal needs $(bold)950 °C$() and 18 hours, which is 18 minutes. Each piece (3 kg) gives:$(li)0.70 piece of $(thing)coke$()$(li)0.10 L of coal tar$(li)0.30 L of ammoniacal liquor$(li)41.5 mol of gas: hydrogen 21, methane 14, carbon monoxide 3, carbon dioxide 1, nitrogen 2, ammonia 0.3, hydrogen sulfide 0.2$(br)Nothing is made or lost: 3 kg become 2.1 kg of coke, 0.12 of tar, 0.30 of liquor and 0.46 of gas.",
                    "Углю нужно $(bold)950 °C$() и 18 часов, то есть 18 минут. Каждый кусок (3 кг) даёт:$(li)0,70 куска $(thing)кокса$()$(li)0,10 л каменноугольной смолы$(li)0,30 л аммиачной воды$(li)41,5 моля газа: водорода 21, метана 14, угарного газа 3, углекислого 1, азота 2, аммиака 0,3, сероводорода 0,2$(br)Ничто не берётся из ничего и не пропадает: 3 кг дают 2,1 кг кокса, 0,12 смолы, 0,30 воды и 0,46 газа."))
            .anchor("coal")
            .titled(
                L.of("Lignite and wood", "Лигнит и дрова"),
                L.of("$(thing)Lignite$() chars at 700 °C in 18 hours and does not cake. Each piece gives 0.4 piece of $(thing)lignite char$(), 0.24 L of tar, 0.4 L of liquor and 15 mol of gas, mostly carbon dioxide. The char is a fuel, but no charge for the blast furnace.$(br2)$(thing)Logs$() are done at 400 °C in 12 hours. Each 5 kg log gives 0.5 piece of charcoal (30% of its mass), 0.25 L of wood tar, 2 L of $(thing)pyroligneous liquor$() and 30 mol of gas, mostly carbon dioxide and monoxide.",
                    "$(thing)Лигнит$() обугливается при 700 °C за 18 часов и не спекается. Каждый кусок даёт 0,4 куска $(thing)буроугольного полукокса$(), 0,24 л смолы, 0,4 л воды и 15 молей газа, в основном углекислого. Полукокс это топливо, но не шихта для доменной печи.$(br2)$(thing)Поленья$() готовы при 400 °C за 12 часов. Из полена в 5 кг выходит 0,5 куска древесного угля (30% его массы), 0,25 л древесного дёгтя, 2 л $(thing)подсмольной воды$() и 30 молей газа, в основном углекислого и угарного."))
            .spotlight("tfcpp:coke", L.of("Coke", "Кокс"),
                L.of("Coke burns at $(bold)1250 °C$() for 2200 ticks and feeds the $(l:metalworking/blast_furnace)blast furnace$(): hard enough to bear the ore, open enough to pass the blast. Raw coal is refused there. It carries less sulfur too: 0.9 mol of sulfur dioxide a piece, against 1.4 for coal.",
                    "Кокс горит при $(bold)1250 °C$() 2200 тактов и кормит $(l:metalworking/blast_furnace)доменную печь$(): он твёрд, чтобы держать руду, и пористый, чтобы пропускать дутьё. Сырой уголь туда не берут. Серы в нём тоже меньше: 0,9 моля сернистого газа на кусок против 1,4 у угля."))
            .anchor("coke")
            .showsOnLastPage("tfcpp:coke", "tfcpp:lignite_char")
            .text(
                L.of("Lignite char burns at 1100 °C for 1500 ticks and gives 1.0 mol of sulfur dioxide a piece. The blast furnace refuses it: it is crumbly and ashy.$(br2)A quern grinds coke into 4 coke powders and raises 2 g of coal dust into the air (see $(l:tfcpp/mine_safety#dust)Mine Safety$()).",
                    "Буроугольный полукокс горит при 1100 °C 1500 тактов и даёт 1,0 моля сернистого газа на кусок. Доменная печь его не берёт: он рассыпчатый и зольный.$(br2)Жёрнов мелет кокс в 4 порошка кокса и поднимает в воздух 2 г угольной пыли (см. $(l:tfcpp/mine_safety#dust)Шахтную безопасность$())."))
            .titled(
                L.of("Tar and liquor", "Смола и вода"),
                L.of("Tar and liquor condense only if a $(thing)condenser$() stands beside the oven. The oven keeps 50 mB of tar and 200 mB of liquor. Without a condenser their vapours are lost, though the gas is not any less for it. When a tank is full the batch waits and loses nothing.$(br2)Right-click the oven with a wooden bucket or a barrel to draw them off. They do not eat wood. A jug does not take them.",
                    "Смола и вода конденсируются, только если рядом с печью стоит $(thing)конденсатор$(). Печь хранит 50 мБ смолы и 200 мБ воды. Без конденсатора их пары пропадают, хотя газа от этого не меньше. Когда бак полон, партия ждёт и ничего не теряет.$(br2)Чтобы слить их, нажмите правой кнопкой по печи деревянным ведром или бочкой. Они не разъедают дерево. Кувшин их не берёт."))
            .anchor("liquids")
            .titled(
                L.of("What they are for", "Для чего они"),
                L.of("$(li)$(thing)Coal tar$() with burnt dolomite makes the $(thing)Basic Brick$() that lines a converter taking phosphorus (see $(l:tfcpp/iron_phosphorus#basic_lining)Iron and Phosphorus$()).$(li)$(thing)Ammoniacal liquor$() with a little oil of vitriol makes ammonium sulfate, a fertilizer (see $(l:tfcpp/acids#retort)Acids$()).$(li)$(thing)Pyroligneous liquor$() gives wood spirit and vinegar in the glass retort.$(li)$(thing)Wood tar$() has no use yet.",
                    "$(li)$(thing)Каменноугольная смола$() с обожжённым доломитом даёт $(thing)основной кирпич$() для футеровки конвертера, берущего фосфор (см. $(l:tfcpp/iron_phosphorus#basic_lining)Железо и фосфор$()).$(li)$(thing)Аммиачная вода$() с каплей купоросного масла даёт сульфат аммония, удобрение (см. $(l:tfcpp/acids#retort)Кислоты$()).$(li)$(thing)Подсмольная вода$() в стеклянной реторте даёт древесный спирт и уксус.$(li)$(thing)Древесный дёготь$() пока ни к чему не идёт."))
            .titled(
                L.of("The gas", "Газ"),
                L.of("The gas leaves hot, at the heat of the oven, up into the pipe above, or into the room over the oven if there is none. A full charge of 64 coal gives 2.5 mol a second for 18 minutes: a cell of the room is full of it in 17 seconds.$(br2)It is half hydrogen and a third methane, and burns or explodes in air from about 5 to 33% of gas (Le Chatelier's rule). 7% of it is carbon monoxide, which is 72 000 ppm, and there is hydrogen sulfide and ammonia besides. Never let it into a room (see $(l:tfcpp/gases)Gases$()).",
                    "Газ выходит горячим, с температурой печи, вверх в трубу или, если трубы нет, в комнату над печью. Полная загрузка в 64 куска угля даёт 2,5 моля в секунду 18 минут: клетка комнаты наполняется им за 17 секунд.$(br2)Он наполовину водород и на треть метан, горит и взрывается в воздухе примерно при 5-33% газа (правило Ле Шателье). 7% его это угарный газ, то есть 72 000 ppm, и есть ещё сероводород и аммиак. Не пускайте его в комнату (см. $(l:tfcpp/gases)Газы$())."))
            .anchor("gas")
            .titled(
                L.of("Hot gas, hot pipe", "Горячий газ, горячая труба"),
                L.of("The gas enters the pipe at the heat of the oven. A pipe hotter than its limit holds nothing: wood fails at 177 °C, lead at 150, wrought iron at 627, and only fired clay goes to 1127 °C (see $(l:tfcpp/pipes#walls)Pipes and Pressure$()).$(br2)The oven itself is fire brick, half a cubic metre that gives way at 3.5 atm, so a line that is shut at the far end bursts it.",
                    "Газ входит в трубу с жаром печи. Труба, нагретая выше своего предела, не держит ничего: дерево сдаётся при 177 °C, свинец при 150, кованое железо при 627, и лишь обожжённая глина идёт до 1127 °C (см. $(l:tfcpp/pipes#walls)Трубы и давление$()).$(br2)Сама печь это огнеупорный кирпич, полкубометра, сдающиеся при 3,5 атм, поэтому линия, закрытая на дальнем конце, её разорвёт."))
            .crafting("tfcpp:crafting/gas_burner",
                L.of("The $(thing)Gas Burner$() is three sheets of wrought iron under a brass mechanism.",
                    "$(thing)Газовая горелка$() это три листа кованого железа под латунным механизмом."))
            .showsOnLastPage("tfcpp:gas_burner")
            .titled(
                L.of("Burning the gas", "Сжигание газа"),
                L.of("Join the burner to the line by any side but the top, and set what is to be heated on it. An empty hand turns the hand wheel. Light it with flint and steel, flint and pyrite or a fire starter. The gas needs 0.005 atm of push to pass the nozzle.$(br2)It burns hydrogen (242 kJ a mole), methane (802) and carbon monoxide (283) in the oxygen of the room, up to 400 kW; hydrogen sulfide becomes sulfur dioxide. The flame is 1100 °C if the mixture gives 150 kJ a mole, cooler if it is thinner.",
                    "Присоедините горелку к линии с любой стороны, кроме верхней, и поставьте на неё то, что нужно греть. Пустая рука поворачивает маховик. Зажигают её огнивом, кремнем с пиритом или фаерстартером. Чтобы газ прошёл сопло, нужен напор 0,005 атм.$(br2)Она сжигает водород (242 кДж на моль), метан (802) и угарный газ (283) в кислороде комнаты, до 400 кВт, а сероводород газа превращается в сернистый. Пламя имеет 1100 °C, если смесь даёт 150 кДж на моль, и холоднее, если она беднее."))
            .anchor("burner")
            .titled(
                L.of("Power and heat", "Мощность и жар"),
                L.of("What stands on the burner is heated to the point where it loses by radiation as much as it receives, and that rises only as the fourth root of the power: 200 kW brings it to the full 1100 °C, 50 kW to about 780 °C.$(br2)A piece of coal gives 17 MJ in its gas. A battery of ovens can heat itself from its own output, as real works did: about a third of the yield of a batch is enough.",
                    "То, что стоит на горелке, нагревается до точки, где оно теряет излучением столько же, сколько получает, а она растёт лишь как корень четвёртой степени из мощности: 200 кВт доводят до полных 1100 °C, 50 кВт до около 780 °C.$(br2)Кусок угля даёт в газе 17 МДж. Батарея печей может греться своим же газом, как на настоящих заводах: хватает около трети выхода партии."))
            .titled(
                L.of("How it kills", "Чем она убивает"),
                L.of("$(li)An open burner that is not lit lets the gas into the room, as town gas killed people.$(li)If the pipe holds a burnable mixture of gas and air (a new pipe is full of air) and the burner is lit, the flame runs back and the line explodes. Fill the line with gas first, and keep the burner shut until then.$(li)A poor joint leaks carbon monoxide and hydrogen sulfide.$(li)Burnt, the gas makes carbon dioxide and sulfur dioxide in the room: ventilate.",
                    "$(li)Открытая и незажжённая горелка выпускает газ в комнату, так светильный газ и убивал людей.$(li)Если в трубе горючая смесь газа с воздухом (новая труба полна воздуха), а горелка горит, пламя бежит назад, и линия взрывается. Сначала заполните линию газом, а до того держите горелку закрытой.$(li)Плохой стык пропускает угарный газ и сероводород.$(li)Сгорев, газ даёт в комнате углекислый и сернистый газ: проветривайте."));
    }

    private CoalGuide() {}
}
