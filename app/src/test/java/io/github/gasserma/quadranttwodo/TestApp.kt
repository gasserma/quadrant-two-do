package io.github.gasserma.quadranttwodo

/**
 * The app under Robolectric: no widget updates (WorkManager isn't initialised in unit tests) and no
 * system night-mode calls; tests install their own container.
 */
class TestApp : QuadrantTwoDoApp() {
    override fun startWidgetUpdates() = Unit
    override fun startThemeSetting() = Unit
}
