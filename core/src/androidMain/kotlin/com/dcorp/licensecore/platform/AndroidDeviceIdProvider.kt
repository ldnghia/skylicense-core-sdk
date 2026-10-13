package com.dcorp.licensecore.platform

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * `Settings.Secure.ANDROID_ID`: stable per (app signing key, user, device) since Android 8,
 * survives reinstall (so a reinstall does not burn a `MaxDevice` slot), resets on factory reset.
 * Falls back to a random UUID - persisted in a plain (non-encrypted) prefs file, since this
 * fallback id is no more sensitive than ANDROID_ID itself and is hashed with the app salt before
 * it ever leaves the device either way - when ANDROID_ID is null or the known-bad emulator value.
 */
internal class AndroidDeviceIdProvider(private val context: Context) : DeviceIdProvider {
    override fun rawDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank() && androidId != KNOWN_BAD_ANDROID_ID) return androidId

        val prefs = context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(FALLBACK_KEY, null)?.let { return it }

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(FALLBACK_KEY, generated).commit()
        return generated
    }

    private companion object {
        // Emulators/some devices historically shipped this literal value for ANDROID_ID.
        const val KNOWN_BAD_ANDROID_ID = "9774d56d682e549c"
        const val FALLBACK_PREFS_NAME = "licensecore_device_id_fallback"
        const val FALLBACK_KEY = "random_id"
    }
}
