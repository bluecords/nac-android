package nac.chat.settings.providers

import nac.chat.NACApplication
import nac.chat.persistence.KVStorage

object AgeGateUnlockedStorageProvider {
    private val kv = KVStorage(NACApplication.instance)

    suspend fun setAgeGateUnlocked(unlocked: Boolean) {
        kv.set("ageGateUnlocked", unlocked)
    }

    suspend fun getAgeGateUnlocked(): Boolean {
        return kv.getBoolean("ageGateUnlocked") ?: false
    }
}