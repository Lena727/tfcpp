# Справочник разработчика TFC++

Читай это вместо обхода кода. Правила проекта и цели лежат в `CLAUDE.md`, числа в `docs/physics-ledger.md`, план текущей версии в `docs/design-v*.md`. Нашёл факт, которого здесь нет и который пригодился бы следующему: допиши его сюда.

## Сборка и проверка

```bash
bash tools/verify.sh            # runData, JUnit, игровые тесты, короткая сводка
bash tools/verify.sh --client   # то же, плюс настоящий клиент
```

Запускай `tools/verify.sh`, а не gradle по отдельности: скрипт сам держит порядок и не выводит лишнего. Подробные логи остаются в `build/verify/`.

- JDK 21 лежит в `~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`, системная `java` на этой машине старая.
- `runData` всегда идёт отдельным запуском, раньше всего, что читает сгенерированные файлы.
- **Никогда не трогай `run/saves/New World`.** Только копии.
- Длинные heredoc портят обратные косые черты: файлы пиши инструментом записи. Python в Windows не читает `/tmp`.

## Исходники TFC

Тег v4.2.12 распакован в `~/.gradle/tfc-src/4.2.12`, только для чтения. Ищи там, а не в jar. Копировать код и ассеты TFC в аддон нельзя. В репозиторий TFC ничего не отправлять.

Данные TFC лежат там же в `src/main/resources` и `src/generated/resources`. Наши данные патчат их через `MetallurgyData.read(tfcFile(...))`.

## Где что лежит

| Нужно | Смотри |
|---|---|
| Блок над огнём, рецепт JSON, бак, статус, выпадение при поломке | `metallurgy/RetortBlockEntity`, `RetortBlock`, `RetortRecipe` |
| Партия с локальным временем, два бака, остаток в счётчике | `chemistry/CokeOvenBlockEntity`, `CarbonizationBatch`, `Remainder` |
| Блок сети труб, работающий каждую секунду | `chemistry/GasBurnerBlock`, `PipeMember`, `PipePort` |
| Регистрация жидкости | `chemistry/Liquid`, `Chemistry` (фабрика) |
| Данные и теги новой химии | `chemistry/ChemistryData`; модели и состояния в `chemistry/ChemistryAssets` |
| Данные металлургии, сплавы, топливо | `metallurgy/MetallurgyData`, новый расплав: `lead()` |
| Новая руда и обзор генерации | `geology/Ores`, `OreData`, `/tfcpp survey` |
| Вид газа, токсичность, горение | `pneumatics/gas/Gas`, `Physiology`, `Flammability`, `Deflagration` |
| Выброс газа в комнату | `GasEmission.release` |
| Угольная и каменная пыль: источники, осланцевание | `pneumatics/gas/DustSources`, `StoneDusting`, `QuernMixin` |
| Поверхность с осевшей пылью, выгребание | `pneumatics/instruments/DustStore` (общее: хранение, сбор, подъём пыли, выпадение), `SettlingBaffleBlockEntity` |
| Блок сети, который берёт вещество из газа сети: пыль и ртуть по температуре | `metallurgy/CondensationTowerBlock` и `CondensationTowerBlockEntity`, чистая физика `TowerCondensation`; тесты `core/LeadGameTests` |
| Конвертер и его продувка | `metallurgy/ConverterBath` (чистая физика), `ConverterBlockEntity` |
| Купелляция: серебро из веркблея, блик, глёт | `metallurgy/CupellationBath` (чистая физика), `CupelHearthBlockEntity`, `CupelHearthBlock`; время по `tickClock`, тесты `core/LeadGameTests` |
| Свинцовая камера: серная кислота из SO₂, воздуха, воды и окислов | `chemistry/ChamberProcess` (чистая физика), `LeadChamberBlock`, `LeadChamberBlockEntity`; тесты `core/AcidGameTests` |
| Серная горелка: сера в SO₂ из воздуха сети | `chemistry/SulfurBurning` (чистая физика), `SulfurBurnerBlock`, `SulfurBurnerBlockEntity`; тесты `core/AcidGameTests` |
| Разлив едкой жидкости: ожог рядом | `chemistry/CorrosiveSpill`, зовётся из `PipeTracker.blowDown`, `PipeNetwork.overflow` и `LeadChamberBlockEntity.spill`; тег `tfcpp:corrosive` |
| Смешанная загрузка домны, фосфористое железо | `metallurgy/IronBlend`, миксины `IronBlendMixins`, данные в `MetallurgyData.ironAndSteel`, `castableIngot`, `thomasProcess` |
| Стеклянная реторта: партия, рецепт, колба, разлив | `chemistry/Distillation` (чистая физика), `DistillationTable` (числа этапа F), `DistillationRecipe` (тип `tfcpp:glass_distillation`), `GlassRetortBlock`, `GlassRetortBlockEntity`; тесты `core/AcidGameTests` |
| Бак с дробными литрами (колба, вода приёмника) | `chemistry/FloatTank`; выход в целых мБ с остатком в `Remainder` |
| Песчаная баня | `chemistry/SandBath` (чистая функция), `SandBathBlock` (глубина в состоянии блока), `SandBathBlockEntity` |
| Бутыль для кислот | `chemistry/GlassCarboyItem` (подкласс `FluidContainerItem` TFC), теги `tfcpp:usable_in_carboy`, способность предмета в `Chemistry.registerCapabilities` |
| Проба Марша и индикатор ртути на золотом листе | `chemistry/PoisonTests` (чистая физика, тест `PoisonTestsTest`), `MarshApparatusItem`, `pneumatics/instruments/GoldLeafIndicatorBlock` и `GoldLeafIndicatorBlockEntity`; игровые тесты `core/AcidGameTests` (batch `tfcpp_marsh`) |
| Пары из жидкости, если её не держит конденсатор или колба | `chemistry/AcidVapour` |
| Текстуры | `tools/generate_textures.py` |
| Игровые тесты | `core/PhosphorusGameTests` (свежий образец: домна, конвертер, футеровка, сохранение), `ChemistryGameTests`, `GasGameTests`, `IronGameTests` |
| Перечень миксинов | таблица в `README.md`, каждый новый добавляется туда и в `tfcpp.mixins.json` |

