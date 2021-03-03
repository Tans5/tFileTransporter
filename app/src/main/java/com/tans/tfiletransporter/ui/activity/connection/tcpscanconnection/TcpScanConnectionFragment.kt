package com.tans.tfiletransporter.ui.activity.connection.tcpscanconnection

import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.databinding.TcpScanConnnectionFragmentBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.net.connection.RemoteDevice
import com.tans.tfiletransporter.net.connection.ServerStatus
import com.tans.tfiletransporter.net.connection.TcpScanConnectionClient
import com.tans.tfiletransporter.net.connection.TcpScanConnectionServer
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.showLoadingDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showOptionalDialog
import com.tans.tfiletransporter.ui.activity.connection.ConnectionActivity
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportActivity
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import java.util.*


data class TcpScanConnectionState(
    val server: Optional<TcpScanConnectionServer> = Optional.empty(),
    val client: Optional<TcpScanConnectionClient> = Optional.empty(),
    val serverStatus: ServerStatus = ServerStatus.Stop,
    val remoteDevices: List<RemoteDevice> = emptyList()
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
                            val localAddress = (activity as ConnectionActivity).bindState().map { it.address }.firstOrError().await()
                            val result = withContext(Dispatchers.Main) {
                                val accept = requireActivity().showOptionalDialog(
                                    title = getString(R.string.broadcast_request_connect),
                                    message = getString(R.string.broadcast_remote_info, deviceInfo, remoteAddress.hostAddress),
                                    cancelable = false,
                                    positiveButtonText = getString(R.string.broadcast_request_accept),
                                    negativeButtonText = getString(R.string.broadcast_request_deny)
                                ).await().orElseGet { false }
                                if (accept && localAddress.isPresent) {
                                    requireActivity().startActivity(
                                        FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress.get(),
                                            remoteDevice = remoteAddress to deviceInfo,
                                            asServer = true
                                        )
                                    )
                                }
                                accept
                            }
                            result
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

        binding.scanLayout.clicks()
            .switchMapSingle {
                rxSingle(Dispatchers.IO) {
                    val client = bindState().map { it.client}.firstOrError().await()
                    if (client.isPresent) {
                        val loadingDialog = withContext(Dispatchers.Main) {
                            requireActivity().showLoadingDialog()
                        }
                        val result = runCatching { client.get().scanServers { _, _ ->  } }.getOrNull() ?: emptyList()
                        updateState { oldState -> oldState.copy(remoteDevices = result) }.await()
                        withContext(Dispatchers.Main) {
                            loadingDialog.cancel()
                        }
                    }
                }
            }
            .bindLife()

        binding.remoteDevicesRv.adapter = SimpleAdapterSpec<RemoteDevice, RemoteServerItemLayoutBinding>(
            layoutId = R.layout.remote_server_item_layout,
            bindData = { _, data, lBinding ->
                lBinding.device = data.second
                lBinding.ipAddress = data.first.hostAddress
            },
            dataUpdater = bindState().map { it.remoteDevices },
            differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first.hostAddress == d2.first.hostAddress }),
            itemClicks = listOf { lBinding, _ ->
                lBinding.root to { _, data ->
                    rxSingle(Dispatchers.IO) {
                        val client = bindState().map { it.client }.firstOrError().await()
                        val localAddress = (activity as ConnectionActivity).bindState().map { it.address }.firstOrError().await()
                        if (client.isPresent && localAddress.isPresent) {
                            val loadingDialog = withContext(Dispatchers.Main) {
                                requireActivity().showLoadingDialog()
                            }
                            val result = runCatching { client.get().connectTo(data.first) }
                            withContext(Dispatchers.Main) { loadingDialog.cancel() }
                            if (result.getOrDefault(false)) {
                                withContext(Dispatchers.Main) {
                                    requireActivity().startActivity(
                                        FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress.get(),
                                            remoteDevice = data,
                                            asServer = false
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ).toAdapter()

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