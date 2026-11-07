# Реестр величин TFC++

Числа, на которых стоит физика мода. Новая величина записывается сюда в той же задаче, где появляется в коде. Если значение в коде и здесь расходятся, правда в коде, а реестр надо поправить.

**Шкалы:** химические жидкости и вода: 1 мБ = 1 л. Металлы: 1 мБ = 1 мл. Куски: уголь, кокс, лигнит, древесный уголь 3 кг, полено 5 кг, порошок 50 г. Исключение: порошок флюса весит 0,75 кг (7,5 моль CO₂ при обжиге), а порошок извести даёт 7,5 моль CaO. Время: сутки 1440 с, час 60 с. Температура в трубах и `PipeMaterial` в кельвинах, в печах и рецептах в градусах Цельсия.

**Таблицы, которые живут только в коде и сюда не переписаны:** `MetalHeat` (металлы), `Flammability.FUELS` (пределы газов; пределы пыли записаны ниже), `Physiology.acuteSeverity`, `FuelSulfur`, `CarbonizationTable`.

## pneumatics/pipes/PipeMaterial
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| WOOD: прочность (`strength`) | `1.5f` | МПа | Bored logs and bound staves were the water mains of old cities. They char once hot. |
| WOOD: толщина стенки (`wall`) | `30f` | мм | Bored logs and bound staves were the water mains of old cities. They char once hot. |
| WOOD: максимальная температура (`maximumTemperature`) | `450f` | ? | Bored logs and bound staves were the water mains of old cities. They char once hot. |
| CERAMIC: прочность (`strength`) | `1.2f` | МПа | Fired clay is proof against nearly everything, but it is brittle and its joints are weak. |
| CERAMIC: толщина стенки (`wall`) | `15f` | мм | Fired clay is proof against nearly everything, but it is brittle and its joints are weak. |
| CERAMIC: максимальная температура (`maximumTemperature`) | `1400f` | ? | Fired clay is proof against nearly everything, but it is brittle and its joints are weak. |
| CERAMIC (химическая керамика, штайнцойг) | прочность `1.2f` МПа, стенка `15f` мм: по Барлоу 3,55 атм избыточного | атм | Стойка к серной, азотной и соляной кислоте любой крепости при любой температуре; плавиковая кислота и горячая крепкая щёлочь её берут. Хрупкая, слабая на разрыв, раструбные стыки слабее. Химическая керамическая труба продаётся на рабочее давление около 2–4 бар: источник по памяти из справочников химического оборудования, порядок величины. Те же числа и у фитингов керамики. |
| Горячий щёлок для керамики (`HOT_ALKALI_KELVIN`, `HOT_ALKALI_CERAMIC`) | выше `353f` К: `0.05f` доли стенки в сутки; холодный `0f` | К, доля в сутки | Горячая крепкая щёлочь снимает глазурь, потом разъедает черепок. Игровая цифра: медленно, но не нулевая. |
| Установка предохранительного клапана (`safetyValveSetting`, `SAFETY_VALVE_SET_SHARE`) | `min(8, 0.8 * рейтинг нового корпуса)` атм избыточного; седло на 1 атм ниже | атм | Клапан должен поднять до разрыва своего корпуса: железо 8, свинец 4,7, керамика 2,8. Игровая цифра: четыре пятых. |
| COPPER: прочность (`strength`) | `70f` | МПа | — |
| COPPER: толщина стенки (`wall`) | `4f` | мм | — |
| COPPER: максимальная температура (`maximumTemperature`) | `700f` | ? | — |
| LEAD: прочность (`strength`) | `5f` | МПа | Lead is weak and creeps under load, but it is proof against sulfuric acid. Низкая, потому что свинец течёт под нагрузкой. |
| LEAD: толщина стенки (`wall`) | `6f` | мм | Толще меди: свинец слабее, стенку берут толще. |
| LEAD: максимальная температура (`maximumTemperature`) | `423f` | K | It softens at about 150 C: 423 K. |
| LEAD: коррозия от SO2 (`corrosion(Gas.SULFUR_DIOXIDE)`) | `0f` | доля стенки в сутки | Sulfur dioxide makes a film of lead sulfate that protects the metal. |
| LEAD: коррозия от Cl2 (`corrosion(Gas.CHLORINE)`) | `0.1f` | доля стенки в сутки | Chlorine still attacks lead. |
| LEAD: коррозия от H2S (`corrosion(Gas.HYDROGEN_SULFIDE)`) | `0.05f` | доля стенки в сутки | Hydrogen sulfide blackens lead. |
| LEAD: коррозия от уксуса (`corrosion("tfc:vinegar")`) | `0.1f` | доля стенки в сутки | Vinegar dissolves lead, as it did in Roman wine. Вдвое больше, чем у меди (0,05). |
| NITROGEN_OXIDES: коррозия (`corrosion(Gas.NITROGEN_OXIDES)`) | WOOD `0.05f`, COPPER `0.1f`, WROUGHT_IRON и STEEL `0.05f`, LEAD `0f` (по умолчанию), CERAMIC `0f` | доля стенки в сутки | Wet oxides make nitric acid, which chars wood and dissolves copper. Nitric acid passivates iron, so iron takes less than copper. Lead takes nothing, as in the lead chambers that ran on these oxides. |
| Кислота камерная (`corrosion("tfcpp:chamber_acid", T)`) | WOOD `0.5f`, CERAMIC `0f`, COPPER `0.2f`, WROUGHT_IRON и STEEL `0.5f`, LEAD `0.001f` до 353 K | доля стенки в сутки | Chamber acid is about 65% sulfuric acid. Lead holds it under a film of sulfate. |
| Серная кислота (`corrosion("tfcpp:sulfuric_acid", T)`) | WOOD `1.0f`, CERAMIC `0f`, COPPER `0.1f`, WROUGHT_IRON и STEEL `0.01f`, LEAD `0.1f` до 353 K | доля стенки в сутки | Strong sulfuric acid passivates iron, which is why steel carried oil of vitriol. Для свинца `0.1f` в сто раз больше камерной кислоты (`0.001f`) при той же плёнке сульфата: значение проекта под вопросом, см. отчёт. |
| Азотная кислота (`corrosion("tfcpp:nitric_acid", T)`) | WOOD `1.0f`, CERAMIC `0f`, COPPER `2.0f`, WROUGHT_IRON и STEEL `0.02f`, LEAD `0.5f` | доля стенки в сутки | Strong nitric acid passivates iron. Copper dissolves in it quickly. |
| Соляная кислота (`corrosion("tfcpp:hydrochloric_acid", T)`) | WOOD `0.02f`, CERAMIC `0f`, COPPER `0.1f`, WROUGHT_IRON и STEEL `2.0f`, LEAD `0.1f` | доля стенки в сутки | Chloride ions keep iron from passivating, so it rusts quickly. |
| Температура, выше которой горячая кислота растворяет плёнку на свинце (`HOT_ACID_KELVIN`) | `353f` | K | Hot acid dissolves the film of lead sulfate above 80 C. |
| Множитель коррозии свинца в горячей кислоте (`HOT_ACID_FACTOR`) | `20f` | безразм. | A game figure, not measured: the film is gone and the lead is 20 times worse. |
| WROUGHT_IRON: прочность (`strength`) | `180f` | МПа | — |
| WROUGHT_IRON: толщина стенки (`wall`) | `4f` | мм | — |
| WROUGHT_IRON: максимальная температура (`maximumTemperature`) | `900f` | ? | — |
| STEEL: прочность (`strength`) | `350f` | МПа | — |
| STEEL: толщина стенки (`wall`) | `4f` | мм | — |
| STEEL: максимальная температура (`maximumTemperature`) | `1000f` | ? | — |
| Предел давления стыков (`JOINT_LIMIT_ATM`) | `10f` | атм | Joints sealed with hemp and tallow |

## pneumatics/pipes/PipePhysics
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Газовая постоянная (`GAS_CONSTANT`) | `8.314f` | Дж/(моль·К) | — |
| Паскалей в атмосфере (`PASCALS_PER_ATM`) | `101325f` | Па/атм | — |
| Молярная масса воздуха (`AIR_MOLAR_MASS`) | `28.97f` | г/моль | — |
| Комнатная температура (`ROOM_TEMPERATURE`) | `293f` | К | — |
| Диаметр трубы (`BORE`) | `0.1f` | м | Pipe of 100 mm bore. |
| Площадь сечения трубы (`BORE_AREA`) | `(float) (Math.PI * BORE * BORE / 4f)` | м² | — |
| Коэффициент трения Дарси (`FRICTION_FACTOR`) | `0.03f` | безразм. | Darcy friction factor of a rough old pipe in turbulent flow. |
| Потери на входе и выходе порта (`PORT_LOSS`) | `1.5f` | скоростных напоров | Losses of the entry and exit of a port, in velocity heads. |
| Скорость звука, предел скорости истечения (`SPEED_OF_SOUND`) | `340f` | м/с | Gas cannot leave a pipe faster than the speed of sound, and the flow chokes there. |
| Температурное расширение воды (`LIQUID_EXPANSION`) | `2.1e-4f` | 1/К | Thermal expansion of water per kelvin, and its bulk modulus in atmospheres. |
| Модуль объёмной упругости воды (`LIQUID_BULK_MODULUS`) | `21700f` | атм | Thermal expansion of water per kelvin, and its bulk modulus in atmospheres. |
| Доля заполнения жидкостью, выше которой газ не задаёт давление (`SOLID_LIQUID_FILL`) | `0.999f` | доля | Below this share of free space, gas no longer sets the pressure. |
| Показатель адиабаты воздуха (`GAMMA`) | `1.4f` | безразм. | Ratio of specific heats of air. |
| Теплота парообразования воды (`WATER_LATENT_HEAT`) | `2257f` | кДж/л | The heat water takes to turn to steam, in kJ per litre. |

