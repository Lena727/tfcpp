package tfcpp.compat.guide;

import java.util.List;

/**
 * TFC's Mechanics chapters that TFC++ made wrong: the steel pump, which TFC let make water from nothing and now only moves it.
 */
final class MechanicsGuide
{
    private static final String CATEGORY = "tfc:mechanics";

    static List<Entry> entries()
    {
        return List.of(pumps());
    }

    private static Entry pumps()
    {
        return Entry.of("mechanics/pumps", CATEGORY, "tfc:steel_pipe", Entry.NO_SORT, L.of("Pumps", "Насосы"))
            .text(
                L.of("$(thing)Steel Pumps$() and $(thing)Steel Pipes$() carry fluids: $(thing)Water$(), $(thing)Salt Water$(), $(thing)Spring Water$(). They move source blocks, lift them upward, which $(l:food/aqueducts)Aqueducts$() cannot do, and top up small enclosed pools.$(br2)A pump moves water, it does not make it: the block it takes leaves its source. Only a river stays as it was.",
                    "$(thing)Стальные насосы$() и $(thing)стальные трубы$() переносят жидкости: $(thing)воду$(), $(thing)солёную воду$(), $(thing)родниковую воду$(). Они двигают блоки-источники, поднимают их вверх, чего не умеют $(l:food/aqueducts)акведуки$(), и пополняют небольшие замкнутые бассейны.$(br2)Насос переносит воду, а не создаёт её: блок, который он взял, исчезает из источника. Только река остаётся как была."))
            .showsOnLastPage("tfc:steel_pipe", "tfc:steel_pump")
            .anvil("tfc:anvil/steel_pipe",
                L.of("Steel pipes are forged from $(l:metalworking/steel)Steel$() on an $(l:metalworking/anvils)Anvil$(). A $(thing)Steel Pump$() is made of some pipes and $(l:getting_started/primitive_alloys#brass)Brass Mechanisms$().", "Стальные трубы куют из $(l:metalworking/steel)стали$() на $(l:metalworking/anvils)наковальне$(). $(thing)Стальной насос$() делают из нескольких труб и $(l:getting_started/primitive_alloys#brass)латунных механизмов$()."))
            .crafting("tfc:crafting/steel_pump",
                L.of("A pump needs a $(l:mechanics/crankshaft)Crankshaft$() and $(l:mechanics/mechanical_power)Mechanical Power$():$(br)$(li)It joins a pipe at its $(bold)bottom$().", "Насосу нужны $(l:mechanics/crankshaft)коленвал$() и $(l:mechanics/mechanical_power)механическая энергия$():$(br)$(li)К его $(bold)низу$() присоединяется труба."))
            .text(
                L.of("$(li)On its narrow side the pump stands against the working end of a crankshaft.$(li)The line of pipe is up to $(bold)16$() blocks long, and its open end must stand in the liquid. The atmosphere lifts water only about 10 m, so that end may lie no more than $(bold)10$() blocks below the pump. A deep mine needs pumps in stages.$(li)While the crank turns, liquid comes out on the wide end a block at a time.",
                    "$(li)Узкой стороной насос стоит у рабочего конца коленвала.$(li)Линия труб длиной до $(bold)16$() блоков, и её открытый конец должен стоять в жидкости. Атмосфера поднимает воду лишь на 10 м, поэтому этот конец может лежать не глубже $(bold)10$() блоков под насосом. Глубокой шахте нужны насосы ступенями.$(li)Пока крутится кривошип, жидкость выходит на широком конце по блоку."))
            .text(
                L.of("The lift is work, and it loads the shaft: the higher the water goes, the harder the crank is to turn. A block takes tens of seconds even at the full speed of a water wheel, and a wheel that is too weak, or a lift that is too high, stops the shaft.",
                    "Подъём это работа, и он нагружает вал: чем выше идёт вода, тем тяжелее крутить кривошип. Блок воды занимает десятки секунд даже при полной скорости водяного колеса, а слишком слабое колесо или слишком высокий подъём останавливают вал."));
    }

    private MechanicsGuide() {}
}
