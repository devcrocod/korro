plugins {
    id("io.github.devcrocod.korro")
}

repositories {
    mavenCentral()
}

korro {
    docs {
        from(fileTree("docs/in"))
        baseDir.set(layout.projectDirectory.dir("docs/in"))
    }
    samples {
        from(fileTree("samples"))
    }
}