## pneumatics/gas/Gas
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| CARBON_MONOXIDE: молярная масса (`molarMass`) | `28.01f` | г/моль | — |
| CARBON_DIOXIDE: молярная масса (`molarMass`) | `44.01f` | г/моль | — |
| FLUE_NITROGEN: молярная масса (`molarMass`) | `28.01f` | г/моль | — |
| SULFUR_DIOXIDE: молярная масса (`molarMass`) | `64.07f` | г/моль | — |
| METHANE: молярная масса (`molarMass`) | `16.04f` | г/моль | — |
| HYDROGEN: молярная масса (`molarMass`) | `2.016f` | г/моль | — |
| HYDROGEN_SULFIDE: молярная масса (`molarMass`) | `34.08f` | г/моль | — |
| AMMONIA: молярная масса (`molarMass`) | `17.03f` | г/моль | — |
| CHLORINE: молярная масса (`molarMass`) | `70.90f` | г/моль | — |
| NITROGEN_OXIDES: молярная масса (`molarMass`) | `46.01f` | г/моль | Nitrogen dioxide, NO2. It carries the oxygen to the sulfur dioxide in a lead chamber. |
| MERCURY_VAPOR: молярная масса (`molarMass`) | `200.59f` | г/моль | Air at 20 C can only hold about 13 mg/m3 of mercury vapour, or 1.6 ppm. |
| MERCURY_VAPOR: насыщение (`saturationMoles`) | `6.6e-5f` | моль на ячейку | Air at 20 C can only hold about 13 mg/m3 of mercury vapour, or 1.6 ppm. |
| MERCURY_VAPOR: скорость осаждения избытка (`depositionRate`) | `0.05f` | 1/с | Air at 20 C can only hold about 13 mg/m3 of mercury vapour, or 1.6 ppm. |
| ARSENIC_TRIOXIDE: молярная масса (`molarMass`) | `197.84f` | г/моль | Arsenic and antimony oxides sublime in a furnace and condense to a fine white dust as soon as the fumes cool. |
| ARSENIC_TRIOXIDE: насыщение (`saturationMoles`) | `0f` | моль на ячейку | Arsenic and antimony oxides sublime in a furnace and condense to a fine white dust as soon as the fumes cool. |
| ARSENIC_TRIOXIDE: скорость осаждения избытка (`depositionRate`) | `0.003f` | 1/с | Arsenic and antimony oxides sublime in a furnace and condense to a fine white dust as soon as the fumes cool. |
| ANTIMONY_TRIOXIDE: молярная масса (`molarMass`) | `291.52f` | г/моль | — |
| ANTIMONY_TRIOXIDE: насыщение (`saturationMoles`) | `0f` | моль на ячейку | — |
| ANTIMONY_TRIOXIDE: скорость осаждения избытка (`depositionRate`) | `0.003f` | 1/с | — |
| ZINC_OXIDE_FUME: молярная масса (`molarMass`) | `81.38f` | г/моль | Zinc vapour burns in air to a white smoke of zinc oxide. |
| ZINC_OXIDE_FUME: насыщение (`saturationMoles`) | `0f` | моль на ячейку | Zinc vapour burns in air to a white smoke of zinc oxide. |
| ZINC_OXIDE_FUME: скорость осаждения избытка (`depositionRate`) | `0.003f` | 1/с | Zinc vapour burns in air to a white smoke of zinc oxide. |
| LEAD_OXIDE_FUME: молярная масса (`molarMass`) | `223.2f` | г/моль | Lead boils off a smelting hearth and burns in air to a fine smoke of oxide. |
| LEAD_OXIDE_FUME: насыщение (`saturationMoles`) | `0f` | моль на ячейку | Lead boils off a smelting hearth and burns in air to a fine smoke of oxide. |
| LEAD_OXIDE_FUME: скорость осаждения избытка (`depositionRate`) | `0.003f` | 1/с | Lead boils off a smelting hearth and burns in air to a fine smoke of oxide. |
| MERCURY_VAPOR: плёнка испаряется обратно (`evaporatesFromDeposit`) | `true` | — | Осевшая ртуть лежит жидкой плёнкой там, где конденсировалась, и отдаёт пар обратно до насыщения со скоростью осаждения (0,05 1/с, постоянная времени 20 с). Пока плёнка есть, комната держится у 1,6 ppm. Пыль оксидов (`ARSENIC_TRIOXIDE` и др.) остаётся лежать, обратно не возвращается. Количество хранится на чанк (`GasChunkData.deposit`), места плёнки отдельно (`filmSites`): воздух получает пар только там, где она лежала |
| Порог создания ячейки для газа без насыщения (`ORDINARY_CREATE_CELL_MOLES`) | `2e-5f` | моль | При смешении 10%/с газ идёт в пустое место до 5 ppm. Порог удаления вдвое меньше, `1e-5f` |
| Порог создания ячейки для пара с насыщением (`createCellMoles`) | `0,01 × насыщение` | моль | Ртуть: `6.6e-6f` (0,16 ppm). Порог удаления вдвое меньше: `3.3e-6f`. Для газов с `saturationMoles` строго между 0 и `Float.MAX_VALUE`. Следствие: при смешении 10%/с ячейка выпускает в пустое место только от насыщения, то есть фронт ртути уходит дальше насыщенной ячейки на одно кольцо. Значение проекта, не измерено |
| COAL_DUST: единица | 1 кг пыли в ячейке (1 м³) | кг | не моль: пыль не вытесняет воздух (2 кг/м³ угля это 0,15% объёма) и не входит в `GasMath.total`, давление и кислород |
| COAL_DUST: молярная масса (`molarMass`) | `1000f` | г на единицу | только для справки: пыль исключена из `GasMath.relativeDensity`. Частица 10-75 мкм падает сама со скоростью от нескольких мм/с до 0,2 м/с, облако не даёт плотностного течения и висит минутами |
| COAL_DUST: насыщение (`saturationMoles`) | `0f` | ед. на ячейку | вся пыль оседает |
| COAL_DUST: скорость оседания (`depositionRate`) | `0.01f` | 1/с | около 70 с до половины. Оседает не в `deposits` чанка, а в `SETTLED_COAL_DUST` той же ячейки (`Gas.settlesInto`). Разносится диффузией и потоком давления, как газ |
| SETTLED_COAL_DUST, SETTLED_ROCK_DUST | `mobile = false`, `particulate = true` | кг | осевшая пыль: симулятор не двигает, атмосфера не выдувает, трубы и вентилятор не берут, не вдыхается |

## pneumatics/gas/Physiology
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Константа Холдейна (`HALDANE`) | `246f` | безразм. | Haldane's constant: haemoglobin binds CO about 246 times more strongly than O2. |
| Доля альвеолярного кислорода (`ALVEOLAR_OXYGEN_SHARE`) | `0.65f` | доля | Парциальное давление O2 в альвеолах около 100 мм рт. ст. против 160 в сухом вдыхаемом воздухе. Равновесие HbCO по Холдейну считается на `кислород × 0,65`: 100 ppm даёт около 15%, 400 ppm около 42% (измерено у курильщиков и рабочих после многих часов) |
| Диапазон запаха H2S (`isNoticeable`) | от `0.05f` до `100f` | ppm | Нос чует от сотых долей ppm, выше около 100 ppm паралич обонятельного нерва: самый тяжёлый воздух не даёт предупреждения |
| Скорость поглощения CO при лёгкой работе (`UPTAKE`) | `0.16f / 3600f` | %COHb/(с·ppm) | Empirical uptake at light work: %COHb per second per ppm. |
| Период полувыведения CO на чистом воздухе (`WASHOUT_HALF_TIME_DAYS`) | `5f / 24f` | сутки (замедленного времени) | Half-time of washout in clean air, in days of slow time: real blood takes about 5 hours. |
| Объём вдоха при лёгкой работе (`BREATHING_RATE`) | `0.00033f` | м³/с | Air breathed at light work, 20 litres per minute, in cubic metres per second. |
| Доля вдыхаемого яда, которая задерживается (`RETAINED_FRACTION`) | `0.8f` | доля | — |
| Порог симптомов отравления CO, HbCO (`COHB_SYMPTOMS`) | `20f` | % COHb | — |
| Порог утраты трудоспособности, HbCO (`COHB_DISABLED`) | `35f` | % COHb | — |
| Смертельный порог HbCO (`COHB_FATAL`) | `50f` | % COHb | — |
| ARSENIC: период полувыведения (`halfLifeDays`) | `4f` | сутки (замедленного времени) | Half-life about 4 days, fatal oral dose about 70-180 mg |
| ARSENIC: симптомы (`symptoms`) | `20f` | мг | Half-life about 4 days, fatal oral dose about 70-180 mg |
| ARSENIC: утрата трудоспособности (`disabled`) | `70f` | мг | Half-life about 4 days, fatal oral dose about 70-180 mg |
| ARSENIC: смертельная доза (`fatal`) | `150f` | мг | Half-life about 4 days, fatal oral dose about 70-180 mg |
| MERCURY: период полувыведения (`halfLifeDays`) | `60f` | сутки (замедленного времени) | Half-life about 60 days, chronic tremor builds slowly |
| MERCURY: симптомы (`symptoms`) | `5f` | мг | Half-life about 60 days, chronic tremor builds slowly |
| MERCURY: утрата трудоспособности (`disabled`) | `20f` | мг | Half-life about 60 days, chronic tremor builds slowly |
| MERCURY: смертельная доза (`fatal`) | `50f` | мг | Half-life about 60 days, chronic tremor builds slowly |
| ZINC_OXIDE: период полувыведения (`halfLifeDays`) | `0.25f` | сутки (замедленного времени) | Metal fume fever: awful, passes within hours, not fatal |
| ZINC_OXIDE: симптомы (`symptoms`) | `15f` | мг | Metal fume fever: awful, passes within hours, not fatal |
| ZINC_OXIDE: утрата трудоспособности (`disabled`) | `60f` | мг | Metal fume fever: awful, passes within hours, not fatal |
| ZINC_OXIDE: смертельная доза (`fatal`) | `Float.MAX_VALUE` | мг | Metal fume fever: awful, passes within hours, not fatal |
| LEAD: период полувыведения (`halfLifeDays`) | `30f` | сутки (замедленного времени) | Half-life in blood about 30 days |
| LEAD: симптомы (`symptoms`) | `30f` | мг | Half-life in blood about 30 days |
| LEAD: утрата трудоспособности (`disabled`) | `100f` | мг | Half-life in blood about 30 days |
| LEAD: смертельная доза (`fatal`) | `300f` | мг | Half-life in blood about 30 days |

## pneumatics/gas/Flammability
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| COAL_DUST: нижний предел | `0.05f / GasMath.CELL_MOLES` | доля ячейки (50 г/м³ = 0,05 ед.) | Bureau of Mines: угольная пыль, нижний предел взрываемости около 50 г/м³ |
| COAL_DUST: верхний предел | `2f / GasMath.CELL_MOLES` | доля ячейки (2 кг/м³ = 2 ед.) | по проекту v1.0 |
| COAL_DUST: минимум кислорода | `0.12f` | доля | по проекту v1.0, как у метана |
| Допуск на предел (`LIMIT_TOLERANCE`) | `1e-5f` | относительная | смесь точно на пределе (два горючих по половине своих) не должна пропадать из-за округления float |
| Удалено: минимум горючего в молях (`MINIMUM_FUEL_MOLES`) | было `0.04f * GasMath.CELL_MOLES` | моль | для газов следовало из суммы Ле Шателье (нижний предел не ниже 4%), для пыли в молях не имеет смысла. Требование кислорода теперь взвешено долей слагаемого суммы Ле Шателье, а не молями |

