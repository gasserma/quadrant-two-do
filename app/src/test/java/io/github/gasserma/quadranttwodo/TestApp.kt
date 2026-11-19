package io.github.gasserma.quadranttwodo

/** The app under Robolectric: no widget updates (WorkManager isn't initialised in unit tests). */
class TestApp : QuadrantTwoDoApp() {
    override fun startWidgetUpdates() = Unit
}
