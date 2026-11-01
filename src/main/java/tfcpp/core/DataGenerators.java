package tfcpp.core;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import tfcpp.metallurgy.MetallurgyAssets;
import tfcpp.metallurgy.MetallurgyData;
import tfcpp.pneumatics.gas.Breathing;
import tfcpp.pneumatics.gas.GasIgnition;

@EventBusSubscriber(modid = TFCpp.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGenerators
{
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event)
    {
        final PackOutput output = event.getGenerator().getPackOutput();

        final DatapackBuiltinEntriesProvider datapack = event.getGenerator().addProvider(event.includeServer(), new DatapackBuiltinEntriesProvider(output, event.getLookupProvider(), new RegistrySetBuilder()
            .add(Registries.DAMAGE_TYPE, context -> {
                context.register(Breathing.ASPHYXIATION, new DamageType("tfcpp.asphyxiation", 0f));
                context.register(Breathing.CARBON_MONOXIDE, new DamageType("tfcpp.carbon_monoxide", 0f));
                context.register(Breathing.TOXIC_GAS, new DamageType("tfcpp.toxic_gas", 0f));
                context.register(GasIgnition.BLAST, new DamageType("tfcpp.blast", 0.1f));
            }), Set.of(TFCpp.MOD_ID)));

        event.getGenerator().addProvider(event.includeServer(), new DamageTags(output, datapack.getRegistryProvider(), event.getExistingFileHelper()));

        event.getGenerator().addProvider(event.includeServer(), new MetallurgyData(output));
        event.getGenerator().addProvider(event.includeClient(), new MetallurgyAssets.Models(output, event.getExistingFileHelper()));
        event.getGenerator().addProvider(event.includeClient(), new MetallurgyAssets.BlockStates(output, event.getExistingFileHelper()));

        event.getGenerator().addProvider(event.includeClient(), new English(output));
        event.getGenerator().addProvider(event.includeClient(), new Russian(output));
    }

    /** Gas acts on the lungs and blood, so armour does not reduce it, and it does not knock anybody back. */
    private static final class DamageTags extends TagsProvider<DamageType>
    {
        DamageTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper helper)
        {
            super(output, Registries.DAMAGE_TYPE, lookup, TFCpp.MOD_ID, helper);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider)
        {
            for (TagKey<DamageType> tag : List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_SHIELD, DamageTypeTags.NO_KNOCKBACK))
            {
                tag(tag).add(Breathing.ASPHYXIATION, Breathing.CARBON_MONOXIDE, Breathing.TOXIC_GAS);
            }
            tag(DamageTypeTags.BYPASSES_ARMOR).add(GasIgnition.BLAST); // A pressure wave passes straight through armour and into the lungs
            tag(DamageTypeTags.IS_EXPLOSION).add(GasIgnition.BLAST);
        }
    }

    private static final class English extends LanguageProvider
    {
        English(PackOutput output)
        {
            super(output, TFCpp.MOD_ID, "en_us");
        }

        @Override
        protected void addTranslations()
        {
            add("death.attack.tfcpp.asphyxiation", "%1$s ran out of air to breathe");
            add("death.attack.tfcpp.carbon_monoxide", "%1$s fell asleep and never woke up");
            add("death.attack.tfcpp.toxic_gas", "%1$s choked on toxic fumes");
            add("tfcpp.gas.smell.sulfur_dioxide", "A sharp, choking smell of burning sulfur catches your throat");
            add("tfcpp.gas.smell.hydrogen_sulfide", "The air stinks of rotten eggs");
            add("tfcpp.gas.smell.ammonia", "A pungent smell of ammonia stings your eyes");
            add("tfcpp.gas.smell.chlorine", "A sharp bleach-like smell burns your nose");

            add("itemGroup.tfcpp", "TFC++");
            add("fluid_type.tfcpp.metal.arsenical_copper", "Molten Arsenical Copper");
            add("fluid_type.tfcpp.mercury", "Mercury");
            add("item.tfcpp.metal.ingot.arsenical_copper", "Arsenical Copper Ingot");
            add("item.tfcpp.calcine.small_zinc", "Small Zinc Calcine");
            add("item.tfcpp.calcine.poor_zinc", "Poor Zinc Calcine");
            add("item.tfcpp.calcine.normal_zinc", "Zinc Calcine");
            add("item.tfcpp.calcine.rich_zinc", "Rich Zinc Calcine");
            add("item.tfcpp.glass_bulb", "Glass Bulb");
            add("item.tfcpp.mercury_bulb", "Mercury Bulb");
            add("fluid_type.tfcpp.metal.iron_oxide", "Molten Iron Oxide");
            add("item.tfcpp.slag.iron", "Iron Slag");
            add("item.tfcpp.slag.furnace", "Blast Furnace Slag");
            add("block.tfcpp.converter", "Bessemer Converter");
            add("tfcpp.converter.status", "%1$s C, %2$s mB of %3$s, %4$s, lining %5$s");
            add("tfcpp.converter.status_empty", "Empty, lining %1$s");
            add("tfcpp.converter.set", "the iron has set in the vessel");
            add("tfcpp.converter.reline_full", "It has to be emptied before it can be relined");
            add("tfcpp.converter.flame.sparks", "sparks, hardly any flame");
            add("tfcpp.converter.flame.boiling", "a long white flame");
            add("tfcpp.converter.flame.shortening", "the flame is shortening");
            add("tfcpp.converter.flame.dropped", "the flame has dropped");
            add("tfcpp.converter.lining.good", "sound");
            add("tfcpp.converter.lining.worn", "worn");
            add("tfcpp.converter.lining.thin", "thin, and about to go");
            add("tfcpp.info.converter", "Set below a blast furnace or pour in molten iron, then blow air through it from a pipe at a pressure above about 1.7 atm. Silicon burns first in a shower of sparks, then carbon in a long flame, and when the flame drops the iron itself starts to burn. Stop while the flame is shortening for steel. No fuel is used: the reactions alone keep the iron liquid, but only with a strong enough blast, and iron that sets in the vessel stops the blow. Water in the pipe will blow it apart. Reline it with fire bricks when it is empty.");
            add("tfcpp.info.iron_slag", "Holds iron that the furnace could not get out. It melts back into a blast furnace or bloomery charge.");
            add("block.tfcpp.retort", "Retort");
            add("block.tfcpp.retort_condenser", "Retort Condenser");
            add("tfcpp.retort.status", "%1$s C, charge: %2$s, carbon: %3$s, %4$s mB of %5$s");
            add("tfcpp.retort.status.empty_tank", "nothing condensed yet");
            add("tfcpp.retort.no_condenser", "No condenser beside the retort: the vapour will escape into the room");

            add("death.attack.tfcpp.blast", "%1$s was caught in a gas explosion");
            add("block.tfcpp.davy_lamp", "Davy Lamp");
            add("block.tfcpp.davy_lamp.filled", "Davy Lamp");
            add("item.tfcpp.wire_gauze", "Copper Wire Gauze");
            add("item.tfcpp.animal_cage", "Animal Cage");
            add("item.tfcpp.sentinel_cage", "Caged Sentinel Animal");
            add("block.tfcpp.settling_baffle", "Settling Baffle");
            add("item.tfcpp.powder.arsenic_trioxide", "White Arsenic");
            add("item.tfcpp.powder.antimony_trioxide", "Antimony Trioxide");
            add("item.tfcpp.powder.zinc_oxide", "Zinc Oxide");
            add("tfcpp.davy_lamp.steady", "The flame burns steady and yellow");
            add("tfcpp.davy_lamp.dim", "The flame burns low and dull: the air is short of oxygen");
            add("tfcpp.davy_lamp.pale_cap", "A faint pale cap shows above the flame");
            add("tfcpp.davy_lamp.blue_cap", "A blue cap stands above the flame");
            add("tfcpp.davy_lamp.tall_cap", "A tall blue cap reaches up the gauze: get out soon");
            add("tfcpp.davy_lamp.filled", "Flame fills the whole gauze: the air is explosive, get out now");
            add("tfcpp.davy_lamp.out", "The flame has died: this air cannot keep a flame going, and soon not a person either");
            add("tfcpp.sentinel.uneasy", "The animal in the cage is uneasy and breathing fast");
            add("tfcpp.sentinel.collapsed", "The animal in the cage has collapsed: leave, the air is poisoned");
            add("tfcpp.sentinel.dead", "The animal in the cage is dead");
            add("tfcpp.sentinel.starved", "The animal in the cage has starved");
            add("tfcpp.sentinel.state.lively", "Lively");
            add("tfcpp.sentinel.state.uneasy", "Uneasy");
            add("tfcpp.sentinel.state.collapsed", "Collapsed");
            add("tfcpp.settling_baffle.nothing", "Too little dust on the plates to scrape off");
            add("tfcpp.jei.retort", "Retort");
            add("tfcpp.jei.retort.temperature", "%s °C");
            add("tfcpp.jei.retort.carbon", "Carbon: %s mol");
            add("tfcpp.jei.retort.condenser", "Needs a condenser beside it");
            add("tfcpp.info.davy_lamp", "A lamp whose gauze keeps its flame from lighting the air around it. Carried in hand with oil in it, it shows firedamp as a blue cap above the flame, and goes out when oxygen runs low. It does not show carbon monoxide, and a gauze with gas burning in it for minutes gets hot enough to light the gas outside.");
            add("tfcpp.info.sentinel_cage", "Right-click a rat or a quail with an empty cage. The animal takes in carbon monoxide twenty times as fast as a person, so it collapses long before you feel anything. Feed it grain from the other hand at least every three days.");
            add("tfcpp.info.settling_baffle", "Place these in a closed chamber that furnace fumes pass through. Arsenic, antimony and zinc oxide dust settles on the plates, and is scraped off by right-clicking with an empty hand. Scraping stirs up some of the dust, so do not breathe it.");
            add("block.tfcpp.mine_fan", "Mine Fan");
            add("block.tfcpp.pipe.wood", "Wooden Pipe");
            add("block.tfcpp.pipe.ceramic", "Fired Clay Pipe");
            add("block.tfcpp.pipe.copper", "Copper Pipe");
            add("block.tfcpp.pipe.wrought_iron", "Wrought Iron Pipe");
            add("block.tfcpp.pipe.steel", "Steel Pipe");
            add("block.tfcpp.pipe_valve", "Gate Valve");
            add("block.tfcpp.pipe_vent", "Pipe Vent");
            add("block.tfcpp.pipe_tap", "Pipe Tap");
            add("block.tfcpp.safety_valve", "Safety Valve");
            add("block.tfcpp.mercury_manometer", "Mercury Manometer");
            add("block.tfcpp.pressure_vessel", "Pressure Vessel");
            add("block.tfcpp.piston_pump", "Piston Pump");
            add("tfcpp.pipe.opened", "Opened");
            add("tfcpp.pipe.closed", "Closed");
            add("tfcpp.manometer.reading", "%s mm of mercury. The air outside is 760");
            add("tfcpp.info.pipes", "A run of pipe is one volume at one pressure, and bursts at its weakest wall or joint. Hemp and tallow joints hold 10 atm at most. Corrosion thins the walls: copper suffers from ammonia and mercury, iron from brine and chlorine, and wood chars when hot. Breaking a pipe lets out everything in the run, so close a valve first.");
            add("tfcpp.info.pressure_vessel", "Half a cubic metre that joins any pipe touching it. A fire below heats everything in its network. Gas gains pressure in proportion to its absolute temperature, water adds its vapour pressure, and a vessel full of liquid with no room to expand gives way almost at once. Fit a safety valve.");
            add("tfcpp.info.piston_pump", "Driven by a shaft from the side. It takes in from the back, from a pipe, a barrel, standing water or the air, and delivers into a pipe at the front. Every turn sweeps the same volume, and the shaft pays for the difference in pressure.");
            add("tfcpp.info.safety_valve", "Lifts at 8 atm above the air outside and seats again at 7, with a loud hiss. It cannot be held shut.");
            add("tfcpp.info.manometer", "Right-click to read the pressure. Above 3 atm over the air outside the mercury is blown out of the tube, into the room.");
            add("tfcpp.info.mine_fan", "Driven by a shaft at its back, it blows out of its front and draws air in through its other sides, along the open spaces leading to it. Air moves quickly along a narrow drift and hardly at all across a large chamber. The fan loads the shaft more the faster it turns.");
            add("tfcpp.info.shaft_load", "Every machine on a shaft takes torque from its source. The more it has to drive, the slower it turns, and past what the source can start against it stops. A water wheel gives its best power at half of its free speed. Pumps take more torque the higher they lift. Water itself no longer multiplies, and a pump moves it from its intake to its outlet.");
            add("tfcpp.info.coal", "Coal seams hold methane, more of it the deeper they lie, and some rock next to them holds gas under pressure. Mining lets it out. A flame lights air holding 5% to 15% of it, and the blast is followed by afterdamp full of carbon monoxide.");
        }
    }

    private static final class Russian extends LanguageProvider
    {
        Russian(PackOutput output)
        {
            super(output, TFCpp.MOD_ID, "ru_ru");
        }

        @Override
        protected void addTranslations()
        {
            add("death.attack.tfcpp.asphyxiation", "%1$s задохнулся: в воздухе не осталось кислорода");
            add("death.attack.tfcpp.carbon_monoxide", "%1$s уснул и больше не проснулся");
            add("death.attack.tfcpp.toxic_gas", "%1$s отравился ядовитыми испарениями");
            add("tfcpp.gas.smell.sulfur_dioxide", "Резкий удушливый запах горелой серы перехватывает горло");
            add("tfcpp.gas.smell.hydrogen_sulfide", "В воздухе стоит запах тухлых яиц");
            add("tfcpp.gas.smell.ammonia", "Едкий запах нашатыря щиплет глаза");
            add("tfcpp.gas.smell.chlorine", "Резкий запах хлорки обжигает нос");

            add("itemGroup.tfcpp", "TFC++");
            add("fluid_type.tfcpp.metal.arsenical_copper", "Расплав мышьяковистой меди");
            add("fluid_type.tfcpp.mercury", "Ртуть");
            add("item.tfcpp.metal.ingot.arsenical_copper", "Слиток черновой мышьяковистой меди");
            add("item.tfcpp.calcine.small_zinc", "Малый цинковый огарок");
            add("item.tfcpp.calcine.poor_zinc", "Бедный цинковый огарок");
            add("item.tfcpp.calcine.normal_zinc", "Цинковый огарок");
            add("item.tfcpp.calcine.rich_zinc", "Богатый цинковый огарок");
            add("item.tfcpp.glass_bulb", "Стеклянная колба");
            add("item.tfcpp.mercury_bulb", "Ртутная колба");
            add("fluid_type.tfcpp.metal.iron_oxide", "Расплав оксида железа");
            add("item.tfcpp.slag.iron", "Железистый шлак");
            add("item.tfcpp.slag.furnace", "Доменный шлак");
            add("block.tfcpp.converter", "Конвертер Бессемера");
            add("tfcpp.converter.status", "%1$s °C, %2$s мБ: %3$s, %4$s, футеровка %5$s");
            add("tfcpp.converter.status_empty", "Пусто, футеровка %1$s");
            add("tfcpp.converter.set", "железо застыло в сосуде");
            add("tfcpp.converter.reline_full", "Перед заменой футеровки сосуд нужно опорожнить");
            add("tfcpp.converter.flame.sparks", "искры, пламени почти нет");
            add("tfcpp.converter.flame.boiling", "длинное белое пламя");
            add("tfcpp.converter.flame.shortening", "пламя укорачивается");
            add("tfcpp.converter.flame.dropped", "пламя упало");
            add("tfcpp.converter.lining.good", "целая");
            add("tfcpp.converter.lining.worn", "изношена");
            add("tfcpp.converter.lining.thin", "истончилась, скоро прогорит");
            add("tfcpp.info.converter", "Поставьте под доменную печь или налейте расплавленное железо, затем продувайте воздухом из трубы при давлении выше примерно 1,7 атм. Сначала в снопе искр горит кремний, затем в длинном пламени углерод, а когда пламя падает, начинает гореть само железо. Для стали остановите дутьё, пока пламя укорачивается. Топливо не нужно: железо остаётся жидким только от тепла реакций, но лишь при достаточно сильном дутье, а застывшее железо останавливает продувку. Вода в трубе разнесёт конвертер. Когда он пуст, футеровку можно обновить шамотным кирпичом.");
            add("tfcpp.info.iron_slag", "В нём осталось железо, которое печь не смогла извлечь. Его можно снова загрузить в доменную или сыродутную печь.");
            add("block.tfcpp.retort", "Реторта");
            add("block.tfcpp.retort_condenser", "Конденсатор реторты");
            add("tfcpp.retort.status", "%1$s °C, загрузка: %2$s, углерод: %3$s, %4$s мБ: %5$s");
            add("tfcpp.retort.status.empty_tank", "пока ничего не сконденсировалось");
            add("tfcpp.retort.no_condenser", "Рядом с ретортой нет конденсатора: пары уйдут в помещение");

            add("death.attack.tfcpp.blast", "%1$s попал под взрыв газа");
            add("block.tfcpp.davy_lamp", "Лампа Дэви");
            add("block.tfcpp.davy_lamp.filled", "Лампа Дэви");
            add("item.tfcpp.wire_gauze", "Медная проволочная сетка");
            add("item.tfcpp.animal_cage", "Клетка для животного");
            add("item.tfcpp.sentinel_cage", "Сторожевое животное в клетке");
            add("block.tfcpp.settling_baffle", "Пылеосадительная перегородка");
            add("item.tfcpp.powder.arsenic_trioxide", "Белый мышьяк");
            add("item.tfcpp.powder.antimony_trioxide", "Триоксид сурьмы");
            add("item.tfcpp.powder.zinc_oxide", "Оксид цинка");
            add("tfcpp.davy_lamp.steady", "Пламя горит ровно и жёлто");
            add("tfcpp.davy_lamp.dim", "Пламя низкое и тусклое: в воздухе мало кислорода");
            add("tfcpp.davy_lamp.pale_cap", "Над пламенем появился едва заметный бледный ореол");
            add("tfcpp.davy_lamp.blue_cap", "Над пламенем стоит голубой ореол");
            add("tfcpp.davy_lamp.tall_cap", "Высокий голубой ореол тянется вверх по сетке: скоро пора уходить");
            add("tfcpp.davy_lamp.filled", "Пламя заполнило всю сетку: воздух взрывоопасен, уходите немедленно");
            add("tfcpp.davy_lamp.out", "Пламя погасло: в этом воздухе огонь не горит, а вскоре не выживет и человек");
            add("tfcpp.sentinel.uneasy", "Животное в клетке беспокоится и часто дышит");
            add("tfcpp.sentinel.collapsed", "Животное в клетке упало: уходите, воздух отравлен");
            add("tfcpp.sentinel.dead", "Животное в клетке погибло");
            add("tfcpp.sentinel.starved", "Животное в клетке умерло от голода");
            add("tfcpp.sentinel.state.lively", "Бодрое");
            add("tfcpp.sentinel.state.uneasy", "Беспокоится");
            add("tfcpp.sentinel.state.collapsed", "Лежит без сил");
            add("tfcpp.settling_baffle.nothing", "На пластинах слишком мало пыли, соскрести нечего");
            add("tfcpp.jei.retort", "Реторта");
            add("tfcpp.jei.retort.temperature", "%s °C");
            add("tfcpp.jei.retort.carbon", "Углерод: %s моль");
            add("tfcpp.jei.retort.condenser", "Нужен конденсатор рядом");
            add("tfcpp.info.davy_lamp", "Лампа, сетка которой не даёт пламени поджечь окружающий воздух. В руке и с маслом показывает рудничный газ голубым ореолом над пламенем и гаснет, когда не хватает кислорода. Угарный газ она не показывает, а сетка с горящим внутри газом за несколько минут раскаляется настолько, что поджигает газ снаружи.");
            add("tfcpp.info.sentinel_cage", "Правым кликом пустой клеткой поймайте крысу или перепела. Животное набирает угарный газ в двадцать раз быстрее человека и падает задолго до того, как вы что-то почувствуете. Кормите его зерном из другой руки хотя бы раз в три дня.");
            add("tfcpp.info.settling_baffle", "Ставятся в закрытой камере, через которую идут печные газы. Пыль мышьяка, сурьмы и оксида цинка оседает на пластинах, её соскребают правым кликом пустой рукой. При этом часть пыли снова поднимается в воздух, так что дышать ею нельзя.");
            add("block.tfcpp.mine_fan", "Шахтный вентилятор");
            add("block.tfcpp.pipe.wood", "Деревянная труба");
            add("block.tfcpp.pipe.ceramic", "Керамическая труба");
            add("block.tfcpp.pipe.copper", "Медная труба");
            add("block.tfcpp.pipe.wrought_iron", "Труба из сварочного железа");
            add("block.tfcpp.pipe.steel", "Стальная труба");
            add("block.tfcpp.pipe_valve", "Задвижка");
            add("block.tfcpp.pipe_vent", "Трубный выпуск");
            add("block.tfcpp.pipe_tap", "Кран");
            add("block.tfcpp.safety_valve", "Предохранительный клапан");
            add("block.tfcpp.mercury_manometer", "Ртутный манометр");
            add("block.tfcpp.pressure_vessel", "Сосуд под давлением");
            add("block.tfcpp.piston_pump", "Поршневой насос");
            add("tfcpp.pipe.opened", "Открыто");
            add("tfcpp.pipe.closed", "Закрыто");
            add("tfcpp.manometer.reading", "%s мм ртутного столба. Снаружи воздух даёт 760");
            add("tfcpp.info.pipes", "Участок труб это один объём с одним давлением, и он рвётся в самом слабом месте. Стыки из пеньки с салом держат не больше 10 атм. Коррозия утончает стенки: медь страдает от аммиака и ртути, железо от рассола и хлора, а дерево обугливается при нагреве. Если сломать трубу, выйдет всё содержимое участка, поэтому сначала закройте задвижку.");
            add("tfcpp.info.pressure_vessel", "Половина кубометра, соединяется с любой трубой рядом. Огонь снизу нагревает всю сеть. Давление газа растёт пропорционально абсолютной температуре, вода добавляет давление своего пара, а сосуд, полный жидкости без места для расширения, рвётся почти сразу. Поставьте предохранительный клапан.");
            add("tfcpp.info.piston_pump", "Вал подводится сбоку. Насос берёт сзади, из трубы, бочки, стоячей воды или воздуха, и подаёт в трубу спереди. Каждый оборот вытесняет один и тот же объём, а вал оплачивает разность давлений.");
            add("tfcpp.info.safety_valve", "Открывается при 8 атм сверх наружного давления и садится обратно при 7, с громким шипением. Удержать его закрытым нельзя.");
            add("tfcpp.info.manometer", "Правый клик показывает давление. При 3 атм сверх наружного ртуть выбрасывает из трубки прямо в помещение.");
            add("tfcpp.info.mine_fan", "Приводится валом с задней стороны, выдувает вперёд, а воздух тянет через остальные стороны по ведущим к нему выработкам. По узкому штреку воздух идёт быстро, а через большую полость почти незаметно. Чем быстрее вентилятор вращается, тем сильнее он нагружает вал.");
            add("tfcpp.info.shaft_load", "Каждая машина на валу отбирает момент у источника. Чем больше нагрузка, тем медленнее вал, а сверх предела источник останавливается. Водяное колесо даёт наибольшую мощность на половине свободной скорости. Насос нагружает вал тем сильнее, чем выше поднимает воду. Вода больше не размножается сама, а насос перемещает её от входа к выходу.");
            add("tfcpp.info.coal", "Угольные пласты содержат метан, и чем глубже, тем больше, а порода рядом с ними может держать газ под давлением. При добыче он выходит наружу. Пламя поджигает воздух с 5–15% метана, а после взрыва остаётся смесь с большим количеством угарного газа.");
        }
    }

    private DataGenerators() {}
}
