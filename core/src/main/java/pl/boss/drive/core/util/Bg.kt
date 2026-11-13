package pl.boss.drive.core.util

import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.Executors

/** One shared low-priority worker — keeps thread count (and RAM) tiny on the head unit. */
object Bg {
    private val exec = Executors.newSingleThreadExecutor { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "bg").apply { isDaemon = true }
    }
    private val io = Executors.newCachedThreadPool { r -> Thread(r, "io").apply { isDaemon = true } }
    val main = Handler(Looper.getMainLooper())

    fun run(task: () -> Unit) { exec.execute { try { task() } catch (e: Throwable) { android.util.Log.e("Bg", "task", e) } } }
    fun io(task: () -> Unit) { io.execute { try { task() } catch (e: Throwable) { android.util.Log.e("Bg", "io", e) } } }
    fun ui(task: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) task() else main.post(task) }
    fun <T> load(work: () -> T, done: (T) -> Unit) = run { val r = work(); main.post { done(r) } }
}