## pneumatics/gas/Deflagration
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Прирост давления на кДж тепла (`PRESSURE_KPA_PER_KJ`) | `0.25f` | кПа/кДж | gamma - 1 for hot combustion gas, with heat in kJ and volume in cubic metres. |
| Порог, ниже которого взрыв не действует (`MINIMUM_KPA`) | `3f` | кПа | the weakest windows break from about 3 kPa |
| Эффективный объём ячейки под открытым небом (`OPEN_SKY_VOLUME`) | `16f` | м³ | A shell open to the sky relieves far more pressure than its volume suggests, as the gas can leave upward. |
| Доля углерода, остающаяся CO у края пламени (`FROZEN_CARBON_MONOXIDE`) | `0.02f` | доля углерода | Even a lean flame quenches at its edges, leaving about this share of its carbon as CO. |
| Теплота CH4 до CO (`METHANE_TO_CO_KJ`) | `519f` | кДж/моль | — |
| Теплота CO до CO2 (`CO_TO_CO2_KJ`) | `283f` | кДж/моль | — |
| Теплота водорода (`HYDROGEN_KJ`) | `242f` | кДж/моль | — |
| Теплота H2S (`HYDROGEN_SULFIDE_KJ`) | `518f` | кДж/моль | — |
| Теплота NH3 (`AMMONIA_KJ`) | `317f` | кДж/моль | — |
| Теплота на моль сгоревшего углерода пыли (`DUST_KJ_PER_CARBON_MOLE`) | `0.5 * (110.5 + 393.5) = 252f` | кДж/моль C | половина углерода в CO, половина в CO2: сумма того, что на самом деле образуется. Это 20,9 кДж на 1 г углерода (поправка v1.0: 30 кДж/г предполагал полное сгорание) |
| Углерод в пыли (`CARBON_PER_DUST_UNIT`) | `83f` | моль C на кг | 1000 г / 12,011 г/моль |
| Кислород на моль углерода пыли (`OXYGEN_PER_DUST_CARBON`) | `0.75f` | моль O2 на моль C | половина углерода в CO (0,5 O2), половина в CO2 (1 O2) |
| Доля угля в осадке, выше которой взрыв поднимает пыль (`COAL_SHARE_THAT_RISES`) | `0.35f` | доля массы | осланцевание: не менее 65% негорючей пыли, ровно 65% хватает. Поднимается при `доля > 0,35 + допуск` |
| Допуск доли угля (`COAL_SHARE_TOLERANCE`) | `1e-4f` | доля | смесь, замешанная ровно как 35/65, не должна подниматься из-за округления float |
| Давление закрытого объёма пыли | 732 кПа при 150-1900 г/м³ | кПа | проверка в `CoalDustTest`: измеренный Pmax угольной пыли 6-8 бар. Модель ограничена кислородом, на богатой стороне давление не падает |

## pneumatics/gas/Combustion
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Теплота горения C до CO2 (`CO2_HEAT_KJ_PER_MOL`) | `393.5f` | кДж/моль | C + O2 -> CO2 releases 393.5 kJ/mol. |
| Теплота горения C до CO (`CO_HEAT_KJ_PER_MOL`) | `110.5f` | кДж/моль | C + 1/2 O2 -> CO releases 110.5 kJ/mol. |
| Азот воздуха на моль O2 (`NITROGEN_PER_OXYGEN`) | `(1 - GasMath.OXYGEN_IN_AIR) / GasMath.OXYGEN_IN_AIR` | моль/моль | Air carries 3.77 mol of nitrogen per mole of oxygen. |
| Доля O2, ниже которой горение перестаёт быть чистым (`CLEAN_BURN_OXYGEN`) | `0.19f` | доля | Below this oxygen fraction carbon stops burning cleanly. |
| Доля O2, ниже которой угли не горят (`EXTINCTION_OXYGEN`) | `0.12f` | доля | Below this oxygen fraction embers can no longer sustain a fire. |
| Доля углерода домны, уходящая как CO (`BLAST_FURNACE_CARBON_MONOXIDE_SHARE`) | `0.75f` | доля | Колошниковый газ реальной домны 20-28% CO по объёму; с этой долей и азотом воздуха выходит около 22%. Прежнее значение 0,40 давало около 10% |
| Возврат огня без кислорода (`sustainsFlame`) | `кислород ≥ EXTINCTION_OXYGEN` | доля | Ниже 12% O2 ничего не выбрасывает ни один вид огня, в том числе те, что нельзя погасить (сыродутная печь, топка, ямная печь, угольная яма): их топливо тлеет дальше, но в этот воздух дыма не даёт |

## metallurgy/Sulfide
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| TETRAHEDRITE: плотность металла (`density`) | `8.96f` | г/см³ | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| TETRAHEDRITE: молярная масса металла (`molarMass`) | `63.55f` | г/моль | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| TETRAHEDRITE: сера (`sulfur`) | `13f / 12f` | моль на моль металла | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| TETRAHEDRITE: мышьяк (`arsenic`) | `2f / 12f` | моль на моль металла | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| TETRAHEDRITE: сурьма (`antimony`) | `2f / 12f` | моль на моль металла | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| TETRAHEDRITE: ртуть (`mercury`) | `0f` | моль на моль металла | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| TETRAHEDRITE: кислород оксида (`oxideOxygen`) | `0f` | моль на моль металла | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| TETRAHEDRITE: свинцовый дым (`leadFume`) | `0f` | моль на моль металла | Cu12(As,Sb)4S13. The arsenic and antimony sites are taken as half each, as natural tetrahedrite ranges between the two. |
| SPHALERITE: плотность металла (`density`) | `7.14f` | г/см³ | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| SPHALERITE: молярная масса металла (`molarMass`) | `65.38f` | г/моль | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| SPHALERITE: сера (`sulfur`) | `1f` | моль на моль металла | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| SPHALERITE: мышьяк (`arsenic`) | `0f` | моль на моль металла | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| SPHALERITE: сурьма (`antimony`) | `0f` | моль на моль металла | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| SPHALERITE: ртуть (`mercury`) | `0f` | моль на моль металла | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| SPHALERITE: кислород оксида (`oxideOxygen`) | `0.5f` | моль на моль металла | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| SPHALERITE: свинцовый дым (`leadFume`) | `0f` | моль на моль металла | 2 ZnS + 3 O2 -> 2 ZnO + 2 SO2 |
| BISMUTHINITE: плотность металла (`density`) | `9.78f` | г/см³ | Bi2S3 burning to bismuth and SO2 |
| BISMUTHINITE: молярная масса металла (`molarMass`) | `208.98f` | г/моль | Bi2S3 burning to bismuth and SO2 |
| BISMUTHINITE: сера (`sulfur`) | `1.5f` | моль на моль металла | Bi2S3 burning to bismuth and SO2 |
| BISMUTHINITE: мышьяк (`arsenic`) | `0f` | моль на моль металла | Bi2S3 burning to bismuth and SO2 |
| BISMUTHINITE: сурьма (`antimony`) | `0f` | моль на моль металла | Bi2S3 burning to bismuth and SO2 |
| BISMUTHINITE: ртуть (`mercury`) | `0f` | моль на моль металла | Bi2S3 burning to bismuth and SO2 |
| BISMUTHINITE: кислород оксида (`oxideOxygen`) | `0f` | моль на моль металла | Bi2S3 burning to bismuth and SO2 |
| BISMUTHINITE: свинцовый дым (`leadFume`) | `0f` | моль на моль металла | Bi2S3 burning to bismuth and SO2 |
| CINNABAR: плотность металла (`density`) | `13.53f` | г/см³ | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| CINNABAR: молярная масса металла (`molarMass`) | `200.59f` | г/моль | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| CINNABAR: сера (`sulfur`) | `1f` | моль на моль металла | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| CINNABAR: мышьяк (`arsenic`) | `0f` | моль на моль металла | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| CINNABAR: сурьма (`antimony`) | `0f` | моль на моль металла | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| CINNABAR: ртуть (`mercury`) | `1f` | моль на моль металла | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| CINNABAR: кислород оксида (`oxideOxygen`) | `0f` | моль на моль металла | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| CINNABAR: свинцовый дым (`leadFume`) | `0f` | моль на моль металла | HgS + O2 -> Hg + SO2. Mercury boils at 357 C, so in an open furnace all of it goes up as vapour. |
| GALENA: плотность металла (`density`) | `11.34f` | г/см³ | a hearth lost a few percent of it as fume |
| GALENA: молярная масса металла (`molarMass`) | `207.2f` | г/моль | a hearth lost a few percent of it as fume |
| GALENA: сера (`sulfur`) | `1f` | моль на моль металла | a hearth lost a few percent of it as fume |
| GALENA: мышьяк (`arsenic`) | `0f` | моль на моль металла | a hearth lost a few percent of it as fume |
| GALENA: сурьма (`antimony`) | `0f` | моль на моль металла | a hearth lost a few percent of it as fume |
| GALENA: ртуть (`mercury`) | `0f` | моль на моль металла | a hearth lost a few percent of it as fume |
| GALENA: кислород оксида (`oxideOxygen`) | `0f` | моль на моль металла | a hearth lost a few percent of it as fume |
| GALENA: свинцовый дым (`leadFume`) | `0.03f` | моль на моль металла | a hearth lost a few percent of it as fume |

## metallurgy/MetalHeat
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Джоули на единицу тепла TFC (`JOULES_PER_UNIT`) | `121f` | Дж на ед. тепла TFC | The joules in TFC's unit of heat that makes the audit's numbers agree |
| Объём слитка (`INGOT_MILLILITRES`) | `100f` | мл | — |
| Миллибакетов в слитке (`MILLIBUCKETS_PER_INGOT`) | `100f` | мБ | — |
| Комнатная температура (`ROOM_TEMPERATURE`) | `20f` | °C | — |
| Доля ёмкости жидкого металла от твёрдого (`LIQUID_SHARE`) | `0.3f` | доля | TFC's own ratio of a molten ingot's capacity to a solid one. |
| Плотность стали (`STEEL_DENSITY`) | `7.85f` | г/см³ | — |
| Удельная теплоёмкость стали (`STEEL_SPECIFIC_HEAT`) | `0.49f` | Дж/(г·К) | — |
| Скрытая теплота плавления стали (`STEEL_LATENT_HEAT`) | `250f` | Дж/г | — |
| Фосфористый чугун (`PHOSPHORIC_PIG_IRON`) | плотность 7,2 г/см³, 0,46 Дж/(г·К), скрытая 200 Дж/г, плавление 1200 °C | — | плавится как чугун: температуру задаёт углерод |
| Хладноломкое железо (`COLD_SHORT_IRON`) | плотность 7,87 г/см³, 0,449 Дж/(г·К), скрытая 247 Дж/г, плавление 1535 °C | — | плавится как сварочное железо: углерода в нём почти нет |