## Факты о TFC, которые стоили времени

- Топливо горит в тактах игроков: на пустом сервере не расходуется.
- Огонь отдаёт тепло блоку над собой через `HeatCapability.provideHeatTo`. Блок должен быть в теге `tfc:charcoal_forge_invisible`, если стоит над горном.
- `tfc:molten_metals` включает расплав в тег для ingot mold. `c:ingots` задаёт размер слитка: без родительского тега слиток считается мелким и складывается по 64.
- Сплав не может содержать свой результат как компонент.
- Топка (`FireboxBlockEntity`) расходует топливо своим методом, не через `AbstractFirepitBlockEntity`.
- Насос берёт из сети, если блок открыт к нему. Блок, который открыт со всех сторон, но держит жидкость в своём баке (камера с кислотой), отдаёт бак первым: `PipeMember.tankOpenToPump`. У печи, которая открыта только вверх, поведение прежнее.
- Игрок в серверном тесте не получает урон, пока его нет в мире: `helper.getLevel().addFreshEntity(player)`, иначе `getEntitiesOfClass` его не найдёт.
- Бочки, вёдра и кувшины фильтруют жидкости по белым спискам. Едкие жидкости нельзя добавлять в `tfc:ingredients`.
- Домна и сыродутная печь выбирают рецепт по первой загруженной крице/руде (по её расплаву): в `BlastFurnaceBlockEntity` см. `updateCachedRecipe`, `addItemsFromWorld`; то, что не подходит ни как сырьё, ни как катализатор рецепта, просто не принимается. У домны нет сплава: один `inputFluid`, и другая жидкость в нём заменяет прежнюю (`insertOrReplaceFluid`), а `outputFluidTank.fill` другой жидкости молча возвращает 0.
- Сплав тигля (`FluidAlloy`) требует, чтобы каждая жидкость в нём была названа в рецепте: лишний металл даёт `Metal.UNKNOWN`, не сталь. Отдельный перечень «запрещено» не нужен.
- Рецепты домны, сыродутной печи, бочки принимают `{"tag": ...}` и `{"type":"neoforge:compound","ingredients":[...]}` в `fluid`/`input_fluid`.
- Руды: `tfc:prospectable` = `#c:ores`, `tfc:collapse`. Больше rarity значит реже. Жила: `tfc:cluster_vein` или `tfc:disc_vein` с `blocks` и `indicator`, подключается тегом `tfc:in_biome/veins`.

