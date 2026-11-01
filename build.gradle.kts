plugins {
    java
    id("net.neoforged.moddev") version "2.0.107"
}

val modId = "tfcpp"
val modVersion = property("mod_version") as String
val minecraftVersion = property("minecraft_version") as String
val neoForgeVersion = property("neoforge_version") as String
val tfcVersion = property("tfc_version") as String
val patchouliVersion = property("patchouli_version") as String

base {
    archivesName.set("TFCpp-NeoForge-$minecraftVersion")
    group = "tfcpp"
    version = modVersion
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.blamejared.com") }
        filter {
            includeGroup("vazkii.patchouli")
            includeGroup("mezz.jei")
        }
    }
    exclusiveContent {
        forRepository { maven("https://maven.terraformersmc.com/") }
        filter { includeGroup("dev.emi") }
    }
}

sourceSets {
    main {
        resources.srcDir("src/generated/resources")
    }
}

neoForge {
    version = neoForgeVersion

    parchment {
        mappingsVersion = property("parchment_version") as String
        minecraftVersion = project.property("minecraft_version") as String
    }

    runs {
        register("client") {
            client()
            // Joins a saved world directly, for checking a build on a real client: -PquickPlayWorld=<folder>
            (findProperty("quickPlayWorld") as String?)?.let { programArguments.addAll("--quickPlaySingleplayer", it) }
            // Logs what worldgen really made in new chunks far from spawn: -Psurvey=<radius in chunks>
            (findProperty("survey") as String?)?.let { systemProperty("tfcpp.survey", it) }
        }
        register("server") {
            server()
            programArgument("--nogui")
        }
        register("data") {
            data()
            programArguments.addAll(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath,
                "--existing-mod", "tfc"
            )
        }
        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            // Runs the load test, which models a large base and logs what the mod costs per tick: -Pload=1
            (findProperty("load") as String?)?.let { systemProperty("tfcpp.load", it) }
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    addModdingDependenciesTo(sourceSets.test.get())
}

dependencies {
    implementation("maven.modrinth:terrafirmacraft:$tfcVersion")
    implementation("vazkii.patchouli:Patchouli:$patchouliVersion")

    compileOnly("mezz.jei:jei-$minecraftVersion-common-api:${property("jei_version")}")
    compileOnly("mezz.jei:jei-$minecraftVersion-neoforge-api:${property("jei_version")}")
    compileOnly("dev.emi:emi-neoforge:${property("emi_version")}:api")
    runtimeOnly("mezz.jei:jei-$minecraftVersion-neoforge:${property("jei_version")}") // Only for running from this project: the built jar does not need it

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    val properties = mapOf(
        "mod_version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "neoforge_version" to neoForgeVersion,
        "tfc_version" to tfcVersion
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties)
    }
}
