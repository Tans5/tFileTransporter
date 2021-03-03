package com.tans.tfiletransporter.ui.activity.connection.broadcastconnetion

import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.connection.ConnectionActivity
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportActivity
import io.reactivex.Single
import io.reactivex.rxkotlin.withLatestFrom

data class BroadcastState(
        val useSystemBroadcast: Boolean = true
)

class BroadcastConnectionFragment : BaseFragment<BroadcastConnectionFragmentBinding, BroadcastState>(
    layoutId = R.layout.broadcast_connection_fragment,
    default = BroadcastState()
) {

    override fun initViews(binding: BroadcastConnectionFragmentBinding) {

        binding.searchServerLayout.clicks()
            .withLatestFrom(bindState())
            .map { it.second.useSystemBroadcast }
            .withLatestFrom((requireActivity() as ConnectionActivity).bindState())
            .filter { it.second.address.isPresent }
            .map { it.second.address.get() to it.first }
            .switchMapSingle { (localAddress, useSystemBroadcast) ->
                requireActivity().showBroadcastReceiverDialog(localAddress, !useSystemBroadcast)
                    .doOnSuccess {
                        if (it.isPresent) {
                            startActivity(
                                FileTransportActivity.getIntent(
                                    context = requireContext(),
                                    localAddress = localAddress,
                                    remoteDevice = it.get(),
                                    asServer = false
                                )
                            )
                        }
                    }
                    .map { }
                    .onErrorResumeNext {
                        Single.just(Unit)
                    }
            }
            .bindLife()

        binding.asServerLayout.clicks()
            .withLatestFrom(bindState())
            .map { it.second.useSystemBroadcast }
            .withLatestFrom((requireActivity() as ConnectionActivity).bindState())
            .filter { it.second.address.isPresent }
            .map { it.second.address.get() to it.first }
            .switchMapSingle { (localAddress, useSystemBroadcast) ->
                requireActivity().showBroadcastSenderDialog(localAddress, !useSystemBroadcast)
                    .doOnSuccess {
                        if (it.isPresent) {
                            startActivity(
                                FileTransportActivity.getIntent(
                                    context = requireContext(),
                                    localAddress = localAddress,
                                    remoteDevice = it.get(),
                                    asServer = true
                                )
                            )
                        }
                    }
                    .map { }
                    .onErrorResumeNext {
                        Single.just(Unit)
                    }
            }
            .bindLife()

        binding.systemBroadcastSwitch.checkedChanges()
                .flatMapSingle { check ->
                    updateState { it.copy(useSystemBroadcast = check) }
                }
                .bindLife()
    }

}