## metallurgy/IronSmelting
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Железа в куске шлака (`MILLIBUCKETS_PER_SLAG`) | `25` | мБ | Iron carried by a piece of bloomery slag. |
| Железа в крице (`BLOOM_MILLIBUCKETS`) | `100` | мБ | What a bloom holds, as the rest of TFC expects when it is forged or melted. |
| Моль железа на мБ (`MOLES_OF_IRON_PER_MILLIBUCKET`) | `7.87f / 55.845f` | моль/мБ | — |
| CO2 на моль железа при восстановлении (`CARBON_DIOXIDE_PER_IRON`) | `1.5f` | моль CO2 на моль Fe | Fe2O3 + 3 CO -> 2 Fe + 3 CO2: one and a half moles of CO2 for every mole of iron. |
| CO2 от одной порции флюса (`CARBON_DIOXIDE_PER_FLUX`) | `7.5f` | моль CO2 на порцию флюса | A flux powder of 0.75 kg of limestone: CaCO3 -> CaO + CO2. |
| CO2 от одной порции доломита (`CARBON_DIOXIDE_PER_DOLOMITE`) | `8.1f` | моль CO2 на порцию доломита | A dolomite powder of 0.75 kg: CaMg(CO3)2 -> CaO + MgO + 2 CO2, 184 g/mol. |

## metallurgy/ConverterBath
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| WROUGHT_IRON: верхний предел углерода (`upTo`) | `0.2f` | % мас. | the point in weight percent where each kind ends |
| WROUGHT_IRON: углерод (`carbon`) | `0.03f` | % мас. | each kind counts as the least it can hold |
| WROUGHT_IRON: кремний (`silicon`) | `0f` | % мас. | each kind counts as the least it can hold |
| STEEL: верхний предел углерода (`upTo`) | `1.0f` | % мас. | the point in weight percent where each kind ends |
| STEEL: углерод (`carbon`) | `0.2f` | % мас. | each kind counts as the least it can hold |
| STEEL: кремний (`silicon`) | `0f` | % мас. | each kind counts as the least it can hold |
| HIGH_CARBON_STEEL: верхний предел углерода (`upTo`) | `2.1f` | % мас. | the point in weight percent where each kind ends |
| HIGH_CARBON_STEEL: углерод (`carbon`) | `1.0f` | % мас. | each kind counts as the least it can hold |
| HIGH_CARBON_STEEL: кремний (`silicon`) | `0f` | % мас. | each kind counts as the least it can hold |
| CAST_IRON: верхний предел углерода (`upTo`) | `4.0f` | % мас. | the point in weight percent where each kind ends |
| CAST_IRON: углерод (`carbon`) | `2.1f` | % мас. | each kind counts as the least it can hold |
| CAST_IRON: кремний (`silicon`) | `0f` | % мас. | each kind counts as the least it can hold |
| PIG_IRON: верхний предел углерода (`upTo`) | `Float.MAX_VALUE` | % мас. | the point in weight percent where each kind ends |
| PIG_IRON: углерод (`carbon`) | `4.0f` | % мас. | each kind counts as the least it can hold |
| PIG_IRON: кремний (`silicon`) | `1.0f` | % мас. | each kind counts as the least it can hold |
| PHOSPHORIC_PIG_IRON: верхний предел углерода (`upTo`) | `Float.MAX_VALUE` | % мас. | не выбирается по углероду, только по жидкости |
| PHOSPHORIC_PIG_IRON: углерод (`carbon`) | `4.0f` | % мас. | — |
| PHOSPHORIC_PIG_IRON: кремний (`silicon`) | `1.0f` | % мас. | — |
| PHOSPHORIC_PIG_IRON: фосфор (`phosphorus`) | `1.5f` | % мас. | бурые железняки 0,5–2% P |
| COLD_SHORT_IRON: верхний предел углерода (`upTo`) | `0.2f` | % мас. | не более 0,2% C в выпуске |
| COLD_SHORT_IRON: углерод (`carbon`) | `0.03f` | % мас. | считается наименьшим, чтобы переливка не прибавляла углерод |
| COLD_SHORT_IRON: фосфор (`phosphorus`) | `1.5f` | % мас. | — |
| Молярная масса фосфора (`GRAMS_PER_MOL_PHOSPHORUS`) | `30.974f` | г/моль | — |
| Теплота окисления фосфора (`PHOSPHORUS_HEAT`) | `745f` | кДж/моль P | 2 P + 5/2 O2 -> P2O5 с переходом в 4CaO·P2O5, на моль фосфора |
| Кислорода на моль фосфора (`OXYGEN_PER_PHOSPHORUS`) | `1.25f` | моль O2 на моль P | 2 P + 5/2 O2 -> P2O5 |
| Извести на моль кремния (`LIME_PER_SILICON`) | `2f` | моль CaO на моль Si | 2CaO·SiO2 |
| Извести на моль фосфора (`LIME_PER_PHOSPHORUS`) | `2f` | моль CaO на моль P | 4CaO·P2O5 |
| CaO в порошке извести (`LIME_MOLES_PER_POWDER`) | `7.5f` | моль CaO на порошок | по проекту v1.0 |
| Предел хладноломкости (`PHOSPHORUS_LIMIT_PERCENT`) | `0.1f` | % мас. P | выше него выпуск всегда `cold_short_iron` |
| Углерод, ниже которого горит фосфор (`AFTERBLOW_CARBON_PERCENT`) | `0.06f` | % мас. C | вдвое выше предела `CARBON_FLOOR_PERCENT`: углерод ещё забирает не более десятой доли кислорода |
| Дым P2O5 заметен выше (`SMOKE_PERCENT`) | `0.02f` | % мас. P | порог пламени `AFTERBLOW` |
| Теплоёмкость CaO (`LIME_HEAT_CAPACITY`) | `0.048f` | кДж/(моль·К) | Известь бросают холодной, и нагрев её до температуры ванны это настоящая плата за процесс Томаса |
| Ёмкость конвертера (`MILLIBUCKETS`) | `2000f` | мБ | — |
| Масса 1 мБ железа (`KG_PER_MILLIBUCKET`) | `0.0079f` | кг/мБ | — |
| Температура окружения (`AMBIENT`) | `20f` | °C | — |
| Молярная масса углерода (`GRAMS_PER_MOL_CARBON`) | `12.011f` | г/моль | — |
| Молярная масса кремния (`GRAMS_PER_MOL_SILICON`) | `28.086f` | г/моль | — |
| Молярная масса железа (`GRAMS_PER_MOL_IRON`) | `55.845f` | г/моль | — |
| Теплота окисления кремния (`SILICON_HEAT`) | `911f` | кДж/моль | Heats of Si + O2 -> SiO2, C + 1/2 O2 -> CO and Fe + 1/2 O2 -> FeO, in kJ per mole of the element. |
| Теплота окисления углерода до CO (`CARBON_HEAT`) | `110.5f` | кДж/моль | Heats of Si + O2 -> SiO2, C + 1/2 O2 -> CO and Fe + 1/2 O2 -> FeO, in kJ per mole of the element. |
| Теплота окисления железа до FeO (`IRON_HEAT`) | `272f` | кДж/моль | Heats of Si + O2 -> SiO2, C + 1/2 O2 -> CO and Fe + 1/2 O2 -> FeO, in kJ per mole of the element. |
| Порог углерода, ниже которого воздух чаще встречает железо (`CARBON_RACE_PERCENT`) | `0.3f` | % мас. C | Below this much carbon, a bubble of air meets iron more often than it meets carbon. |
| Минимальный углерод, который отнимает воздух (`CARBON_FLOOR_PERCENT`) | `0.03f` | % мас. C | The least carbon air can take out of iron, where it stands in balance with the oxygen dissolved in the iron. |
| Железа выброшено на моль сгоревшего углерода (`IRON_THROWN_PER_CARBON`) | `0.3f` | моль Fe на моль C | Iron blown out of the mouth in sparks and fume, for each mole of carbon burned. |
| Молярная теплоёмкость уходящих газов (`GAS_HEAT_CAPACITY`) | `0.030f` | кДж/(моль·К) | Molar heat of the nitrogen and CO that leave, in kJ/mol/K. |
| Удельная теплоёмкость железа (`IRON_HEAT_CAPACITY`) | `0.82f` | кДж/(кг·К) | — |
| Теплопотеря на кельвин (`LOSS_KILOWATTS_PER_KELVIN`) | `0.002f` | кВт/К | A small vessel with its mouth open, far larger against its charge than a five tonne one: 3 kW at the temperature of steel. |
| Теплоёмкость футеровки (`LINING_HEAT_CAPACITY`) | `5f` | кДж/К | — |
| Ликвидус чистого железа (`LIQUIDUS_PURE_IRON`) | `1538f` | °C | — |
| Наклон ликвидуса по углероду (`LIQUIDUS_PER_CARBON_PERCENT`) | `91f` | °C на % мас. C | The iron-carbon liquidus, from 1538 C for pure iron to the eutectic of 1147 C at 4.3% carbon, which is nearly straight. |
| Эвтектика Fe-C (`EUTECTIC`) | `1147f` | °C | — |

## metallurgy/ConverterBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Основных кирпичей на футеровку (`BASIC_BRICKS_PER_LINING`) | `8` | шт | столько же, сколько шамотных: футеровка того же размера |
| Износ от моля кремнезёма без извести (`SILICA_WEAR_PER_MOLE`) | `60f` | с футеровки на моль | кремнезём может разъесть только основную футеровку |
| Фосфор в куске томасовского шлака (`PHOSPHORUS_PER_THOMAS_PIECE`) | `2f` | моль P | одна формульная единица 4CaO·P2O5 |

