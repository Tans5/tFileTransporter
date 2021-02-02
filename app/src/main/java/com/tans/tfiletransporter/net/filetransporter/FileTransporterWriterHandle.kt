package com.tans.tfiletransporter.net.filetransporter

import com.squareup.moshi.Types
import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.moshi
import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.net.filetransporter.FolderChildrenShareWriterHandle.Companion.getJsonString
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.net.model.ResponseFolderModel
import com.tans.tfiletransporter.net.model.ResponseFolderModelJsonAdapter
import com.tans.tfiletransporter.utils.getFileMd5
import com.tans.tfiletransporter.utils.toBytes
import com.tans.tfiletransporter.utils.writeDataLimit
import com.tans.tfiletransporter.utils.writeSuspendSize
import kotlinx.coroutines.rx2.await
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channels
import java.nio.file.Paths


sealed class FileTransporterWriterHandle(val action: FileNetAction) : Stateable<FileTransporterWriterHandle.Companion.TransporterWriterState> by Stateable(TransporterWriterState.Create) {

    suspend fun updateState(state: TransporterWriterState) {
        updateState { state }.await()
    }

    abstract suspend fun handle(writerChannel: AsynchronousSocketChannel)

    suspend fun AsynchronousSocketChannel.defaultActionCodeWrite() {
        val bytes = ByteArray(1) { action.actionCode }
        val buffer = ByteBuffer.allocate(1)
        writeSuspendSize(buffer, bytes)
    }

    suspend fun AsynchronousSocketChannel.defaultIntSizeWrite(intSize: Int) {
        val bytes = intSize.toBytes()
        val buffer = ByteBuffer.allocate(4)
        writeSuspendSize(buffer, bytes)
    }

    companion object {
        enum class TransporterWriterState {
            Create,
            Start,
            Finish
        }
    }
}

class RequestFolderChildrenShareWriterHandle(
        // ByteArray size
        val pathSize: Int,
        val pathWrite: suspend RequestFolderChildrenShareWriterHandle.(outputStream: OutputStream) -> Unit
) : FileTransporterWriterHandle(FileNetAction.RequestFolderChildrenShare) {

    override suspend fun handle(writerChannel: AsynchronousSocketChannel) {
        writerChannel.defaultActionCodeWrite()
        writerChannel.defaultIntSizeWrite(pathSize)
        writerChannel.writeDataLimit(limit = pathSize.toLong(),
                buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)) { outputStream ->
            pathWrite(outputStream)
        }
    }
}

class FolderChildrenShareWriterHandle(
        // ByteArray size
        val filesJsonSize: Int,
        val filesJsonWrite: suspend FolderChildrenShareWriterHandle.(outputStream: OutputStream) -> Unit
) : FileTransporterWriterHandle(FileNetAction.FolderChildrenShare) {

    override suspend fun handle(writerChannel: AsynchronousSocketChannel) {
        writerChannel.defaultActionCodeWrite()
        writerChannel.defaultIntSizeWrite(filesJsonSize)
        writerChannel.writeDataLimit(limit = filesJsonSize.toLong(),
                buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)) { outputStream ->
            filesJsonWrite(outputStream)
        }
    }

    companion object {
        fun getJsonString(responseFolderModel: ResponseFolderModel): String {
            return ResponseFolderModelJsonAdapter(moshi).toJson(responseFolderModel) ?: ""
        }
    }
}

class RequestFilesShareWriterHandle(
        // ByteArray size
        val filesJsonDataSize: Int,
        val filesJsonDataWrite: suspend RequestFilesShareWriterHandle.(outputStream: OutputStream) -> Unit
) : FileTransporterWriterHandle(FileNetAction.RequestFilesShare) {
    override suspend fun handle(writerChannel: AsynchronousSocketChannel) {
        writerChannel.defaultActionCodeWrite()
        writerChannel.defaultIntSizeWrite(filesJsonDataSize)
        writerChannel.writeDataLimit(
                limit = filesJsonDataSize.toLong(),
                buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
        ) { filesJsonDataWrite(it) }
    }

    companion object {
        fun getJsonString(files: List<File>): String {
            val moshiType = Types.newParameterizedType(List::class.java, File::class.java)
            return moshi.adapter<List<File>>(moshiType).toJson(files) ?: ""
        }
    }
}

class FilesShareWriterHandle(
        val files: List<File>,
        val filesWrite: suspend FilesShareWriterHandle.(files: List<File>, outputStream: OutputStream) -> Unit
) : FileTransporterWriterHandle(FileNetAction.FilesShare) {

    override suspend fun handle(writerChannel: AsynchronousSocketChannel) {
        writerChannel.defaultActionCodeWrite()
        val jsonData = getJsonString(files.filter { it.size > 0 }.map { FileMd5(md5 = Paths.get(FileConstants.homePathString, it.path).getFileMd5(), it) }).toByteArray(Charsets.UTF_8)
        writerChannel.defaultIntSizeWrite(jsonData.size)
        val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
        writerChannel.writeDataLimit(limit = jsonData.size.toLong()) { outputStream ->
            val jsonWriter = Channels.newChannel(outputStream)
            jsonWriter.writeSuspendSize(buffer, jsonData)
        }
        val filesLimitSize = files.sumOf { it.size }
        writerChannel.writeDataLimit(
                limit =  filesLimitSize,
                buffer = buffer
        ) { filesWrite(files, it) }
    }

    companion object {
        fun getJsonString(md5Files: List<FileMd5>): String {
            val moshiType = Types.newParameterizedType(List::class.java, FileMd5::class.java)
            return moshi.adapter<List<FileMd5>>(moshiType).toJson(md5Files) ?: ""
        }
    }
}

class SendMessageShareWriterHandle(
        // ByteArray size
        val messageSize: Int,
        val messageWrite: suspend SendMessageShareWriterHandle.(outputStream: OutputStream) -> Unit
) : FileTransporterWriterHandle(FileNetAction.SendMessage) {

    override suspend fun handle(writerChannel: AsynchronousSocketChannel) {
        writerChannel.defaultActionCodeWrite()
        writerChannel.defaultIntSizeWrite(messageSize)
        writerChannel.writeDataLimit(limit = messageSize.toLong(),
                buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)) { outputStream ->
            messageWrite(outputStream)
        }
    }
}