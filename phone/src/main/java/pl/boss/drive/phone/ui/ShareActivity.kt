package pl.boss.drive.phone.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import pl.boss.drive.phone.CarLink
import pl.boss.drive.phone.CarService

/** "Share → Boss Drive" from any file manager / music app: queue the files for the car. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CarLink.init(this)
        val uris = ArrayList<Uri>()
        @Suppress("DEPRECATION")
        when (intent?.action) {
            Intent.ACTION_SEND -> (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM))?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM))?.let { uris.addAll(it) }
        }
        // keep read access to the shared files after this activity finishes
        uris.forEach { try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {} }
        if (uris.isEmpty()) { finish(); return }
        CarLink.transfers.enqueueUris(uris) { n ->
            Toast.makeText(this, if (n > 0) "Boss Drive: $n do wysłania do auta" else "Nie udało się odczytać pliku", Toast.LENGTH_SHORT).show()
            if (n > 0) CarService.start(this)
            finish()
        }
    }
}
