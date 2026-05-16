package com.xyoye.user_component.ui.activities.thumbnail_setting

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Observer
import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityThumbnailSettingBinding

@Route(path = RouteTable.User.ThumbnailSetting)
class ThumbnailSettingActivity :
    BaseActivity<ThumbnailSettingViewModel, ActivityThumbnailSettingBinding>() {

    override fun initView() {
        title = "缩略图管理"

        viewModel.serverItems.observe(this, Observer { items ->
            updateServerList(items)
        })
    }

    private fun updateServerList(items: List<ServerThumbnailItem>) {
        val container = dataBinding.serverContainer
        container.removeAllViews()

        if (items.isEmpty()) {
            dataBinding.serverSection.visibility = View.GONE
            return
        }

        dataBinding.serverSection.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        items.forEach { item ->
            val itemView = inflater.inflate(R.layout.item_server_thumbnail, container, false)
            itemView.findViewById<ImageView>(R.id.server_icon).setImageResource(item.mediaType.cover)
            itemView.findViewById<TextView>(R.id.server_name).text = item.displayName
            itemView.findViewById<TextView>(R.id.server_type).text = item.mediaType.storageName
            val switchView = itemView.findViewById<SwitchCompat>(R.id.server_switch)
            switchView.isChecked = item.enabled
            switchView.tag = item.libraryId
            switchView.setOnCheckedChangeListener { _, isChecked ->
                viewModel.onServerThumbnailChanged(switchView.tag as Int, isChecked)
            }
            container.addView(itemView)
        }
    }

    override fun getLayoutId() = R.layout.activity_thumbnail_setting

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            ThumbnailSettingViewModel::class.java
        )
}