## metallurgy/MetallurgyData
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Обжиг доломита (`BURNT_DOLOMITE_TEMPERATURE`) | `1500f` | °C | CaMg(CO3)2 -> CaO·MgO + 2 CO2: до спекания, не гасится |
| Фосфор в томасовской муке (`THOMAS_MEAL_PHOSPHORUS`) | `0.15f` | доля, шкала удобрений TFC | основной шлак содержит 15-20% P2O5 |
| Томасовской муки из куска шлака | `4` | шт | кусок шлака 2 моль P (4CaO·P2O5, 366 г), порошок 50 г: даже меньше, чем по массе |
| Порошка доломита из булыжника | `2` | шт | по образцу TFC для флюса |
| Глёт: углерод на порошок (`LITHARGE_CARBON`) | `0.125f` | моль C на порошок | 2 PbO + C -> 2 Pb + CO2: порошок 0,25 моль PbO, значит 0,125 моль C и 0,125 моль CO2 |
| Свинец из порошка глёта (`LITHARGE_LEAD_MILLIBUCKETS`) | `4` | мБ | Lead in one powder of litharge is 4.57 mL, and a tank holds whole millibuckets, so 4 mB; 0,57 мл округлением теряются |
| Температура восстановления глёта (`LITHARGE_REDUCTION_TEMPERATURE`) | `800f` | °C | Выше плавления свинца (327 C), ниже кипения (1749 C). Цинку нужно 1000 C |
| Температура обжига костной муки (`BONE_ASH_TEMPERATURE`) | `700f` | °C | Bone meal burns to bone ash once its organic matter is gone |
| Теплоёмкость костной муки (`item_heat`, `heat_capacity`) | `0.7f` | шкала TFC | Как у молотого известняка и остальных порошков TFC |
| Объём свинцового листа | `100` | мБ | Лист равен одному слитку (100 мБ), а не двойному слитку TFC (200 мБ): иначе рецепт из одного слитка создавал бы металл |
| Температура ковки свинцового листа (`forging_temperature`) | `0f` | °C | Lead recrystallises at room temperature, so it has no forging temperature to meet; 0 means any heat will do |

## metallurgy/RetortBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Ёмкость бака (`TANK_MILLIBUCKETS`) | `1000` | мБ | — |
| Моль углерода в куске древесного угля (`CARBON_PER_CHARCOAL`) | `200f` | моль C на кусок угля | Moles of carbon in a 3 kg piece of charcoal, on the same scale as the rest of the mod. |
| Ртуть в ампуле (`MERCURY_BULB_MILLIBUCKETS`) | `5` | мБ | — |
| Миллибакетов в слитке (`MILLIBUCKETS_PER_INGOT`) | `100` | мБ | — |

## pneumatics/instruments/DustStore
Общее хранилище пыли перегородки и конденсационной башни.

| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Моль на предмет порошка (`MOLES_PER_POWDER`) | `0.25f` | моль на предмет | One item of powder, about 50 g of arsenic trioxide. |
| Доля собранной пыли, поднимаемая обратно при сборе (`STIRRED_UP`) | `0.05f` | доля | Share of what is scraped off that goes up into the air as well. |

## pneumatics/instruments/SettlingBaffleBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Доля газа, осаждаемая за такт в секунду (`CAPTURE_PER_SECOND`) | `0.05f` | 1/с | — |

## metallurgy/TowerCondensation
Чистая физика конденсационной башни. Доля захвата за шаг `1 - exp(-CAPTURE_RATE * t)`, поэтому длина шага ничего не меняет. N башен в одной сети ловят в N раз быстрее (каждая берёт свою долю у того, что оставила прошлая).

| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Скорость захвата (`CAPTURE_RATE`) | `0.05f` | 1/с | The same share as a baffle takes: 5% a second of the dust, or of the mercury vapour, in the gas. |
| Потолок оседания оксидной пыли (`DUST_CEILING_KELVIN`) | `523f` | К | 250 C. Above it the oxide fume of As, Sb, Zn and Pb stays in the gas. |
| Потолок конденсации ртути (`MERCURY_CEILING_KELVIN`) | `373f` | К | 100 C. Above it mercury stays a vapour (it boils at 357 C, and a gas that holds it is cooled far below that to condense it). |
| Бак ртути (`MERCURY_TANK_MILLIBUCKETS`) | `100` | мБ | The mercury a tower holds before the rest stays in the gas. Игровой размер. |

## metallurgy/CondensationTowerBlock
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Объём (`VOLUME`) | `1f` | м³ | A chamber a metre across, and open. |
| Теплопотери (`HEAT_LOSS`) | `0.012f` | кВт/К | Six faces of a metre of brick at about 2 Вт/(м²·К). Труба теряет 0,005, сосуд высокого давления 0,03. Решение не из проекта. |

## metallurgy/CondensationTowerBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Моль ртути в мБ (`MOLES_PER_MILLIBUCKET`) | `Sulfide.CINNABAR.metalMoles(1f)` | моль на мБ | 13,53 г/см³ над 200,59 г/моль, около 0,0675. 1 мБ металла = 1 мл. |
| Порог пометки чанка (`CHANGE_TO_SAVE`) | `1e-3f` | моль | A change in the dust smaller than this is not worth saving the chunk for. |

## chemistry/CarbonizationTable
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Час игрового процесса (`HOUR`) | `LocalTime.SECONDS_PER_DAY / 24f` | с | — |
| Масса куска угля, лигнита и кокса (`COAL_PIECE_KILOGRAMS`) | `3.0` | кг | A piece of coal, lignite or coke weighs 3 kg, a log 5 kg |
| Масса полена (`LOG_KILOGRAMS`) | `5.0` | кг | A piece of coal, lignite or coke weighs 3 kg, a log 5 kg |

## chemistry/FuelSulfur
В файле нет именованных `float`/`double`/`int` констант и enum-аргументов. Значения серы в `SULFUR_DIOXIDE_MOLES` (`Map.of`) в реестр не внесены.

## chemistry/BurnerMath
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Максимальная мощность горелки (`MAX_KILOWATTS`) | `400f` | кВт | The most heat it can give, which is what the nozzle passes. |
| Температура пламени коксового газа (`FULL_TEMPERATURE`) | `1100f` | °C | Flame temperature of coke oven gas burning in air with a little excess, in Celsius. |
| Теплота смеси для полной температуры пламени (`FULL_HEAT_KILOJOULES_PER_MOLE`) | `150f` | кДж/моль | A mixture this rich in heat gives the full temperature. |
| Мощность пламени для полного нагрева (`FULL_HEAT_KILOWATTS`) | `200f` | кВт | A flame this strong holds what it heats at the full flame temperature. |
| Минимальное избыточное давление газа у горелки (`MINIMUM_GAUGE_ATM`) | `0.005f` | атм | Town gas was supplied at about 500 Pa. |
| Предел давления взрыва в трубе (`MAXIMUM_EXPLOSION_KILOPASCALS`) | `800f` | кПа | Peak pressure of a confined explosion of any of these gases in air is close to 8 bar. |
| Температура окружения (`AMBIENT`) | `20f` | °C | — |
| Доля CO в хорошо отрегулированной горелке (`CLEAN_MONOXIDE_SHARE`) | `0.01f` | доля | A well-adjusted burner leaves hardly any carbon monoxide |
| Показатель адиабаты горячих газов (`GAMMA`) | `1.4f` | безразм. | — |
| Низшая теплота сгорания водорода (`HYDROGEN_HEAT`) | `242f` | кДж/моль | Lower heating values in kJ per mole, with the water leaving as steam. |
| Низшая теплота сгорания метана (`METHANE_HEAT`) | `802f` | кДж/моль | Lower heating values in kJ per mole, with the water leaving as steam. |
| Низшая теплота сгорания CO (`MONOXIDE_HEAT`) | `283f` | кДж/моль | Lower heating values in kJ per mole, with the water leaving as steam. |
| Низшая теплота сгорания H2S (`SULFIDE_HEAT`) | `518f` | кДж/моль | Lower heating values in kJ per mole, with the water leaving as steam. |
| Низшая теплота сгорания NH3 (`AMMONIA_HEAT`) | `317f` | кДж/моль | Lower heating values in kJ per mole, with the water leaving as steam. |

## pneumatics/gas/GasMath (обмен с атмосферой)
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Обмен у поверхности (`OPEN_AIR_EXCHANGE_RATE`) | `0.3f` | доля газа в секунду | Доля газа, которую ветер уносит каждую секунду у устья открытого столба, то есть на уровне земли |
| Масштаб глубины (`OPEN_AIR_DEPTH_SCALE`) | `3f` | м | Обмен ячейки в открытом столбе равен `0,3 · exp(-глубина / 3)`: на 3 м в e раз меньше, на 12 м 0,0055 1/с. Глубина считается от земли вокруг: максимум карты высот (`MOTION_BLOCKING_NO_LEAVES`) своего и четырёх соседних столбцов минус высота ячейки. Колодец и шурф держат тяжёлый газ, как пещера. Крыша над ячейкой даёт 0 |

## pneumatics/gas/GasSimulator (покой чанка)
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Порог изменения (`CHANGE_THRESHOLD_MOLES`) | `1e-6f` | моль (кг для пыли) за шаг | Меньше этого за шаг чанк не помечается к сохранению и, если в нём только газ, засыпает. Это 0,024 ppm ячейки. Выше шага округления float у ячейки с атмосферой газа: 40 моль имеют шаг 3,8е-6, обмен на 24% от него ещё ниже порога. Считается наибольшее изменение любого вида: перенос между ячейками, унос атмосферой, оседание, испарение плёнки. Мелкие шаги копятся (`GasChunkData.accumulateChange`), пока сумма не дойдёт до порога; при засыпании чанк один раз помечается, если что-то накопилось |
| Перепроверка спящего газа (`RESTING_RECHECK_TICKS`) | `200` | тиков календаря TFC | Дверь открыли или стену сломали, а газ об этом никто не сообщил: чанк со спящим газом раз в 10 с делает один шаг и, если ничего не меняется, засыпает снова. Горючий газ (по Ле Шателье, с пылью) не засыпает: пламя рядом должно быть замечено |
| Порог пробуждения соседа (`differsFrom`) | разность × 0,1 ≥ `CHANGE_THRESHOLD_MOLES` | моль | Ячейка с газом будит чанк-соседа через границу (и помечает его к сохранению) только если обмен с соседней ячейкой сдвинул бы не меньше порога |

## pneumatics/gas/GasEmission
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Доля, проходящая сквозь слишком толстое покрытие (`LEAK_SHARE`) | `0.1f` | доля | Дёрн угольной ямы не герметичен. Покрытие в два слоя и толще: 10% газа выходит в первой открытой ячейке над ним в пределах `MAX_CHARGE_HEIGHT` (16), остальное остаётся (у `release`) или не учитывается (у огня). Если открытой ячейки в пределах досягаемости нет, не выходит ничего. Значение проекта, не измерено |

## mechanics/MineFanBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Досягаемость вентилятора (`REACH_CELLS`) | `512` | ячеек | Вентилятор двигает газ, только если обход дошёл (кроме пути выхода) до ячейки под открытым небом, или пространство больше досягаемости (принимается за связанное с воздухом). Закрытая комната или тупик не проветриваются: нужен второй ход (закон о двух стволах появился после Хартли, 1862). Обход остаётся в прежнем бюджете `WalkTurns` |

