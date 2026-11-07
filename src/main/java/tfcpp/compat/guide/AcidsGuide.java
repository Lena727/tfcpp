package tfcpp.compat.guide;

import java.util.List;

/**
 * The chapter of the TFC++ category about acids: the sulfur burner, the lead chamber, the glass retort with its sand bath and the recipes of it, the carboy, and the care that corrosive liquids need.
 * <p>
 * Every number on these pages is read from the code and from {@code docs/physics-ledger.md}: {@code SulfurBurning}, {@code SulfurBurnerBlockEntity}, {@code ChamberProcess}, {@code LeadChamberBlockEntity} (chamber), {@code SandBath}, {@code Distillation}, {@code DistillationTable} and the generated recipes {@code tfcpp:glass_distillation/*} (retort), {@code GlassRetortBlockEntity} (how the flask is used), {@code GlassCarboyItem}, {@code CorrosiveSpill}, {@code AcidVapour} and {@code Liquid}. A powder of sulfur is 1.56 mol and gives 1.56 x 0.0975 = 0.15 L of chamber acid; one burner at 0.0624 mol/s feeds about 3.4 blocks of chamber at 7% sulfur dioxide (0.26 x 0.07 = 0.0182 mol/s a block).
 */
final class AcidsGuide
{
    private static final String CATEGORY = "tfc:tfcpp";

    static List<Entry> entries()
    {
        return List.of(acids());
    }

