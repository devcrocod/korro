package io.github.devcrocod.korro

import org.gradle.api.tasks.Copy
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Writes outside the build directory (mutates source tree).")
abstract class KorroTask : Copy()
