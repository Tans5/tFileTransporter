package com.tans.tfiletransporter.file

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.os.storage.StorageVolume

/**
 * returns a list of all available sd cards paths, or null if not found.
 *
 * @param includePrimaryExternalStorage set to true if you wish to also include the path of the primary external storage
 */
fun getSdCardPaths(context: Context, includePrimaryExternalStorage: Boolean): List<String> {
    val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    val storageVolumes = storageManager.storageVolumes
    if (storageVolumes.isNotEmpty()) {
        val primaryVolume = storageManager.primaryStorageVolume
        val result = ArrayList<String>(storageVolumes.size)
        for (storageVolume in storageVolumes) {
            val volumePath = getVolumePath(storageVolume) ?: continue
            if (storageVolume.uuid == primaryVolume.uuid || storageVolume.isPrimary) {
                if (includePrimaryExternalStorage)
                    result.add(volumePath)
                continue
            }
            result.add(volumePath)
        }
        return result
    } else {
        return emptyList()
    }
}

private fun getVolumePath(storageVolume: StorageVolume): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        return storageVolume.directory?.absolutePath
    try {
        val storageVolumeClazz = StorageVolume::class.java
        val getPath = storageVolumeClazz.getMethod("getPath")
        return getPath.invoke(storageVolume) as String
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}