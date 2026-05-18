package com.xyoye.common_component.ui.image_viewer

import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.viewpager2.widget.ViewPager2
import com.alibaba.android.arouter.facade.annotation.Route
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import com.gyf.immersionbar.ImmersionBar
import com.xyoye.common_component.R
import com.xyoye.common_component.base.BaseAppCompatActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.databinding.ActivityImageViewerBinding
import java.io.File

@Route(path = RouteTable.ImageViewer.Viewer)
class ImageViewerActivity : BaseAppCompatActivity<ActivityImageViewerBinding>() {

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val EXTRA_AUTH_HEADER = "extra_auth_header"
    }

    private var authHeader: String? = null

    override fun initStatusBar() {
        ImmersionBar.with(this)
            .statusBarDarkFont(false)
            .statusBarColor(android.R.color.black)
            .navigationBarColor(android.R.color.black)
            .init()
    }

    override fun getLayoutId() = R.layout.activity_image_viewer

    override fun initView() {
        val singleUri = intent.getStringExtra(EXTRA_IMAGE_URI)
        val uris = intent.getStringArrayListExtra(EXTRA_IMAGE_URIS)
        val currentPosition = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)
        authHeader = intent.getStringExtra(EXTRA_AUTH_HEADER)

        val imageList = when {
            !uris.isNullOrEmpty() -> uris
            !singleUri.isNullOrEmpty() -> listOf(singleUri)
            else -> {
                finish()
                return
            }
        }

        if (imageList.isEmpty()) {
            finish()
            return
        }

        if (imageList.size == 1) {
            dataBinding.viewPager.visibility = View.GONE
            dataBinding.pageIndicator.visibility = View.GONE
            dataBinding.photoView.visibility = View.VISIBLE
            loadSingleImage(imageList[0])
        } else {
            dataBinding.photoView.visibility = View.GONE
            dataBinding.viewPager.visibility = View.VISIBLE
            initViewPager(imageList, currentPosition)
        }
    }

    private fun loadSingleImage(path: String) {
        dataBinding.photoView.scaleType = ImageView.ScaleType.FIT_CENTER
        dataBinding.photoView.maximumScale = 10f
        dataBinding.photoView.mediumScale = 5f
        dataBinding.photoView.minimumScale = 1f

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        Glide.with(this)
            .load(buildGlideUrl(path))
            .override(screenWidth, screenHeight)
            .apply(RequestOptions().apply {
                format(DecodeFormat.PREFER_RGB_565)
                dontAnimate()
                diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
            })
            .into(dataBinding.photoView)
    }

    private fun buildGlideUrl(imagePath: String): Any {
        val header = authHeader
        return when {
            imagePath.startsWith("http://") || imagePath.startsWith("https://") -> {
                if (header != null) {
                    GlideUrl(imagePath, LazyHeaders.Builder()
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Authorization", header)
                        .build())
                } else {
                    imagePath
                }
            }
            imagePath.startsWith("content://") -> Uri.parse(imagePath)
            else -> File(imagePath)
        }
    }

    private fun initViewPager(imageList: List<String>, startPosition: Int) {
        val adapter = ImageViewerAdapter(imageList, authHeader)
        dataBinding.viewPager.adapter = adapter
        dataBinding.viewPager.setCurrentItem(startPosition, false)

        adapter.preload(startPosition)

        if (imageList.size > 1) {
            updatePageIndicator(startPosition, imageList.size)
            dataBinding.pageIndicator.visibility = View.VISIBLE

            dataBinding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updatePageIndicator(position, imageList.size)
                    adapter.preload(position)
                }
            })
        }
    }

    private fun updatePageIndicator(position: Int, total: Int) {
        dataBinding.pageIndicator.text = "${position + 1} / $total"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