- **Виды-пылинки в `Gas`.** `COAL_DUST`, `SETTLED_COAL_DUST`, `SETTLED_ROCK_DUST` лежат в той же `float[]` ячейки, но их единица 1 кг, а не моль. `Gas.isParticulate()`: пыль не входит в `GasMath.total`, давление, кислород и плотность (`relativeDensity`), поэтому в цикле по видам, где важны моли, её надо пропускать. `Gas.mobile()` ложно у осевшей пыли: симулятор её не двигает и не выдувает, трубы, вентилятор, дыхание её не берут. Каждый цикл по `Gas.VALUES`/`Gas.COUNT`, который переносит вещество между ячейками или в трубу, обязан пропускать `!mobile()` (вентилятор) или `isParticulate()` (труба). Ячейка с одной осевшей пылью живёт и сохраняется (`GasMath.mobileContent`, `settledContent`), но чанк, где двигаться нечему, засыпает (`GasChunkData.isActive`): будить его надо через `cellForEmission` или `markChanged`, а запись прямо в чужую ячейку чанк не разбудит.
- **Горение пыли идёт по Ле Шателье** (`Flammability`): нижние пределы газа и пыли складываются, и 2,5% метана с 25 г/м³ пыли горят вместе. `relativeToLowerLimit` считает только газы (его читает лампа), `relativeToLowerLimitWithDust` и `isFlammable` с пылью.

- **Стеклодувное дело TFC** (`tfc:glassworking`). Рецепт выбирает пара «партия + список операций»: `GlassworkingRecipe.matches` сравнивает `operations.equals(steps)` и `batchItem.test(batch)`. Партия попадает в трубку только если она в теге `tfc:glass_batches` (`ForgeEventHandler.onItemStacked`, рецепт `add_glass`). Ярусы это только теги для рецептов: `glass_batches_tier_2` (кремнезёмная, гематитовая), `_tier_3` (плюс оливиновая), вулканическая стоит только в `glass_batches` и `glass_batches_not_tier_1`. **Рабочей температуры у партии нет**: `GlassOperation.hasRequiredTemperature` сравнивает с одним порогом операции (`Heat.FAINT_RED.getMin()`, 480 °C, у спила 0), он зашит в коде, а не в данных. Предел сверху задан рецептом нагрева трубки с стеклом (`blowpipe_with_glass` → пустая трубка при 1500 °C), а латунная трубка сама плавится при 930 °C. Так что стекло, работающее только от 1500 °C, данными не сделать.
- **Ёмкость для жидкостей TFC**: `FluidContainerItem` (защищённый конструктор: вместимость, тег жидкостей, можно ли ставить блоки), содержимое в компоненте `TFCComponents.FLUID`, способность предмета `new FluidContainerHandler(stack, item.containerInfo())` регистрируется своей строкой `event.registerItem(Capabilities.FluidHandler.ITEM, ...)`. Подсказка, имя `...filled` и стек 1 с жидкостью даёт сам базовый класс.
- Блок, который обрабатывает пустую руку в `useWithoutItem`, перехватывает и руку с предметом (ванильный порядок: `useItemOn` вернул `PASS_TO_DEFAULT_BLOCK_INTERACTION`, затем зовётся `useWithoutItem`). Если блок не должен мешать ставить блоки рядом, в начале проверь `player.getMainHandItem().isEmpty()` и верни `PASS`.
- Для блока с `IntegerProperty` в файле состояний варианты писать явно на каждое значение (`getVariantBuilder(...).forAllStates`, как у горелки): `simpleBlock` пишет единственный ключ `""`, и работает ли он при свойствах, на клиенте не проверялось.

