package app.tunefy.android.core

object Constants {
    const val APP_NAME = "TuneFy"
    const val GITHUB_REPOSITORY = "ClmentCch/Tunefy-Spicetify-for-rooted-android"
    const val PROJECT_URL = "https://github.com/$GITHUB_REPOSITORY"
    const val ISSUES_URL = "$PROJECT_URL/issues"

    const val SPOTIFY_PACKAGE = "com.spotify.music"
    const val MORPHE_URL = "https://morphe.software/"
    const val MORPHE_PATCHES_REPOSITORY = "https://github.com/spicetify/morphe-patches"
    const val MORPHE_PATCHES_INDEX_URL =
        "https://raw.githubusercontent.com/spicetify/morphe-patches/main/patches-list.json"
    const val SPICETIFY_REPOSITORY = "https://github.com/spicetify"
    const val SPICETIFY_THEMES_REPOSITORY = "https://github.com/spicetify/spicetify-themes"

    const val USER_AGENT = "TuneFy/1.0 (Android; +$PROJECT_URL)"

    const val CHANNEL_DOWNLOADS = "tunefy_downloads"
    const val CHANNEL_INSTALLS = "tunefy_installs"
    const val NOTIFICATION_DOWNLOAD_ID = 4201
    const val NOTIFICATION_INSTALL_ID = 4202

    const val EXTRA_TASK_ID = "task_id"
    const val ACTION_CANCEL = "app.tunefy.android.action.CANCEL_DOWNLOAD"
    const val ACTION_CANCEL_ALL = "app.tunefy.android.action.CANCEL_ALL_DOWNLOADS"
    const val ACTION_START = "app.tunefy.android.action.START_DOWNLOAD_SERVICE"
}
