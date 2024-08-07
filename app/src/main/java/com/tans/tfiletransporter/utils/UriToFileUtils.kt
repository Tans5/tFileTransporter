package com.tans.tfiletransporter.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import java.io.File


internal fun uri2FileReal(context: Context, uri: Uri): File? {
    val authority = uri.authority
    val scheme = uri.scheme
    val path = uri.path
    if (path != null) {
        val externals = arrayOf("/external/", "/external_path/")
        var file: File?
        for (external: String in externals) {
            if (path.startsWith(external)) {
                file = File(
                    Environment.getExternalStorageDirectory().absolutePath
                            + path.replace(external, "/")
                )
                if (file.exists()) {
                    return file
                }
            }
        }
        file = null
        if (path.startsWith("/files_path/")) {
            file = File(
                (context.filesDir.absolutePath
                        + path.replace("/files_path/", "/"))
            )
        } else if (path.startsWith("/cache_path/")) {
            file = File(
                (context.cacheDir.absolutePath
                        + path.replace("/cache_path/", "/"))
            )
        } else if (path.startsWith("/external_files_path/")) {
            file = File(
                (context.getExternalFilesDir(null)?.absolutePath
                        + path.replace("/external_files_path/", "/"))
            )
        } else if (path.startsWith("/external_cache_path/")) {
            file = File(
                (context.externalCacheDir?.absolutePath
                        + path.replace("/external_cache_path/", "/"))
            )
        }
        if (file != null && file.exists()) {
            return file
        }
    }
    if ((ContentResolver.SCHEME_FILE == scheme)) {
        if (path != null) return File(path)
        return null
    } // end 0
    else if (DocumentsContract.isDocumentUri(context, uri)
    ) {
        if (("com.android.externalstorage.documents" == authority)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                return File(Environment.getExternalStorageDirectory().toString() + "/" + split[1])
            } else {
                // Below logic is how External Storage provider build URI for documents
                // http://stackoverflow.com/questions/28605278/android-5-sd-card-label
                val mStorageManager =
                    context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                try {
                    val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
                    val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
                    val getUuid = storageVolumeClazz.getMethod("getUuid")
                    val getState = storageVolumeClazz.getMethod("getState")
                    val getPath = storageVolumeClazz.getMethod("getPath")
                    val isPrimary = storageVolumeClazz.getMethod("isPrimary")
                    val isEmulated = storageVolumeClazz.getMethod("isEmulated")
                    val result = getVolumeList.invoke(mStorageManager) as Array<*>
                    val length: Int = result.size
                    for (i in 0 until length) {
                        val storageVolumeElement: Any = result[i]!!
                        //String uuid = (String) getUuid.invoke(storageVolumeElement);
                        val mounted =
                            ((Environment.MEDIA_MOUNTED == getState.invoke(storageVolumeElement)) || (Environment.MEDIA_MOUNTED_READ_ONLY == getState.invoke(
                                storageVolumeElement
                            )))

                        //if the media is not mounted, we need not get the volume details
                        if (!mounted) continue

                        //Primary storage is already handled.
                        if ((isPrimary.invoke(storageVolumeElement) as Boolean
                                    && isEmulated.invoke(storageVolumeElement) as Boolean)
                        ) {
                            continue
                        }
                        val uuid: String? = getUuid.invoke(storageVolumeElement) as? String
                        if (uuid != null && (uuid == type)) {
                            return File(
                                (getPath.invoke(storageVolumeElement)?.toString()
                                    ?: "") + "/" + split[1]
                            )
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            return null
        } // end 1_0
        else if (("com.android.providers.downloads.documents" == authority)) {
            var id = DocumentsContract.getDocumentId(uri)
            if (TextUtils.isEmpty(id)) {
                return null
            }
            if (id.startsWith("raw:")) {
                return File(id.substring(4))
            } else if (id.startsWith("msf:")) {
                id = id.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            }
            val availableId: Long
            try {
                availableId = id.toLong()
            } catch (e: Exception) {
                return null
            }
            val contentUriPrefixesToTry = arrayOf(
                "content://downloads/public_downloads",
                "content://downloads/all_downloads",
                "content://downloads/my_downloads"
            )
            for (contentUriPrefix: String in contentUriPrefixesToTry) {
                val contentUri =
                    ContentUris.withAppendedId(Uri.parse(contentUriPrefix), availableId)
                try {
                    val file: File? = getFileFromUri(context, contentUri)
                    if (file != null) {
                        return file
                    }
                } catch (ignore: Exception) {
                }
            }
            return null
        } // end 1_1
        else if (("com.android.providers.media.documents" == authority)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val type = split[0]
            val contentUri: Uri = if (("image" == type)) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else if (("video" == type)) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else if (("audio" == type)) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                return null
            }
            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])
            return getFileFromUri(context, contentUri, selection, selectionArgs)
        } // end 1_2
        else if ((ContentResolver.SCHEME_CONTENT == scheme)) {
            return getFileFromUri(context, uri)
        } // end 1_3
        else {
            return null
        } // end 1_4
    } // end 1
    else if ((ContentResolver.SCHEME_CONTENT == scheme)) {
        return getFileFromUri(context, uri)
    } // end 2
    else {
        return null
    } // end 3
}

private fun getFileFromUri(context: Context, uri: Uri): File? {
    return getFileFromUri(context, uri, null, null)
}

private fun getFileFromUri(
    context: Context,
    uri: Uri,
    selection: String?,
    selectionArgs: Array<String>?
): File? {
    if ("com.google.android.apps.photos.content" == uri.authority) {
        if (!TextUtils.isEmpty(uri.lastPathSegment)) {
            return uri.lastPathSegment?.let { File(it) }
        }
    } else if ("com.tencent.mtt.fileprovider" == uri.authority) {
        val path = uri.path
        if (!TextUtils.isEmpty(path)) {
            val fileDir = Environment.getExternalStorageDirectory()
            return File(fileDir, path!!.substring("/QQBrowser".length, path.length))
        }
    } else if ("com.huawei.hidisk.fileprovider" == uri.authority) {
        val path = uri.path
        if (!TextUtils.isEmpty(path)) {
            return File(path!!.replace("/root", ""))
        }
    }
    val cursor: Cursor = context.contentResolver?.query(
        uri, arrayOf("_data"), selection, selectionArgs, null
    ) ?: return null
    return try {
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex("_data")
            if (columnIndex > -1) {
                File(cursor.getString(columnIndex))
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: java.lang.Exception) {
        null
    } finally {
        cursor.close()
    }
}