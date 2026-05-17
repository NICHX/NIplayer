package com.xyoye.local_component.ui.activities.play_history

import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.vertical
import com.xyoye.data_component.enums.MediaType
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityPlayHistoryBinding
import com.xyoye.local_component.ui.weight.PlayHistoryMenus

@Route(path = RouteTable.Local.PlayHistory)
class PlayHistoryActivity : BaseActivity<PlayHistoryViewModel, ActivityPlayHistoryBinding>() {

    @Autowired
    @JvmField
    var typeValue: String = MediaType.LOCAL_STORAGE.value

    private lateinit var mediaType: MediaType

    // 标题栏菜单管理器
    private lateinit var mMenus: PlayHistoryMenus

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            PlayHistoryViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_play_history

    override fun initView() {
        ARouter.getInstance().inject(this)

        mediaType = MediaType.fromValue(typeValue)
        viewModel.mediaType = mediaType

        title = "播放历史"

        initRv()

        initListener()
    }

    override fun onResume() {
        super.onResume()

        viewModel.updatePlayHistory()
    }

    private fun initListener() {
        viewModel.historyLiveData.observe(this) {
            dataBinding.playHistoryRv.setData(it)
        }
        viewModel.playLiveData.observe(this) {
            ARouter.getInstance()
                .build(RouteTable.Player.Player)
                .navigation()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mMenus = PlayHistoryMenus.inflater(this, menu)
        mMenus.onClearHistory { viewModel.clearHistory() }
        mMenus.onSyncHistory { viewModel.syncPlayHistory() }
        mMenus.onSortTypeChanged { viewModel.changeSortOption(it) }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        mMenus.onOptionsItemSelected(item)
        return super.onOptionsItemSelected(item)
    }

    private fun initRv() {
        dataBinding.playHistoryRv.apply {
            layoutManager = vertical()

            adapter = PlayHistoryAdapter(
                this@PlayHistoryActivity,
                viewModel
            ).createAdapter()
        }
    }
}