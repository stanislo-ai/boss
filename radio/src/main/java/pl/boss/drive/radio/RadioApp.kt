package pl.boss.drive.radio

import android.app.Application
import android.content.ComponentCallbacks2
import pl.boss.drive.core.ui.Fonts
import pl.boss.drive.radio.ui.ArtCache

class RadioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fonts.init(this)
        Hub.init(this)
        LinkService.start(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // 1 GB unit: give caches back as soon as the system asks
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Store.photos.evictAll(); Store.icons.evictAll(); Store.library.evictAll()
            ArtCache.clear()
        }
    }
}