## pneumatics/instruments/DavyLampBlock
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Проверка воздуха у горящей лампы (`CHECK_TICKS`) | `40` | тиков | Погасшая в чёрном роднике или богатом рудничном газе лампа остаётся погасшей (`LIT` в блоке, компонент `tfcpp:lamp_lit` в предмете) и зажигается снова только огнивом из тега `tfcpp:lights_gas_burner`, другой рукой или TFC у стоящей. Условие гашения то же, что чтение `OUT` (`DavyLamp.burns`): кислород ниже 16% или метановый эквивалент выше 15% |

## geology/SeamGas
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| BITUMINOUS: объём Ленгмюра (`langmuirVolume`) | `25f` | м³/т | Langmuir volume about 25 m3/t, Langmuir pressure about 2 MPa, for high volatile bituminous coal. Its gas is nearly all methane. |
| BITUMINOUS: давление Ленгмюра (`langmuirPressure`) | `2f` | МПа | Langmuir volume about 25 m3/t, Langmuir pressure about 2 MPa, for high volatile bituminous coal. Its gas is nearly all methane. |
| BITUMINOUS: доля метана в газе (`methaneShare`) | `0.95f` | доля | Langmuir volume about 25 m3/t, Langmuir pressure about 2 MPa, for high volatile bituminous coal. Its gas is nearly all methane. |
| LIGNITE: объём Ленгмюра (`langmuirVolume`) | `6f` | м³/т | Low rank coal holds little gas, and that is mostly CO2. |
| LIGNITE: давление Ленгмюра (`langmuirPressure`) | `2f` | МПа | Low rank coal holds little gas, and that is mostly CO2. |
| LIGNITE: доля метана в газе (`methaneShare`) | `0.2f` | доля | Low rank coal holds little gas, and that is mostly CO2. |
| Масса угля в блоке (`COAL_TONNES_PER_BLOCK`) | `1.3f` | т | — |
| Гидростатическое давление в пласте (`MEGAPASCALS_PER_METRE`) | `0.01f` | МПа/м | Hydrostatic pressure in the seam, about 10 kPa per metre of depth. |
| Доля быстро выходящего газа (`FAST_FRACTION`) | `0.1f` | доля | — |
| Порог моль, ниже которого открытая грань выделилась полностью (`FACE_DONE_MOLES`) | `0.01f` | моль | — |
| Частота карманов в 4×4×4 областях (`POCKET_RARITY`) | `64` | безразм. (1 из N) | One in this many 4 x 4 x 4 regions of rock next to a seam holds a pocket. |
| Минимальный объём кармана (`MIN_POCKET_VOLUME`) | `4f` | м³ | — |
| Максимальный объём кармана (`MAX_POCKET_VOLUME`) | `16f` | м³ | — |
| Глубина, с которой воздух достигает угля (`BLACKDAMP_DEPTH`) | `15` | м | Air has reached the coal down to about this depth, so what collects there is the product of oxidation and not methane.  Выше этой глубины адсорбированный газ пласта (быстрая доля при добыче и медленная с открытой грани) выходит тем же чёрным рудничным газом в тех же долях, что и в карманах (13% CO2, остальное азот), а не газом ранга; с 15 м и глубже газ ранга. |
| Доля CO2 в чёрном роднике (`BLACKDAMP_CARBON_DIOXIDE`) | `0.13f` | доля | Blackdamp as measured in old workings: air with its oxygen burned out by coal, leaving nitrogen and some CO2. |
| Вероятность искры при ударе стальным инструментом (`SPARK_CHANCE`) | `0.05f` | доля | — |

## core/LocalTime
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Сутки медленных процессов (`SECONDS_PER_DAY`) | `24f * 60f` | с | A day of slow processes, in real seconds. |
| Ускорение медленных процессов (`TIME_SCALE`) | `60f` | с на реальную с | How much faster than reality slow processes run: 60 seconds of them pass in each real second. |

## pneumatics/gas/DustSources
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Пыль в воздух при добыче угольного блока (`MINED_AIRBORNE`) | `0.005f` | кг | по проекту v1.0: 5 г. Только в ячейку сломанного блока и не под открытым небом |
| Осевшая пыль при добыче угольного блока (`MINED_SETTLED`) | `0.05f` | кг | по проекту v1.0: 50 г поровну на ячейку блока и открытые соседние. Доля ячейки под открытым небом уносится ветром |
| Пыль в воздух за помол на жёрнове (`GROUND_AIRBORNE`) | `0.002f` | кг | по проекту v1.0: 2 г за помол древесного угля, кокса, любого `#minecraft:coals` |
| Масса порошка камня для осланцевания (`KILOGRAMS_PER_STONE_DUST_POWDER`) | `0.75f` | кг | порошок флюса 0,75 кг (см. шкалы), в каменную пыль без потерь |
| Радиус осланцевания (`STONE_DUST_RADIUS`) | `2` | блока | открытые ячейки, достижимые от грани через открытые ячейки, в кубе радиуса 2 |
| Норма негорючего в пробе (`StoneDusting.REQUIRED_INCOMBUSTIBLE`) | `0.65f` | доля массы | норма шахт; только для текста сообщения игроку |

## metallurgy/CupellationBath
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Скорость окисления свинца (`OXIDATION_MILLIBUCKETS_PER_SECOND`) | `1f` | мБ свинца в секунду | A game pace, not measured: at this rate a full charge of 1000 mB takes about 17 minutes. |
| Минимальная температура ванны (`LEAST_TEMPERATURE`) | `960f` | °C | Litharge melts at 888 C and must stay liquid to run off the floor, so the hearth is held above that with a working margin. Below it the blast does nothing. |
| Доля свинца, уходящая в дым (`FUME_SHARE`) | `0.03f` | доля окисленного свинца | Share of the oxidised lead that leaves as fume rather than as litharge. A hearth lost a few percent of its lead as fume, as in the galena entry of the ledger. |
| Серебро на мБ свинца (`SILVER_PER_LEAD`) | `0.00324f` | мБ Ag на мБ Pb | 0.3% of the lead's mass, with densities of 11.34 g/cm3 for lead and 10.49 g/cm3 for silver, so 0.003 x 11.34 / 10.49 = 0.00324 mL of silver for each mL of lead. |
| Моль свинца на мБ (`MOLES_LEAD_PER_MILLIBUCKET`) | `11.34f / 207.2f` | моль на мБ | Moles of lead in one millibucket: the density of lead, 11.34 g/cm3, over its molar mass, 207.2 g/mol. One millibucket of metal is one millilitre. |
| Свинец в порошке глёта (`LITHARGE_MILLIBUCKETS_PER_POWDER`) | `4.57f` | мБ Pb на порошок | Lead in one powder of 0.25 mol of PbO: 0.25 x 207.2 g of lead, over 11.34 g/cm3, is 4.57 mL. |
| Свинец, который принимает порошок золы (`ASH_MILLIBUCKETS_PER_POWDER`) | `500f` | мБ Pb на порошок | Lead that one powder of bone ash can serve before it is full. A game figure, not measured. |
| Ёмкость горна (`CAPACITY_MILLIBUCKETS`) | `1000f` | мБ Pb | The most work lead one hearth takes at once. A game size, not a measurement. |
| Кислорода на моль свинца (`OXYGEN_PER_LEAD`) | `0.5f` | моль O2 на моль Pb | 2 Pb + O2 -> 2 PbO, so half a mole of oxygen for each mole of lead. |

## chemistry/ChamberProcess
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Константа скорости (`RATE_CONSTANT`) | `0.26f` | моль/(с·м³) на единицу доли SO2 | Fitted to the output of real plants, not derived from kinetics: 7% SO2 gives about 2.5 kg of acid per m3 per day of 1440 s. |
| Доля оксидов азота, при которой они перестают ограничивать (`NITROGEN_OXIDES_FULL`) | `0.002f` | доля | They are a carrier, not a reagent: a few tenths of a percent is enough. |
| Доля кислорода, при которой он перестаёт ограничивать (`OXYGEN_FULL`) | `0.06f` | доля | Below it the chamber slows. |
| Доля кислорода в воздухе (`OXYGEN_IN_AIR`) | `0.2095f` | доля | — |
| Кислорода на моль кислоты (`OXYGEN_PER_MOLE`) | `0.5f` | моль O2 на моль H2SO4 | SO2 + 1/2 O2 + H2O: half a mole of oxygen for each mole of acid. |
| Вода на моль кислоты (`WATER_LITRES_PER_MOLE`) | `0.071f` | л на моль | 18 g of water goes into the acid and 53 g dilutes it to 65%: 71 g. |
| Объём кислоты на моль (`ACID_LITRES_PER_MOLE`) | `0.0975f` | л на моль | 98 g of H2SO4 at 65% is 151 g, at 1.55 kg/L. |
| Потери оксидов на моль кислоты (`OXIDES_LOST_PER_MOLE`) | `0.01f` | моль на моль | A plant without a Gay-Lussac tower lost about 3 kg of saltpeter per 100 kg of sulfur, about 0.01 mol per mol S. |
| Теплота на моль кислоты (`HEAT_KILOJOULES_PER_MOLE`) | `230f` | кДж/моль | SO2 to SO3 gives 99 kJ; SO3 taken up by water gives about 132 kJ more. |
| Выход кислоты на 1 м³ за сутки при 7% SO2 | около 26 моль, 2,57 кг H2SO4 | моль, кг | Проверка по `ChamberProcessTest`: 0.26 x 0.07 x 1440 с. |

## chemistry/SulfurBurning
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Масса порошка серы (`GRAMS_PER_POWDER`) | `50f` | г | Один порошок, размер порошка мода. |
| Моль серы в порошке (`MOLES_PER_POWDER`) | `50f / 32.07f` | моль | 50 g over the molar mass of S, 32.07 g/mol. |
| Скорость горения маленькой горелки (`MOLES_PER_SECOND`) | `2f / 32.07f` | моль/с | A small burner, 2 g/s. |
| Теплота S + O2 -> SO2 (`HEAT_KILOJOULES_PER_MOLE`) | `297f` | кДж/моль | S(s) + O2 -> SO2 is 296.8 kJ per mole of S, rounded up. |
| Минимальная доля кислорода (`LEAST_OXYGEN_SHARE`) | `0.05f` | доля | Below this the burn smoulders out. A figure for a small burner, not measured. |
| Азот в воздухе (`NITROGEN_IN_AIR`) | `0.7905f` | доля | Rest of the air: 1 - 0.2095. |

