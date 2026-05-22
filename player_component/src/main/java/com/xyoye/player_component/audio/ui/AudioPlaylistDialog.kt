package com.xyoye.player_component.audio.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.xyoye.player_component.R
import com.xyoye.player_component.audio.manager.AudioPlayManager
import com.xyoye.player_component.audio.model.AudioPlayMode
import com.xyoye.player_component.audio.model.AudioSong
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AudioPlaylistDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    private val dialog: BottomSheetDialog = BottomSheetDialog(context, com.xyoye.common_component.R.style.Bottom_Sheet_Dialog)
    private val adapter = PlaylistAdapter()
    private var recyclerView: RecyclerView? = null
    private var tvTitle: TextView? = null
    private var tvPlayMode: TextView? = null
    private var ivMode: ImageView? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_audio_playlist, null)
        dialog.setContentView(view)
        dialog.behavior.isDraggable = true

        tvTitle = view.findViewById(R.id.tvTitle)
        tvPlayMode = view.findViewById(R.id.tvPlayMode)
        ivMode = view.findViewById(R.id.ivMode)
        recyclerView = view.findViewById(R.id.recyclerView)

        (ivMode?.parent as? View)?.setOnClickListener { switchPlayMode() }

        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = adapter
        recyclerView?.itemAnimator = null

        observeData()
    }

    private fun observeData() {
        lifecycleOwner.lifecycleScope.launch {
            AudioPlayManager.playMode.collectLatest { mode ->
                ivMode?.setImageResource(getPlayModeIcon(mode))
                tvPlayMode?.text = getPlayModeText(mode)
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioPlayManager.playlist.collectLatest { list ->
                    tvTitle?.text = if (list.isNotEmpty()) "播放列表(${list.size})" else "播放列表"
                    adapter.setItems(list, AudioPlayManager.currentSong.value)
                }
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioPlayManager.currentSong.collectLatest { song ->
                    adapter.updateCurrentSong(song)
                    val list = AudioPlayManager.playlist.value
                    if (list.isNotEmpty() && song != null) {
                        val index = list.indexOfFirst { it.uniqueKey == song.uniqueKey }
                        if (index >= 0) {
                            (recyclerView?.layoutManager as? LinearLayoutManager)
                                ?.scrollToPosition(index.coerceAtMost(list.size - 1))
                        }
                    }
                }
            }
        }
    }

    fun show() {
        val playlist = AudioPlayManager.playlist.value
        val song = AudioPlayManager.currentSong.value
        val mode = AudioPlayManager.playMode.value

        tvTitle?.text = if (playlist.isNotEmpty()) "播放列表(${playlist.size})" else "播放列表"
        ivMode?.setImageResource(getPlayModeIcon(mode))
        tvPlayMode?.text = getPlayModeText(mode)
        adapter.setItems(playlist, song)

        dialog.show()
    }

    fun dismiss() {
        dialog.dismiss()
    }

    private fun switchPlayMode() {
        val mode = when (AudioPlayManager.playMode.value) {
            AudioPlayMode.Loop -> AudioPlayMode.Shuffle
            AudioPlayMode.Shuffle -> AudioPlayMode.Single
            AudioPlayMode.Single -> AudioPlayMode.Loop
        }
        AudioPlayManager.setPlayMode(mode)
    }

    private fun getPlayModeIcon(mode: AudioPlayMode): Int {
        return when (mode) {
            AudioPlayMode.Loop -> R.drawable.ic_play_mode_loop
            AudioPlayMode.Shuffle -> R.drawable.ic_play_mode_shuffle
            AudioPlayMode.Single -> R.drawable.ic_play_mode_single
        }
    }

    private fun getPlayModeText(mode: AudioPlayMode): String {
        return when (mode) {
            AudioPlayMode.Loop -> "列表循环"
            AudioPlayMode.Shuffle -> "随机播放"
            AudioPlayMode.Single -> "单曲循环"
        }
    }

    private inner class PlaylistAdapter : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

        private var items: List<AudioSong> = emptyList()
        private var currentSong: AudioSong? = null

        fun setItems(newItems: List<AudioSong>, current: AudioSong?) {
            currentSong = current
            val oldItems = items
            items = newItems
            if (oldItems.isEmpty()) {
                notifyDataSetChanged()
            } else {
                DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize() = oldItems.size
                    override fun getNewListSize() = newItems.size
                    override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                        oldItems[oldPos].uniqueKey == newItems[newPos].uniqueKey
                    override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                        oldItems[oldPos] == newItems[newPos]
                }, false).dispatchUpdatesTo(this)
            }
        }

        fun updateCurrentSong(song: AudioSong?) {
            val oldSong = currentSong
            currentSong = song
            val oldIndex = items.indexOfFirst { it.uniqueKey == oldSong?.uniqueKey }
            val newIndex = items.indexOfFirst { it.uniqueKey == song?.uniqueKey }
            if (oldIndex >= 0) notifyItemChanged(oldIndex)
            if (newIndex >= 0 && newIndex != oldIndex) notifyItemChanged(newIndex)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_audio_playlist, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val song = items[position]
            val isCurrent = song.uniqueKey == currentSong?.uniqueKey

            holder.tvTitle.text = song.title
            holder.tvTitle.setTextColor(
                if (isCurrent) {
                    context.getColor(R.color.colorAccent)
                } else {
                    context.getColor(R.color.text_black)
                }
            )
            holder.tvArtist.isVisible = false
            holder.root.setOnClickListener {
                AudioPlayManager.play(song)
            }
            holder.ivDelete.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    AudioPlayManager.removeFromPlaylist(pos)
                }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root: View = view.findViewById(R.id.root)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvArtist: TextView = view.findViewById(R.id.tvArtist)
            val ivDelete: View = view.findViewById(R.id.ivDelete)
        }
    }
}
