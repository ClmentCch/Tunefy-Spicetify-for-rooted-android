package app.tunefy.android.ui.nav

object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val SEARCH = "search"
    const val EXTENSIONS = "extensions"
    const val THEMES = "themes"
    const val DOWNLOADS = "downloads"
    const val PATCHES = "patches"
    const val ROOT = "root"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val DETAIL = "detail/{itemId}"

    fun detail(itemId: String): String = "detail/$itemId"
}