## chemistry/Liquid
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| CHAMBER_ACID: плотность (`density`) | `1550` | кг/м³ | About 65% sulfuric acid, 1.55 kg/L. |
| CHAMBER_ACID: вязкость (`viscosity`) | `10f` | мПа·с | About 10 cP on the scale of the other liquids. |
| CHAMBER_ACID: едкость (`corrosive`) | `true` | — | Eats wood, so it never goes into a barrel or a wooden bucket (тег `tfcpp:corrosive`). |
| SULFURIC_ACID: плотность, вязкость, едкость | `1830`, `22f`, `true` | кг/м³, мПа·с | Oil of vitriol, about 93%: 1.83 kg/L, about 22 cP at 20 C. Value of the design (density); viscosity from the references. |
| NITRIC_ACID: плотность, вязкость, едкость | `1480`, `1.1f`, `true` | кг/м³, мПа·с | Aqua fortis, about 90%: 1.48 kg/L, about 1.1 cP. Value of the design (density); viscosity from the references. |
| HYDROCHLORIC_ACID: плотность, вязкость, едкость | `1160`, `1.9f`, `true` | кг/м³, мПа·с | Spirit of salt, 32%: 1.16 kg/L, about 1.9 cP. Value of the design (density); viscosity from the references. |
| WOOD_SPIRIT: плотность, вязкость, едкость | `790`, `0.6f`, `false` | кг/м³, мПа·с | Crude methanol and acetone with some water. It burns but does not eat wood, so it goes into barrels. Value of the design (density); viscosity from the references. |

## chemistry/LeadChamberBlock
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Объём (`VOLUME`) | `1f` | м³ | A chamber a metre across, and open: the whole of the block is gas space. |
| Материал (`PipeMaterial.LEAD`) | — | — | Lead: 5 МПа, стенка 6 мм, размягчается около 150 °C. Холодная камерная кислота держится под плёнкой сульфата (`leadUnderAcid`). |
| Свинцовый корпус фитинга | рейтинг 5,9 атм, плавится при 327 °C, ползёт под нагрузкой; серная кислота до 70% и до 80 °C держится под плёнкой PbSO₄, азотная разъедает (`0.5f`) | атм, °C | Свинцовые фитинги те же, что свинцовая труба: свинец мягкий, поэтому давление ниже железа. |
| Теплопотери (`HEAT_LOSS`) | `0.02f` | кВт/К | Real chambers were built big and thin, to lose their heat. A thin lead sheet on air gives its heat away faster than brick (0,012). A game figure, not measured. |
| Соединение | все стороны | — | Joins pipe on every side, as a chamber is a room of the network. |
| Вода в реакции | `contents.litres`, если жидкость сети вода, иначе 0 | л | Only water in the network makes acid: a chamber with brine or nothing else makes none. |
| Износ свинцовых стен камеры (`tankCorrosion`) | `corrosion(жидкость бака, T сети) * заполнение бака` долей стенки в сутки, на тех же календарных сутках и тем же путём, что у трубы | доля в сутки | Кислота лежит в баке блока и сети её не видит, поэтому износ считается по заполнению бака (мБ / 100). До 80 °C `0.001f`, выше 80 °C в `HOT_ACID_FACTOR` = 20 раз быстрее: плёнка сульфата растворяется. Игровая цифра 20. |

## chemistry/LeadChamberBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Ёмкость бака (`TANK_MILLIBUCKETS`) | `100` | мБ = л | A tank of 100 L, at 1 мБ = 1 л. |
| Окислы азота с порошком селитры (`SALTPETER_MOLES_PER_POWDER`) | `0.5f` | моль на порошок | 50 g of KNO3 is about half a mole, burnt with sulfur in the nitre pot. |
| Порог «полон» для кислоты в баке | `TANK_MILLIBUCKETS - amount - pending` | мБ | Room is the tank less what is in it and what waits as a fraction, so that a whole millibucket always fits. |

## chemistry/SulfurBurnerBlock
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Объём (`VOLUME`) | `0.1f` | м³ | About a tenth of a cubic metre: a trough, not a room. |
| Материал | CERAMIC (`PipeMaterial.CERAMIC`) | — | Fire-clay takes the hot flue of sulfur. |
| Соединение | все стороны, кроме верха | — | Open on the top, where the sulfur is put in: a pipe above it does not join. |

## chemistry/SulfurBurnerBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Ёмкость (`CAPACITY_POWDERS`) | `64` | порошков | A game size: 64 powders of 50 g, 3.2 kg of sulfur. |
| Допуск «полон» (`EPSILON`) | `1e-3f` | моль | Float noise, not sulfur. |
| Сжигание | `SulfurBurning.burn`, каждую секунду | моль/с | Lit by the hot flue: sulfur in air with oxygen burns with no further step. A decision of the design, not a measurement. |

## chemistry/CorrosiveSpill
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Радиус (`RADIUS`) | `3.0` | блоки | A splash reaches three blocks from the place it came out. A game figure. |
| Доля максимального здоровья (`SHARE_OF_MAX_HEALTH`) | `0.3f` | доля | A few hits of a splash are a bad burn. A game figure, not measured. |
| Тип урона (`tfcpp:corrosive`, усталость) | `0f` | — | Its own death message, so that the death says acid, not a blow. Armour gives some protection, as against a splash. |
| Точки вызова | разлив камеры, разрыв трубы в `PipeTracker.blowDown`, перелив в `PipeNetwork.overflow` | — | Only when the liquid is in `tfcpp:corrosive`. A chamber splashes whether or not its tank was full. |

## pneumatics/gas/Gas, Physiology, PipeMaterial: HYDROGEN_CHLORIDE
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Молярная масса | `36.46f` | г/моль | Value of the design. |
| Острая тяжесть (`Physiology.acuteSeverity`) | `severity(ppm, 5, 50, 1300)` | ppm | Values of the design: 5, 50 and 1300 ppm. 50 ppm is the NIOSH IDLH; the 1300 ppm figure is the design's own mark for a death within minutes, and was not checked against the toxicology tables. |
| Запах (`Physiology.isNoticeable`) | от `1` | ppm | Noticed at 1 to 5 ppm. Value of the design. |
| Коррозия труб (`PipeMaterial.corrosion(Gas)`) | дерево `0`, керамика `0`, медь `0.1f`, железо и сталь `0.3f`, свинец `0.05f` | доля стенки в сутки | Value of the design. With the damp of the air it is hydrochloric acid, and chloride keeps iron from passivating. |

## chemistry/Distillation
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Температура растрескивания (`CRACK_TEMPERATURE`) | `550f` | °C | Value of the design. Borosilicate glass in a thin vessel cracks under the gradient above it. Cracks when strictly above. |
| Допуск остатка (`EPSILON`) | `1e-4` | л | Float noise, not liquid: 0.23 L of stock is not refused 0.23 L. |
| Ход партии | пропорционален времени в диапазоне; сырьё берётся один раз в начале | — | A batch outside its range keeps its place, as a coke oven charge does. Output, gas and residue arrive in proportion to progress, the powder when the batch ends. |
| Приёмник | перед шагом нужна ёмкость на выход, остаток и порошок | — | A batch that cannot put its next stretch anywhere does not run, and does not take its inputs. |

## chemistry/DistillationTable
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Концентрирование: 1 л `chamber_acid`, 280–350, 60 с | остаток в колбе 0.59 л `sulfuric_acid`, из колбы уходит вода, приёмник и конденсатор не нужны | л, °C, с | Value of the design, поправлено в пакете 4 ревью: крепкая кислота нелетуча, кипит вода. Баланс ниже. |
| Баланс концентрирования | 1 л камерной кислоты 1,55 кг · 65% = 1,0075 кг H₂SO₄. При 93% и 1,83 кг/л: 1,0075 / 0,93 = 1,083 кг раствора = 0,592 л. Вода: 1,55 − 1,083 = 0,467 кг = 0,467 л = 25,9 моль, на кипение около 1,05 МДж | кг, л, моль | Сходится с 0,59 л. Кислота не теряется: 1,0075 кг входит и остаётся. Водяного пара в газовой сетке мода нет, вода уходит без записи в сетку. Температура окна 280–350 °C это точка кипения готовой кислоты (около 290 °C при 93%): пока колба холоднее, вода ещё не вся ушла. |
| Азотная кислота: 8 селитры + 0.23 л `sulfuric_acid`, 120–200, 60 с | 0.175 л `nitric_acid`, 8 `potassium_bisulfate`, NOx 0.2 моль | л, моль | Value of the design. |
| Соляная кислота: 8 соли + 0.39 л `sulfuric_acid`, вода приёмника 0.53 л, 150–300, 60 с | 0.67 л `hydrochloric_acid`, 8 `sodium_bisulfate` | л | Value of the design. The 0.53 L of water is taken by the batch and ends up in the acid (0.67 L at 1.16 kg/L and 32%). |
| Древесный спирт: 1 л `pyroligneous_liquor`, 70–100, 30 с | 0.02 л `wood_spirit` в приёмнике, 0.9 л `tfc:vinegar` остаётся в колбе | л | Value of the design. Two liquids from one batch: the vinegar is a residue and needs a tank of its own (a decision of the implementation). |
| Суперфосфат: 8 апатита + 0.16 л `chamber_acid`, 0–100, 30 с | 8 `powder/superphosphate` | шт | Value of the design. |
| Сульфат аммония: 1 л `ammonia_liquor` + 0.02 л `sulfuric_acid`, 0–100, 30 с | 1 `powder/ammonium_sulfate` | шт | Value of the design. Two liquids go in, so the retort has two flask tanks (a decision of the implementation). |
| Удобрения | суперфосфат фосфор `0.2f`, сульфат аммония азот `0.1f` | доля, шкала удобрений TFC | Value of the design. Raw apatite is 0.05 and Thomas meal 0.15. |

## chemistry/AcidVapour
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| NOx на литр азотной кислоты (`NITROGEN_OXIDES_PER_LITRE`) | `5f` | моль/л | Value of the design. A litre holds 21 mol of acid, and a quarter of it escapes in the time a flask takes to empty. A game figure. |
| HCl на литр соляной кислоты (`HYDROGEN_CHLORIDE_PER_LITRE`) | `10f` | моль/л | Value of the design. 32% by mass at 1.16 kg/L is 10.2 mol/L. The design table gives 6.8 mol for the batch without water, which is 10.1 mol/L of 0.67 L; the code gives 6.7. |
| Серная и камерная кислота | только пар | — | Sulfuric acid hardly boils off at room temperature. Value of the design. |

## chemistry/SandBath
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Температура вверх (`passedUp`) | `20 + (T − 20) / (1 + глубина)` | °C | Value of the design: 1100 C at depth 1 gives 560 C, at depth 8 about 140 C. A fire no hotter than 20 C gives what it has. |
| Окружающая температура (`AMBIENT`) | `20f` | °C | The room. |
| Глубина | от `1` до `8`, по умолчанию `4` | слоёв | Value of the design (1 to 8); 4 is the implementation's own start. |
| Один слой песка не стоит предметов | — | — | A decision of the implementation: the depth is a setting by hand, nothing is taken for it. |

