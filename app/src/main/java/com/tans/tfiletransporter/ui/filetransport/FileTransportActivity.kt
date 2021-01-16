package com.tans.tfiletransporter.ui.filetransport

import android.Manifest
import android.os.Build
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FileTransportActivityBinding
import com.tans.tfiletransporter.ui.BaseActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

class FileTransportActivity : BaseActivity<FileTransportActivityBinding, FileTransportActivityState>(R.layout.file_transport_activity, FileTransportActivityState()) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        launch {
            val grant = RxPermissions(this@FileTransportActivity).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.firstOrError().await()

            if (grant) {
                binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        when (tab?.position) {
                            DirTabType.MyDir.ordinal -> updateStateCompletable { it.copy(DirTabType.MyDir) }.bindLife()
                            DirTabType.RemoteDir.ordinal -> updateStateCompletable { it.copy(DirTabType.RemoteDir) }.bindLife()
                        }
                    }
                    override fun onTabUnselected(tab: TabLayout.Tab?) {
                    }
                    override fun onTabReselected(tab: TabLayout.Tab?) {
                    }
                })

                render({ it.selectedTabType }) {
                    binding.floatingActionBt.setImageResource(
                        when (it) {
                            DirTabType.MyDir -> R.drawable.share_variant_outline
                            DirTabType.RemoteDir -> R.drawable.download_outline
                        }
                    )
                    changeDirFragment(it)
                }.bindLife()
            } else {
                finish()
            }
        }

    }

    fun changeDirFragment(dirTabType: DirTabType) {
        val transaction = supportFragmentManager.beginTransaction()
        when (dirTabType) {
            DirTabType.MyDir -> {
                var fragment = supportFragmentManager.findFragmentByTag(MyDirFragment.FRAGMENT_TAG)
                if (fragment == null) {
                    fragment = MyDirFragment()
                    transaction.add(R.id.fragment_container_layout, fragment, MyDirFragment.FRAGMENT_TAG)
                } else {
                    transaction.show(fragment)
                }
                for (f in supportFragmentManager.fragments) {
                    if (f != fragment) { transaction.hide(f) }
                }
            }

            DirTabType.RemoteDir -> {
                var fragment = supportFragmentManager.findFragmentByTag(RemoteDirFragment.FRAGMENT_TAG)
                if (fragment == null) {
                    fragment = RemoteDirFragment()
                    transaction.add(R.id.fragment_container_layout, fragment, RemoteDirFragment.FRAGMENT_TAG)
                } else {
                    transaction.show(fragment)
                }
                for (f in supportFragmentManager.fragments) {
                    if (f != fragment) { transaction.hide(f) }
                }
            }
        }
        transaction.commitAllowingStateLoss()
    }

}

data class FileTransportActivityState(
    val selectedTabType: DirTabType = DirTabType.MyDir
)

enum class DirTabType { MyDir, RemoteDir }