## Ловушки игровых тестов

- Шаблон `empty9`. Тесты одного batch идут одновременно: всё, что меняет календарь или взрывает, получает свой batch.
- `helper.relativePos` ненадёжен, используй абсолютные координаты. Сервер не убирает постройки прошлых batch.
- Порядок `runAtTickTime` внутри такта не определён. Шаг сети может прийти в любой такт. Огонь в тесте надо подтверждать каждый такт.
- Трубы могут пересекать границу чанка.
- Сеть труб шагает раз в 20 тактов, фаза зависит от позиции первого сегмента: число шагов за срок теста известно с точностью до одного, поэтому считай его по самому веществу (`ln(остаток / начало) / ln(1 - доля)`), а не по тактам. Температуру сети держи `helper.onEachTick`: сама она остывает к погоде с постоянной времени около 9 с.
- Форма TFC (`Mold`) отдаёт жидкость, только когда её температура не ниже точки плавления содержимого (`isMolten`). Холодная форма с металлом не сливается: после наполнения выставь `HeatCapability.setTemperature(stack, t)`.
- Серверные тесты не видят ошибки клиента: моделей, текстур, рендера. `simpleBlockItem`, `basicItem`, `getBuilder` теряют папку у имён с путём, поэтому пиши явные `block/` и `item/`.

## Чек-лист перед сдачей

Каждый пункт уже приводил к реальному дефекту. Пройди их сам и напиши в отчёте, что не применимо.

**Физика**

- [ ] Масса выхода не больше массы входа. Для каждого процесса есть JUnit-тест на чистом классе без Minecraft.
- [ ] Числа совпадают с проектом, единицы подписаны, новые записаны в `docs/physics-ledger.md`. Значение проекта кажется неверным: сообщи, а не меняй молча.
- [ ] Выход нельзя удвоить: нет бесплатной перезаливки, замены блока ради новой футеровки, повторной переработки того же.
- [ ] Шкала: 1 мБ кислот и воды = 1 л, 1 мБ металла = 1 мл.

**Время**

- [ ] Локальные процессы считают `Calendars.SERVER.getTicks()`, а не `level.getGameTime()`. Иначе на пустом сервере процесс идёт над огнём, который не расходует топливо.
- [ ] Перевод календаря назад не ломает процесс. Есть предел на длинный шаг.

**Устройство**

- [ ] Блоки ломаются и меняют структуру сети только через `PipeTracker.later`, когда сеть не обходится.
- [ ] Всё состояние сохраняется и загружается. Отдельный тест на сохранение.
- [ ] Поломка блока ничего не удваивает и не теряет молча.
- [ ] Выгрузка чанка не теряет и не удваивает вещество.
- [ ] Закрытое, выключенное или незажжённое состояние делает то, что сделало бы в реальности, а не просто ничего.
- [ ] Опасность честная: подумай, как именно это убивало людей, и заложи.

**Данные**

- [ ] Рецепт реально загружается: тест через `getRecipeManager().byKey`.
- [ ] Новый слиток или расплав: `c:ingots`, форма обычная и огнеупорная, `fluid_heat`, `item_heat`.
- [ ] Модель, состояние, текстура, лут, тег добычи, вкладка, en_us и ru_ru, рецепт получения блока.
- [ ] Новый миксин: `require=1`, строка в `README.md`.

**Производительность**

- [ ] Устройство работает раз в секунду, а не каждый такт, если этого не требует физика. Чанк помечается изменённым только при заметном изменении.

**Отчёт**

- [ ] Настоящий вывод `tools/verify.sh`, что не проверено, какие решения приняты вне проекта.