## chemistry/GlassRetortBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Баки колбы (`FLASK_MILLIBUCKETS`) | `25` (два бака входа, бак остатка) | мБ = л | Value of the design for the input tank (25). The second input tank and the residue tank are the implementation's. |
| Приёмник (`RECEIVER_MILLIBUCKETS`) | `25` | мБ = л | Value of the design. |
| Вода приёмника (`WATER_MILLIBUCKETS`) | `5` | мБ = л | Value of the design. |
| Ячейка порошка (`STACK_LIMIT`) | `64` | шт | One stack; the finished powder lies in its own slot with the same limit. |
| Шаг | `20` тактов календаря TFC, не более `5` с за раз | — | As the coke oven: `Calendars.SERVER.getTicks()`, not game time. |
| Радиус сообщения о трещине (`CRACK_HEARD`) | `12.0` | блоки | The message goes to players within this reach. A game figure. |
| Растрескивание | проверка каждый такт, `temperature > 550` | °C | In its own tick, with no `PipeTracker`: the block is destroyed without a drop. |
| Разлив при разрушении | едкая жидкость → `CorrosiveSpill.at`; жидкости как пары по `AcidVapour`; порошок на землю | — | Same on a crack and on breaking by hand. The unreacted share of a running batch goes back as powder and fume; the reacted share has already come out. |
| Нет воды или нет конденсатора для рецепта с водой приёмника | вся HCl в воздух, вода не расходуется | — | `DistillationRecipe.unabsorbed()`: output 0, gas HCl `10` mol per litre of the output it would have had. |
| Боковые стороны реторты (`Side`) | вода в бак воды приёмника, слив приёмника и остатка; другие жидкости не принимаются | — | Приёмник открыт по бокам, чтобы воду подвести трубой с крана; сверху заливается колба, снизу сливается, как прежде. Решение реализации. |
| Остаток под литр (`rinseDregs`) | при присевшей бутыли плёнка меньше 1 л в колбе смывается на пол: пары по `AcidVapour`, едкая жидкость обжигает рядом (`CorrosiveSpill`) | л | Бутыль берёт целые литры, плёнка меньше литра не берётся ничем и навсегда занимает бак. Вещество не пропадает: оно уходит в воздух комнаты. Решение реализации. |
| Порошок | присев с пустой рукой, игрок получает и продукт, и не взятую заряд-порошок | шт | Раньше забирался только продукт. |

## chemistry/GlassCarboyItem
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Ёмкость (`CAPACITY_MILLIBUCKETS`) | `25` | мБ = л | Value of the design. |
| Стек | `1` | шт | A carboy does not stack. |
| Допустимые жидкости | тег `tfcpp:usable_in_carboy` | — | Every `Liquid`, `tfc:vinegar`, `#tfc:any_fresh_water`. Nothing else. |

## chemistry/PoisonTests
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Молярная масса As₂O₃ (`AS2O3_MOLAR_MASS`) | `197.84f` | г/моль | As the `Gas` enum has it. The JUnit test checks it against the enum. |
| Молярная масса Sb₂O₃ (`SB2O3_MOLAR_MASS`) | `291.52f` | г/моль | As the `Gas` enum has it. |
| Молярная масса Hg (`HG_MOLAR_MASS`) | `200.59f` | г/моль | As the `Gas` enum has it. |
| Слабое зеркало (`FAINT_MG_PER_M3`) | `1f` | мг/м³ | Marsh's test finds 0.02 mg of arsenic in a 20 L sample: 0.02 mg / 0.02 м³ = 1 мг/м³. Value of the design. |
| Плотное зеркало (`STRONG_MG_PER_M3`) | `20f` | мг/м³ | Value of the design: ten times the faint mirror. |
| Сумма As₂O₃ и Sb₂O₃ в `marsh` | сумма в мг/м³, один и тот же зеркальный результат | мг/м³ | Both give the same mirror, as in the real test: the mirror cannot say which poison it is. Value of the design. |
| Локальный час (`HOUR_SECONDS`) | `60f` | с | `LocalTime.SECONDS_PER_DAY / 24`. |
| Стадии листа (`STAGE_ONE_DOSE`, `STAGE_TWO_DOSE`, `STAGE_THREE_DOSE`) | `1`, `10`, `100` | (мг/м³)·ч | Value of the design. A game figure: the dose is not measured. |
| Ячейка газа | 1 м³ | м³ | A cell is 1 м³, so moles per cell and moles per м³ are the same number. |

## chemistry/MarshApparatusItem
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Проб с одного заряда (`TESTS_PER_CHARGE`) | `8` | шт | Value of the design. |
| Кислоты на заряд (`ACID_PER_CHARGE`) | `1` | мБ = л | Value of the design: one millibucket of sulfuric or chamber acid from a carboy in the other hand. |
| Проба из руки | тетраэдрит, As₂O₃, Sb₂O₃: плотное; мышьяковистая медь: слабое; прочее: нет | — | Value of the design. The carboy in the other hand is never the sample while tests are left: the air is tested. |
| Рецепт | бутылка силикатного стекла + слиток цинка, без формы | — | Value of the design. Zinc and acid make the arsine that Marsh's test is run with. |

## pneumatics/instruments/GoldLeafIndicatorBlockEntity
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Шаг опроса | раз в `20` тактов, со сдвигом по позиции `(time + pos) % 20 == 0` | с | As the settling baffle: once a second, spread by position. |
| Порог записи (`SAVE_THRESHOLD`) | `0.01` | (мг/м³)·ч | A game figure: a dose that moves less does not mark the chunk changed. |
| Стадия 3 | перестаёт читать воздух | — | A white leaf takes up no more mercury. |
| Рецепт листа | 1 лист золота → 8 листов | шт | Value of the design. |
| Разрушение | стадия 0 → новый лист; стадия 1 и выше → ничего | — | The dose does not go with the leaf. Value of the design. |
| Пластина | 1/16 блока по высоте, газ проходит через неё | — | A thin shape is not a full block, so a gas cell lies across it, as with an open door. |

## pneumatics/pipes (пакет 3 ревью v1.0: P1-P25)
| Величина | Значение | Единицы | Источник из комментария |
|---|---|---|---|
| Напор (`PipePhysics.head`) | `rho * 9.81 * h`, со знаком | Па | Столб жидкости давит на основание; отрицательная высота это столб, который надо поднять. 100 м воды в насосе: 3,1 кН·м за оборот вместо 0,02. |
| Предел всасывания (`suctionLift`) | `p * 101325 / (rho * g)`, для воды при 1 атм 10,33 | м | Атмосфера держит столб воды 10,33 м. Для кислоты с плотностью 1800 около 5,7 м. Давление пара не вычитается (упрощение). `MassPump` держит прямые 10 блоков. |
| Поверхность жидкости (`surfaceHeight`) | заполнение слоёв по уровням снизу вверх | блоки | Жидкость лежит внизу, объём каждого уровня известен сети (`PipeNetwork.layers`). Её читают кран, насос и проверка разрыва. |
| Проверка разрыва с напором | `gauge + head(rho, поверхность - низ уровня)` против самой слабой стенки каждого уровня | атм | Нижний стык несёт давление газа и вес всей жидкости над ним; слабая стенка ищется по уровням, `O(уровней)` в секунду. |
| Нагрузка насоса | `трение + pistonTorque(dp + rho g (поверхность выхода - поверхность входа))` | кН·м | Решение вне проекта: подъём со стороны входа тоже считается, иначе всасывание в 10 м было бы бесплатным. Газ напора не имеет. Нулевая скорость даёт момент давления (`P8`). |
| Таблица насыщенной воды (`SATURATED_*`) | 100-374 °C, шаг 20 К, `u_f`, `s_f` | кДж/кг, кДж/(кг·К) | Округлено по таблицам Cengel и Boles. Линейная интерполяция. |
| Вскипание при разрыве (`flashEnergy`) | `литры * (u_f(T) - u_2)`, `u_2 = u_f(100) + x u_fg(100)`, `x = (s_f(T) - s_f(100)) / s_fg(100)` | кДж | Изоэнтропическое расширение насыщенной жидкости до 1 атм, работа Броуда. 0,5 м3, 90% воды при 11 атм абс.: около 27 МДж. Добавляется к энергии газа в `GasIgnition.rupture`, потолок ударной волны давление разрыва. |
| Ожог паром (`SteamBurst`) | радиус 4, `scaldShare = clamp(кДж / 20000, 0.05, 0.6)` доли здоровья, от 50 кДж | блоки, доля | Игровые цифры. Свой тип урона `tfcpp:scald`, частицы `CLOUD`. |
| Температура кипения (`saturationTemperature`) | Антуан наоборот | К | Выпуск пара наружу не охлаждает воду ниже точки кипения при давлении снаружи (`P23`). |
| Рассол | `tfc:brine` кипит как вода; замерзает при 252 К, вода 268 К | К | Соль снижает давление пара, это не учтено. Эвтектика NaCl -21 °C. |
| Капля крана (`PipePhysics.drip`) | часть мБ переносится на следующую секунду, менее 1 | л | Поток меньше 1 л/с не давал бы ничего. |
| Плёнка (`PipeContents.FILM_LITRES`) | 1 | л | Менее 1 мБ нельзя слить краном: считается смачиванием стенок и не мешает другой жидкости; смывается перед ней (удаляет менее 1 мБ, решение вне проекта). |
| Сток (`PipeContents.runoff`) | до 1000 л, не занимает объём и не давит | л | Перелив ждёт целого блока в содержимом: сохраняется при пересборке, сохранении и делении. Блок кладётся на ближайшее заменяемое место за выходом, не на сам выход; нет места, остаётся в трубе. |
| Выход под жидкостью | давление выше `1 + rho g * 1 м / 101325`, газ идёт в первую клетку воздуха выше (до 16 блоков через жидкость), иначе теряется | атм | Напор только собственного блока. |
| Манометр | выброс ртути один раз, затем `BLOWN`, утечка `0.01` сечения | доля сечения | Тонкая трубка против трубы в 100 мм. |
| Ранение стенки | блок отдаёт предмет, только если целостность выше `0.95` (`PipeTracker.KEEPS_ABOVE`) | доля | Одна проверка в `PipeEvents.onBlockDrops` для всех `PipeMember`. |
| Задвижка | повторный поворот раньше 20 тактов отказан | такты | Каждый поворот пересобирает сеть. |
| Порядок жидкостей | пустой участок между двумя жидкостями идёт к той, чей id раньше по алфавиту (`LiquidRuns`) | — | Не зависит от того, откуда началась пересборка. |
| Коррозия | дни считаются для каждого участка от его `corrodedUntil`, -1 это сейчас, не меньше 0 | сутки | Чанки помечаются, только если стенка изменилась. |
