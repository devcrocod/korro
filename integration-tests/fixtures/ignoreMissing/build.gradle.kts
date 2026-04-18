plugins {
    id("io.github.devcrocod.korro")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

korro {
    docs {
        from(fileTree("docs/in"))
        baseDir.set(layout.projectDirectory.dir("docs/in"))
    }
    samples {
        from(fileTree("samples"))
    }
    behavior {
        ignoreMissing.set(true)
    }
}
