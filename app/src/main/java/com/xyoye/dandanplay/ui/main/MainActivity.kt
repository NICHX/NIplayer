package com.xyoye.dandanplay.ui.main

import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.findAndRemoveFragment
import com.xyoye.common_component.extension.hideFragment
import com.xyoye.common_component.extension.showFragment
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.dandanplay.BR
import com.xyoye.dandanplay.R
import com.xyoye.dandanplay.databinding.ActivityMainBinding
import kotlin.random.Random
import kotlin.system.exitProcess

class MainActivity : BaseActivity<MainViewModel, ActivityMainBinding>() {
    companion object {
        private const val TAG_FRAGMENT_MEDIA = "tag_fragment_media"
        private const val TAG_FRAGMENT_MINE = "tag_fragment_mine"
        private const val TAG_FRAGMENT_PERSONAL = "tag_fragment_personal"
    }

    private lateinit var mediaFragment: Fragment
    private lateinit var mineFragment: Fragment
    private lateinit var personalFragment: Fragment
    private lateinit var previousFragment: Fragment

    private var fragmentTag = ""
    private var touchTime = 0L

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            MainViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_main

    override fun initView() {
        ARouter.getInstance().inject(this)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowTitleEnabled(true)
        }

        title = "服务器"
        supportFragmentManager.findAndRemoveFragment(
            TAG_FRAGMENT_MEDIA,
            TAG_FRAGMENT_MINE,
            TAG_FRAGMENT_PERSONAL
        )
        switchFragment(TAG_FRAGMENT_MEDIA)
        dataBinding.navigationView.post {
            dataBinding.navigationView.selectedItemId = R.id.navigation_media
        }

        dataBinding.navigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.navigation_media -> {
                    title = "服务器"
                    switchFragment(TAG_FRAGMENT_MEDIA)
                }

                R.id.navigation_mine -> {
                    title = "媒体库"
                    switchFragment(TAG_FRAGMENT_MINE)
                }

                R.id.navigation_personal -> {
                    title = "设置"
                    switchFragment(TAG_FRAGMENT_PERSONAL)
                }
            }
            return@setOnItemSelectedListener true
        }

        viewModel.initDatabase()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - touchTime > 1500) {
                ToastCenter.showToast("再按一次退出应用")
                touchTime = System.currentTimeMillis()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun switchFragment(tag: String) {
        if (tag == fragmentTag) {
            return
        }

        if (fragmentTag.isNotEmpty()) {
            supportFragmentManager.hideFragment(previousFragment)
        }

        when (tag) {
            TAG_FRAGMENT_MEDIA -> {
                val fragment = supportFragmentManager.findFragmentByTag(TAG_FRAGMENT_MEDIA)
                if (fragment == null) {
                    getFragment(RouteTable.Local.MediaFragment)?.also {
                        addFragment(it, TAG_FRAGMENT_MEDIA)
                        mediaFragment = it
                        previousFragment = it
                        fragmentTag = tag
                    }
                } else {
                    supportFragmentManager.showFragment(fragment)
                    mediaFragment = fragment
                    previousFragment = fragment
                    fragmentTag = tag
                }
            }

            TAG_FRAGMENT_MINE -> {
                val fragment = supportFragmentManager.findFragmentByTag(TAG_FRAGMENT_MINE)
                if (fragment == null) {
                    getFragment(RouteTable.Local.MineFragment)?.also {
                        addFragment(it, TAG_FRAGMENT_MINE)
                        mineFragment = it
                        previousFragment = it
                        fragmentTag = tag
                    }
                } else {
                    supportFragmentManager.showFragment(fragment)
                    mineFragment = fragment
                    previousFragment = fragment
                    fragmentTag = tag
                }
            }

            TAG_FRAGMENT_PERSONAL -> {
                val fragment = supportFragmentManager.findFragmentByTag(TAG_FRAGMENT_PERSONAL)
                if (fragment == null) {
                    getFragment(RouteTable.User.PersonalFragment)?.also {
                        addFragment(it, TAG_FRAGMENT_PERSONAL)
                        personalFragment = it
                        previousFragment = it
                        fragmentTag = tag
                    }
                } else {
                    supportFragmentManager.showFragment(fragment)
                    personalFragment = fragment
                    previousFragment = fragment
                    fragmentTag = tag
                }
            }

            else -> {
                throw RuntimeException("no match fragment")
            }
        }
    }

    private fun addFragment(fragment: Fragment, tag: String) {
        supportFragmentManager
            .beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .add(R.id.fragment_container, fragment, tag)
            .commit()
    }

    private fun getFragment(path: String) =
        ARouter.getInstance()
            .build(path)
            .navigation() as Fragment?

}
