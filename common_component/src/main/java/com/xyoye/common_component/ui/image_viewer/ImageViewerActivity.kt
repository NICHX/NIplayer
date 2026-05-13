package com.xyoye.common_component.ui.image_viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import androidx.viewpager2.widget.ViewPager2
import com.alibaba.android.arouter.facade.annotation.Route
import com.gyf.immersionbar.ImmersionBar
import com.xyoye.common_component.R
import com.xyoye.common_component.base.BaseAppCompatActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.databinding.ActivityImageViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@Route(path = RouteTable.ImageViewer.Viewer)
class ImageViewerActivity : BaseAppCompatActivity<ActivityImageViewerBinding>() {

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
    }

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
            // 单张图片模式
            dataBinding.viewPager.visibility = View.GONE
            dataBinding.pageIndicator.visibility = View.GONE
            dataBinding.photoView.visibility = View.VISIBLE
            loadSingleImage(imageList[0])
        } else {
            // 多张图片模式
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
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val bitmap = loadImage(path)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        dataBinding.photoView.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadImage(path: String): Bitmap? {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> {
                loadNetworkImage(path)
            }
            path.startsWith("content://") -> {
                loadContentUri(path)
            }
            else -> {
                loadLocalFile(path)
            }
        }
    }

    private fun loadLocalFile(path: String): Bitmap? {
        val file = File(path)
        if (file.exists()) {
            try {
                return BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun loadContentUri(path: String): Bitmap? {
        var inputStream: InputStream? = null
        try {
            val uri = Uri.parse(path)
            inputStream = contentResolver.openInputStream(uri)
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadNetworkImage(urlStr: String): Bitmap? {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()
            
            inputStream = connection.inputStream
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                inputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initViewPager(imageList: List<String>, startPosition: Int) {
        val adapter = ImageViewerAdapter(imageList)
        dataBinding.viewPager.adapter = adapter
        dataBinding.viewPager.setCurrentItem(startPosition, false)
        
        // 预加载当前页相邻图片
        adapter.preload(startPosition)
        
        // 如果有多张图片，显示页码
        if (imageList.size > 1) {
            updatePageIndicator(startPosition, imageList.size)
            dataBinding.pageIndicator.visibility = android.view.View.VISIBLE
            
            dataBinding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updatePageIndicator(position, imageList.size)
                    adapter.preload(position)
                    adapter.trimCache(position)
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
