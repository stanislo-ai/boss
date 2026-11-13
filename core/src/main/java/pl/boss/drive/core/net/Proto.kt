package pl.boss.drive.core.net

import java.util.UUID

/** Wire protocol constants shared by the phone and the head-unit app. */
object Proto {
    const val VERSION = 1
    const val MAGIC = "BOSSDRIVE1"

    /** Radio -> broadcast beacons here; phone listens. */
    const val PORT_BEACON = 47870
    /** Phone -> broadcast probes here; radio answers with a unicast beacon. */
    const val PORT_PROBE = 47871
    /** Radio TCP server. */
    const val PORT_TCP = 47872

    /** RFCOMM service used as a Bluetooth fallback / control-only channel. */
    val BT_UUID: UUID = UUID.fromString("6f1d3b8e-2c4a-4f7e-9b51-b0550d21e7a1")
    const val BT_NAME = "BossDrive"

    const val CHUNK = 64 * 1024

    // ---- message types: phone -> radio ----
    const val HELLO = "hello"
    const val NOW_PLAYING = "np"
    const val ART = "art"
    const val SOURCES = "srcs"
    const val ICON = "icon"
    const val QUEUE = "queue"
    const val LIBRARY = "lib"
    const val CALL = "call"
    const val CONTACTS = "contacts"
    const val PHOTO = "photo"
    const val RECENTS = "recents"
    const val STATUS = "status"
    const val WEATHER = "weather"
    const val MESSAGE = "msg"
    const val MESSAGE_GONE = "msgGone"
    const val X_OFFER = "xOffer"
    const val X_CHUNK = "xChunk"
    const val X_END = "xEnd"
    const val R_LIB_REQ = "rlibReq"
    const val R_DELETE = "rdel"
    const val TOAST = "toast"

    // ---- radio -> phone ----
    const val CMD = "cmd"
    const val SELECT_SOURCE = "src"
    const val LIB_REQ = "libReq"
    const val LIB_PLAY = "libPlay"
    const val QUEUE_PLAY = "queuePlay"
    const val CALL_CMD = "callCmd"
    const val MSG_CMD = "msgCmd"
    const val X_ACK = "xAck"
    const val X_DONE = "xDone"
    const val X_FAIL = "xFail"
    const val R_LIB = "rlib"
    const val LOCAL_PLAYING = "localPlaying"
    const val REFRESH = "refresh"

    // ---- add-on store ----
    const val STORE_REQ = "storeReq"     // radio -> phone
    const val STORE = "store"            // phone -> radio: which add-on APKs the phone carries
    const val APP_GET = "appGet"         // radio -> phone
    const val APP_CHUNK = "appChunk"     // phone -> radio
    const val APP_END = "appEnd"         // phone -> radio

    // ---- both ----
    const val PING = "ping"
    const val BYE = "bye"
}
