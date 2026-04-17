package io.github.devcrocod.korro

import org.gradle.api.tasks.Sync
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Writes outside the build directory (mutates source tree).")
abstract class KorroApplyTask : Sync()