    private static Entry acids()
    {
        return Entry.of("tfcpp/acids", CATEGORY, "tfcpp:lead_chamber", 9, L.of("Acids", "Кислоты"))
            .titled(
                L.of("Oil of vitriol", "Купоросное масло"),
                L.of("Sulfuric acid is the base of the chemical industry. Nitric and hydrochloric acids, fertilizers, pickling baths and batteries come from it. Roebuck built the first lead chambers at Birmingham in 1746.$(br2)The road is long: burn sulfur to $(thing)sulfur dioxide$(), turn it into dilute $(thing)chamber acid$() in lead chambers, concentrate that in glass to $(thing)oil of vitriol$(), and from the oil make the other acids.",
                    "Серная кислота это основа химической промышленности. Из неё делают азотную и соляную кислоты, удобрения, травильные ванны и аккумуляторы. Первые свинцовые камеры Роубак построил в Бирмингеме в 1746 году.$(br2)Дорога длинна: сжечь серу в $(thing)сернистый газ$(), превратить его в слабую $(thing)камерную кислоту$() в свинцовых камерах, сгустить её в стекле до $(thing)купоросного масла$() и из масла делать остальные кислоты."))
            .crafting("tfcpp:crafting/sulfur_burner",
                L.of("The $(thing)Sulfur Burner$() is eight fire bricks round a hollow, a trough open at the top.",
                    "$(thing)Серная горелка$() это восемь огнеупорных кирпичей вокруг пустоты, жёлоб, открытый сверху."))
            .showsOnLastPage("tfcpp:sulfur_burner")
            .titled(
                L.of("Burning sulfur", "Горение серы"),
                L.of("Drop powder of sulfur (50 g, 1.56 mol) into the burner, up to 64 powders. It burns at 2 g a second, a powder in 25 seconds, and takes the oxygen of the air in its network: S and O2 give SO2 and 297 kJ a mole.$(br2)Sulfur has to be lit, with flint and steel, flint and pyrite or a fire starter, which wears. In a pipe hotter than 250 °C it lights by itself. It goes out when the sulfur is gone or the oxygen falls below 5%.",
                    "Бросайте в горелку порошок серы (50 г, 1,56 моля), до 64 порошков. Она горит по 2 г в секунду, порошок за 25 секунд, и берёт кислород воздуха своей сети: S и O2 дают SO2 и 297 кДж на моль.$(br2)Серу надо зажигать огнивом, кремнем с пиритом или фаерстартером, который изнашивается. В трубе горячее 250 °C она загорается сама. Гаснет, когда серы нет или кислорода меньше 5%."))
            .anchor("burner")
            .crafting("tfcpp:crafting/lead_chamber",
                L.of("The $(thing)Lead Chamber$() is eight $(l:tfcpp/lead_silver#sheet)sheets of lead$() round a hollow: a cubic metre of lead room that joins pipe on every side.",
                    "$(thing)Свинцовая камера$() это восемь $(l:tfcpp/lead_silver#sheet)листов свинца$() вокруг пустоты: кубометр свинцовой комнаты, соединяющийся с трубой со всех сторон."))
            .showsOnLastPage("tfcpp:lead_chamber")
            .titled(
                L.of("The chamber process", "Камерный процесс"),
                L.of("Sulfur dioxide, oxygen and water make sulfuric acid, but only with $(thing)nitrogen oxides$() to carry the oxygen: they hand it to the sulfur dioxide and take it again from the air, and are hardly used up.$(br2)Each block of chamber is a cubic metre and works in the gas of its network: N blocks make N times the acid. At 7% sulfur dioxide a block makes 2.5 kg of acid in a process day. A burner feeds about three blocks.",
                    "Сернистый газ, кислород и вода дают серную кислоту, но только при $(thing)оксидах азота$(), которые переносят кислород: они отдают его сернистому газу и снова берут из воздуха и почти не расходуются.$(br2)Каждый блок камеры это кубометр, работающий в газе своей сети: N блоков дают в N раз больше кислоты. При 7% сернистого газа блок даёт 2,5 кг кислоты за сутки процесса. Одна горелка кормит около трёх блоков."))
            .anchor("chamber")
            .titled(
                L.of("What it needs", "Что ей нужно"),
                L.of("$(li)$(thing)Sulfur dioxide$() from a burner or a roast.$(li)$(thing)Air$(): oxygen above 6% of the gas. The acid thins the gas, and a vent lets air in.$(li)$(thing)Water$(): liquid water in the network, 0.071 L for each mole of acid.$(li)$(thing)Nitrogen oxides$(): right-click the chamber with saltpeter powder, half a mole each. Below 0.2% of the gas the rate falls with them.$(br)An empty hand reads the temperature, the tank and the share of each gas.",
                    "$(li)$(thing)Сернистый газ$() от горелки или обжига.$(li)$(thing)Воздух$(): кислорода больше 6% газа. Кислота разрежает газ, и выпуск пускает воздух внутрь.$(li)$(thing)Вода$(): жидкая вода в сети, 0,071 л на моль кислоты.$(li)$(thing)Оксиды азота$(): правый клик по камере порошком селитры, по полмоля. Ниже 0,2% газа скорость падает вместе с ними.$(br)Пустая рука показывает температуру, бак и долю каждого газа."))
            .titled(
                L.of("Warmth", "Тепло"),
                L.of("A chamber is at full speed from 60 °C. Each 10 °C colder halves it: at 20 °C it makes a sixteenth, and at freezing nothing, for there is no mist. The flame of the sulfur and the reaction itself (230 kJ a mole) warm it.$(br2)Above 80 °C the film of lead sulfate that guards the wall dissolves, and the wall wears 20 times faster. Keep the chamber between 60 and 80 °C.",
                    "Камера работает на полную от 60 °C. Каждые 10 °C холоднее вдвое замедляют её: при 20 °C она даёт шестнадцатую долю, а во льду ничего, потому что нет тумана. Греют её пламя серы и сама реакция (230 кДж на моль).$(br2)Выше 80 °C плёнка сульфата свинца, берегущая стенку, растворяется, и стенка изнашивается в 20 раз быстрее. Держите камеру между 60 и 80 °C."))
            .titled(
                L.of("Tail gas", "Хвостовые газы"),
                L.of("The nitrogen of the air is left behind and builds up. A vent is the way in for air and the way out for nitrogen, and with the nitrogen go some of the nitrogen oxides, 0.01 mol for each mole of acid: top them up with saltpeter. A closed plant starves for oxygen.$(br2)For each mole of acid: 1 mol of SO2, 0.5 of O2, 0.071 L of water, and 230 kJ of heat. Nothing is made from nothing.",
                    "Азот воздуха остаётся и копится. Выпуск это путь внутрь для воздуха и наружу для азота, а с азотом уходят и оксиды азота, по 0,01 моля на моль кислоты: добавляйте селитру. Закрытая установка задыхается без кислорода.$(br2)На каждый моль кислоты: 1 моль SO2, 0,5 O2, 0,071 л воды и 230 кДж тепла. Из ничего ничего не берётся."))
            .titled(
                L.of("Chamber acid", "Камерная кислота"),
                L.of("Chamber acid is 65% sulfuric acid, 1.55 kg a litre, and each mole of it is 0.0975 L: a powder of sulfur makes 0.15 L. It gathers in the 100 L tank of the block, which is no part of the network.$(br2)Right-click with a $(thing)carboy$() to draw it off, or set a piston pump with its back to the chamber: it takes the tank and nothing else. Chamber acid eats wood and iron: never a barrel or a bucket.",
                    "Камерная кислота это 65% серной кислоты, 1,55 кг на литр, и каждый её моль это 0,0975 л: порошок серы даёт 0,15 л. Она собирается в баке блока на 100 л, который не входит в сеть.$(br2)Правый клик $(thing)бутылью$() сливает её, либо поставьте поршневой насос спиной к камере: он берёт бак и ничего больше. Камерная кислота ест дерево и железо: никаких бочек и вёдер."))
            .anchor("tank")
            .spotlight("tfcpp:glass_retort", L.of("The Glass Retort", "Стеклянная реторта"),
                L.of("A flask of $(l:tfcpp/glass#retort)borosilicate glass$() over a $(thing)sand bath$(), with a condenser beside it as its receiver. Pour in liquids from a full container or a tap above, put powder in with a right click. A batch runs only while the flask is in the range of its recipe.",
                    "Колба из $(l:tfcpp/glass#retort)боросиликатного стекла$() над $(thing)песчаной баней$() с конденсатором сбоку как приёмником. Жидкость наливайте из полной ёмкости или из крана сверху, порошок кладите правым кликом. Партия идёт, только пока колба в диапазоне своего рецепта."))
            .anchor("retort")
            .showsOnLastPage("tfcpp:glass_retort")
            .crafting("tfcpp:crafting/sand_bath",
                L.of("The $(thing)Sand Bath$() is an iron tray: five sheets of wrought iron and sand.",
                    "$(thing)Песчаная баня$() это железный поддон: пять листов кованого железа и песок."))
            .showsOnLastPage("tfcpp:sand_bath")
            .titled(
                L.of("Choosing the depth", "Выбор глубины"),
                L.of("An empty hand adds a layer of sand, from 1 to 8; a crouching hand takes one off. Of what a fire has above the room only 1 part in (1 + depth) reaches the flask: 20 + (T - 20) / (1 + depth).$(br2)A charcoal fire of 1100 °C gives 236 °C at depth 4 and 290 °C at depth 3, which is why oil of vitriol, at 280 to 350 °C, wants the sand thinned. Sand evens the heat out: glass cannot take a fire.",
                    "Пустая рука добавляет слой песка, от 1 до 8, рука при приседании снимает слой. Из того, что у огня выше комнатной, до колбы доходит только 1 часть из (1 + глубина): 20 + (T - 20) / (1 + глубина).$(br2)Угольный огонь в 1100 °C даёт 236 °C при глубине 4 и 290 °C при глубине 3, поэтому для купоросного масла, 280-350 °C, песок надо истончить. Песок выравнивает жар: стекло огня не выдержит."))
            .titled(
                L.of("A batch", "Партия"),
                L.of("A recipe takes its liquid and powder at its start, and goes on only while the flask is within its range of temperature: outside it, it waits, and loses nothing. What boils over runs into the receiver, and the rest stays in the flask. A batch that has nowhere to put its output waits.$(br2)A recipe that gives something over needs a condenser beside the flask. Without one that something goes into the room as fume.",
                    "Рецепт берёт жидкость и порошок в начале и идёт, только пока колба в своём диапазоне температур: вне его он ждёт и ничего не теряет. Что отгоняется, стекает в приёмник, остальное остаётся в колбе. Партия, которой некуда девать выход, ждёт.$(br2)Рецепту, дающему отгон, нужен конденсатор рядом с колбой. Без него отгон уходит в комнату дымом."))
            .titled(
                L.of("Oil of vitriol, aqua fortis", "Купоросное масло, крепкая водка"),
                L.of("$(li)$(thing)Oil of vitriol$(): 1 L of chamber acid at 280-350 °C for a minute leaves 0.59 L, 93%, in the flask. Water boils off; no condenser is needed.$(li)$(thing)Aqua fortis$(): 8 powders of saltpeter and 0.23 L of oil of vitriol at 120-200 °C give 0.175 L of nitric acid in the receiver and 8 potassium bisulfate, with 0.2 mol of nitrogen oxides lost to the air.",
                    "$(li)$(thing)Купоросное масло$(): 1 л камерной кислоты при 280-350 °C за минуту оставляет в колбе 0,59 л, 93%. Вода выкипает, конденсатор не нужен.$(li)$(thing)Крепкая водка$(): 8 порошков селитры и 0,23 л купоросного масла при 120-200 °C дают 0,175 л азотной кислоты в приёмнике и 8 гидросульфата калия, а 0,2 моля оксидов азота уходит в воздух."))
            .titled(
                L.of("Spirit of salt", "Соляная кислота"),
                L.of("8 powders of salt and 0.39 L of oil of vitriol at 150-300 °C give 0.67 L of $(thing)hydrochloric acid$() in the receiver and 8 sodium bisulfate. The hydrogen chloride has to be taken up by water: pour 0.53 L of water into the receiver first.$(br2)With no water, or no condenser, 6.7 mol of hydrogen chloride goes into the room, and it is deadly at 1300 ppm.",
                    "8 порошков соли и 0,39 л купоросного масла при 150-300 °C дают 0,67 л $(thing)соляной кислоты$() в приёмнике и 8 гидросульфата натрия. Хлороводород должна поглотить вода: сначала налейте в приёмник 0,53 л воды.$(br2)Без воды или без конденсатора 6,7 моля хлороводорода уходит в комнату, а он смертелен при 1300 ppm."))
            .titled(
                L.of("From wood and from liquor", "Из дерева и из воды"),
                L.of("$(li)$(thing)Wood spirit$(): 1 L of $(l:tfcpp/coal_chemistry#liquids)pyroligneous liquor$() at 70-100 °C for half a minute gives 0.02 L in the receiver, crude methanol and acetone, and 0.9 L of vinegar stays in the flask.$(li)$(thing)Superphosphate$(): 8 powders of ground apatite and 0.27 L of chamber acid below 100 °C give 13 powders of fertilizer.$(li)$(thing)Ammonium sulfate$(): 1 L of $(l:tfcpp/coal_chemistry#liquids)ammoniacal liquor$() and 0.02 L of oil of vitriol below 100 °C give a powder of fertilizer.",
                    "$(li)$(thing)Древесный спирт$(): 1 л $(l:tfcpp/coal_chemistry#liquids)подсмольной воды$() при 70-100 °C за полминуты даёт 0,02 л в приёмнике, сырой метанол с ацетоном, а 0,9 л уксуса остаётся в колбе.$(li)$(thing)Суперфосфат$(): 8 порошков молотого апатита и 0,27 л камерной кислоты ниже 100 °C дают 13 порошков удобрения.$(li)$(thing)Сульфат аммония$(): 1 л $(l:tfcpp/coal_chemistry#liquids)аммиачной воды$() и 0,02 л купоросного масла ниже 100 °C дают порошок удобрения."))
            .titled(
                L.of("When the glass cracks", "Когда стекло трескается"),
                L.of("Borosilicate takes acids, but not too steep a gradient: above 550 °C the flask cracks. It breaks, all it held goes into the room as fume, an acid splashes everyone within 3 blocks, and the powder lies on the floor. Players within 12 blocks are told. Breaking the flask by hand does the same.$(br2)Choose the depth of the sand for the recipe, and watch the flask.",
                    "Боросиликат держит кислоты, но не слишком крутой перепад: выше 550 °C колба трескается. Она ломается, всё, что в ней было, уходит в комнату дымом, кислота обрызгивает всех в 3 блоках, а порошок остаётся на полу. Игроков в 12 блоках предупреждают. Разбить колбу рукой значит то же.$(br2)Подбирайте глубину песка под рецепт и следите за колбой."))
            .spotlight("tfcpp:glass_carboy", L.of("The Glass Carboy", "Стеклянная бутыль"),
                L.of("The $(l:tfcpp/glass#carboy)carboy$() holds 25 litres of acid or another chemical liquid, and nothing else, and does not stack. A full one pours into the flask; an empty one draws off the receiver, then the residue. Crouching, it draws the flask itself dry.",
                    "$(l:tfcpp/glass#carboy)Бутыль$() вмещает 25 литров кислоты или другой химической жидкости и ничего больше и не складывается. Полная выливает в колбу, пустая забирает приёмник, затем остаток. При приседании она вычерпывает саму колбу."))
            .anchor("carboy")
            .showsOnLastPage("tfcpp:glass_carboy")
            .titled(
                L.of("Where acids may go", "Куда можно наливать кислоты"),
                L.of("Acids are not ingredients of TFC's containers: barrels, buckets and jugs refuse them, for they would eat the wood. They go into the carboy, the tank of a chamber, the flask, and pipe of lead or fired clay.$(br2)Iron passivates in strong acid and is eaten by weak, and hydrochloric acid eats it at any strength (see $(l:tfcpp/pipes#corrosion)Pipes and Pressure$()). A film under a litre that no container takes is rinsed onto the floor.",
                    "Кислоты не входят в ингредиенты ёмкостей TFC: бочки, вёдра и кувшины их не принимают, потому что они съели бы дерево. Их наливают в бутыль, бак камеры, колбу и в трубы из свинца или обожжённой глины.$(br2)Железо пассивируется в крепкой кислоте и разъедается слабой, а соляная кислота ест его любой крепости (см. $(l:tfcpp/pipes#corrosion)Трубы и давление$()). Плёнка меньше литра, которую не берёт ни одна ёмкость, смывается на пол."))
            .titled(
                L.of("Spilled acid", "Пролитая кислота"),
                L.of("A cracked flask, a broken chamber, a burst pipe and an overflow splash a corrosive liquid on everyone within 3 blocks: 30% of their health, armour helping a little.$(br2)And it fumes: hydrochloric acid gives 10 mol of hydrogen chloride a litre, nitric acid 5 mol of nitrogen oxides, sulfuric acid hardly any. See $(l:tfcpp/poisons#lungs)Poisons$().",
                    "Треснувшая колба, разбитая камера, лопнувшая труба и переполнение обрызгивают едкой жидкостью всех в 3 блоках: 30% их здоровья, броня помогает слегка.$(br2)А ещё она дымит: соляная кислота даёт 10 молей хлороводорода на литр, азотная 5 молей оксидов азота, серная почти ничего. См. $(l:tfcpp/poisons#lungs)Яды$()."))
            .titled(
                L.of("The whole plant", "Весь завод"),
                L.of("$(li)A burner and a chamber on one line, with a vent for air, water in the line, saltpeter in the chamber.$(li)A pump with its back to the chamber draws the acid into a lead line.$(li)The lead tap over a glass retort lets it into the flask.$(li)A fire under a sand bath, the sand set for 280-350 °C.$(li)An empty carboy takes the oil of vitriol: two litres need two batches of a minute.",
                    "$(li)Горелка и камера на одной линии, с выпуском для воздуха, водой в линии и селитрой в камере.$(li)Насос спиной к камере гонит кислоту в свинцовую линию.$(li)Свинцовый кран над стеклянной ретортой впускает её в колбу.$(li)Огонь под песчаной баней, песок подобран на 280-350 °C.$(li)Пустая бутыль забирает купоросное масло: двум литрам нужны две партии по минуте."));
    }

    private AcidsGuide() {}
}
