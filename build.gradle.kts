plugins {
    id("java-library")
    alias(libs.plugins.moddev)
}

group = "euphy.upo.sentrymechanicalarm"
version = "0.4.0"

base {
    archivesName = "sentrymechanicalarm-neoforge-1.21.1"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven {
        name = "NeoForge Maven"
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        name = "Create Maven"
        url = uri("https://maven.createmod.net")
    }
    maven {
        name = "Registrate Maven"
        url = uri("https://maven.ithundxr.dev/snapshots")
    }
    maven {
        name = "Tterrag Maven"
        url = uri("https://maven.tterrag.com")
    }
    maven {
        name = "BlameJared Maven (JEI)"
        url = uri("https://maven.blamejared.com")
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
    maven {
        name = "CurseForge"
        url = uri("https://cursemaven.com")
    }
    maven {
        name = "FTB Maven"
        url = uri("https://maven.ftb.dev/releases")
    }
    maven {
        name = "Architectury Maven"
        url = uri("https://maven.architectury.dev/releases")
    }
}

neoForge {
    version = "${libs.versions.neoforge.get()}"

    runs {
        configureEach {
            systemProperty("forge.logging.console.level", "debug")
        }

        register("client") {
            client()
            systemProperty("mixin.env.allowInjections", "true")
        }
        register("server") {
            server()
        }
        register("data") {
            data()
        }
    }

    mods {
        register("sentrymechanicalarm") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // NeoForge
    implementation(libs.neoforge)

    // Create - slim jar without transitive deps to avoid missing Curios/CC/FTB/Architectury
    implementation("com.simibubi.create:create-1.21.1:6.0.10-217:slim") {
        isTransitive = false
    }

    // Registrate (Create's dependency, needed explicitly when using :slim)
    implementation("com.tterrag.registrate:Registrate:MC1.21-1.3.0+67")

    // Ponder
    implementation(libs.ponder)

    // Flywheel
    implementation(libs.flywheel.api)
    runtimeOnly(libs.flywheel.runtime)

    // JEI
    compileOnly(libs.jei.api)
    runtimeOnly(libs.jei.runtime)

    // LuaJ - for script support
    implementation("org.luaj:luaj-jse:3.0.1")

    // TaCZ (Timeless and Classics Guns Zero) - NeoForge 1.21.1 version from Modrinth Maven
    implementation("maven.modrinth:tacz-1.21.1:1.1.8-r2")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Specification-Title"] = "Sentry Mechanical Arm"
        attributes["Specification-Vendor"] = "Euphy"
        attributes["Specification-Version"] = "1"
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Vendor"] = "Euphy"
    }
}