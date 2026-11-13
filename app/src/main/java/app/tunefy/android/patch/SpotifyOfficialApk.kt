package app.tunefy.android.patch

import app.tunefy.android.core.Constants

/**
 * The exact Spotify build published on the TuneFy GitHub release. The APK is
 * still downloaded from GitHub, never bundled in TuneFy, and its pinned digest
 * is checked before any analysis or patch work starts.
 */
object SpotifyOfficialApk {

    const val RELEASE_TAG = "Spotify-Apk"
    const val PAGE_URL = "https://github.com/" + Constants.GITHUB_REPOSITORY + "/releases/tag/" + RELEASE_TAG
    const val DOWNLOAD_URL = "https://github.com/" + Constants.GITHUB_REPOSITORY +
        "/releases/download/" + RELEASE_TAG + "/Spotify.apk"
    const val VERSION = "9.1.80.2221"
    const val VERSION_CODE = 145767611L
    const val SIZE_BYTES = 150_486_785L
    const val SHA256 = "f4826f9a9148b15d4a7adeb10a01603a6eca2ff29d16c9d59a320439b4ff17da"
}
