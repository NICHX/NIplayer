package com.xyoye.local_component.ui.activities.scrape

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ItemMediaCardCompactBinding
import com.xyoye.local_component.databinding.ItemMediaSectionBinding

class MediaWallAdapter(
    private val sections: List<MediaSection>,
    private val onItemClick: (ScrapeMediaEntity) -> Unit,
    private val onItemLongClick: (ScrapeMediaEntity) -> Unit = {}
) : RecyclerView.Adapter<MediaWallAdapter.SectionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemMediaSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SectionViewHolder(binding, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    override fun getItemCount(): Int = sections.size

    class SectionViewHolder(
        private val binding: ItemMediaSectionBinding,
        private val onItemClick: (ScrapeMediaEntity) -> Unit,
        private val onItemLongClick: (ScrapeMediaEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: MediaSection) {
            binding.sectionTitle.text = section.typeName
            binding.sectionCount.text = "(${section.items.size})"

            val horizontalAdapter = HorizontalCardAdapter(
                section.items,
                onItemClick = { item -> onItemClick(item) },
                onItemLongClick = { item -> onItemLongClick(item) }
            )
            binding.horizontalRv.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = horizontalAdapter
            }
        }
    }
}

class HorizontalCardAdapter(
    private val items: List<ScrapeMediaEntity>,
    private val onItemClick: (ScrapeMediaEntity) -> Unit,
    private val onItemLongClick: (ScrapeMediaEntity) -> Unit = {}
) : RecyclerView.Adapter<HorizontalCardAdapter.CardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemMediaCardCompactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CardViewHolder(binding, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class CardViewHolder(
        private val binding: ItemMediaCardCompactBinding,
        private val onItemClick: (ScrapeMediaEntity) -> Unit,
        private val onItemLongClick: (ScrapeMediaEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScrapeMediaEntity) {
            binding.nameTv.text = item.name
            binding.ratingTv.text = if (item.voteAverage > 0) "${item.voteAverage}" else ""

            Glide.with(binding.coverIv.context)
                .load(item.poster)
                .placeholder(R.drawable.ic_video_cover)
                .error(R.drawable.ic_video_cover)
                .into(binding.coverIv)

            binding.root.setOnClickListener {
                onItemClick(item)
            }
            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }
}