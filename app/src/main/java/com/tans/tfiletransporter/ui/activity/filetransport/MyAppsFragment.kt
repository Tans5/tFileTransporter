package com.tans.tfiletransporter.ui.activity.filetransport

import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.AppItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyAppsFragmentLayoutBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import io.reactivex.Observable

class MyAppsFragment : BaseFragment<MyAppsFragmentLayoutBinding, Unit>(
        layoutId = R.layout.my_apps_fragment_layout,
        default = Unit
) {
    override fun onInit() {
        binding.myAppsRv.adapter = SimpleAdapterSpec<Unit, AppItemLayoutBinding>(
                layoutId = R.layout.app_item_layout,
                dataUpdater = Observable.just(emptyList())
        ).toAdapter()
    }

    companion object {
        const val FRAGMENT_TAG = "my_apps_fragment_tag"
    }
}