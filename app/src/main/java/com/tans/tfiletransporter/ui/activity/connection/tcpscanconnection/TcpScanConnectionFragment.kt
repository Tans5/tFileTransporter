package com.tans.tfiletransporter.ui.activity.connection.tcpscanconnection

import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.TcpScanConnnectionFragmentBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.net.connection.ServerStatus
import com.tans.tfiletransporter.net.connection.TcpScanConnectionClient
import com.tans.tfiletransporter.net.connection.TcpScanConnectionServer
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.connection.ConnectionActivity
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import java.util.*


data class TcpScanConnectionState(
    val server: Optional<TcpScanConnectionServer> = Optional.empty(),
    val client: Optional<TcpScanConnectionClient> = Optional.empty(),
    val serverStatus: ServerStatus = ServerStatus.Stop
)

class TcpScanConnectionFragment : BaseFragment<TcpScanConnnectionFragmentBinding, TcpScanConnectionState>(
    layoutId = R.layout.tcp_scan_connnection_fragment,
    default = TcpScanConnectionState()
) {

    enum class HandleServer {
        Start,
        Stop
    }

    private val handleServerSubject: Subject<HandleServer> = PublishSubject.create<HandleServer>().toSerialized()

    override fun initViews(binding: TcpScanConnnectionFragmentBinding) {
        (activity as? ConnectionActivity)?.bindState()
            ?.map { it.address }
            ?.distinctUntilChanged()
            ?.switchMapSingle { address ->
                updateState { oldState ->
                    if (address.isPresent) {
                        if (oldState.serverStatus == ServerStatus.Stop) { handleServerSubject.onNext(HandleServer.Start) }
                        oldState.copy(
                            server = Optional.of(
                                TcpScanConnectionServer(
                                    localDevice = LOCAL_DEVICE,
                                    localAddress = address.get()
                                )
                            ),
                            client = Optional.of(
                                TcpScanConnectionClient(
                                    localDevice = LOCAL_DEVICE,
                                    localAddress = address.get()
                                )
                            ),
                            serverStatus = ServerStatus.Stop
                        )
                    } else {
                        oldState.copy(
                            server = Optional.empty(),
                            client = Optional.empty(),
                            serverStatus = ServerStatus.Stop
                        )
                    }
                }
            }
            ?.bindLife()

        render({ it.serverStatus }) {
            val string = when (it) {
                ServerStatus.Start -> R.string.tcp_scan_server_status_active
                ServerStatus.Stop -> R.string.tcp_scan_server_status_error
            }
            binding.serverStatusTv.setText(string)
        }.bindLife()

        handleServerSubject
            .switchMapSingle { handle ->
                rxSingle(Dispatchers.IO) {
                    val server = bindState().filter { it.server.isPresent }.map { it.server.get() }.firstOrError().await()
                    if (handle == HandleServer.Start) {
                        server.runTcpScanConnectionServer { remoteAddress, deviceInfo ->
                            // TODO: Show option dialog.
                            false
                        }
                    } else {
                        server.stop()
                    }
                }.onErrorResumeNext {
                    it.printStackTrace()
                    Single.just(Unit)
                }
            }
            .bindLife()

        bindState().map { it.server }
            .distinctUntilChanged()
            .switchMap { server ->
                if (server.isPresent) {
                    server.get().bindState()
                        .map { it.serverStatus }
                        .flatMapSingle { serverStatus ->
                            updateState {
                                it.copy(serverStatus = serverStatus)
                            }
                        }
                } else {
                    updateState { oldState ->
                        oldState.copy(serverStatus = ServerStatus.Stop)
                    }.toObservable()
                }
            }
            .bindLife()

    }

    override fun onStart() {
        super.onStart()
        handleServerSubject.onNext(HandleServer.Start)
    }

    override fun onStop() {
        super.onStop()
        handleServerSubject.onNext(HandleServer.Stop)
    }

}