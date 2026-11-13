package pl.boss.drive.phone

import android.app.Application
import pl.boss.drive.core.ui.Fonts

class PhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fonts.init(this)
        CarLink.init(this)
        if (CarLink.prefs.getBoolean("alwaysOn", false)) CarService.start(this)
    }
}
