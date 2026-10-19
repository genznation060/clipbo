package com.clipvault.smartclipboard.keyboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.clipvault.smartclipboard.data.db.NoteEntity
import com.clipvault.smartclipboard.databinding.ItemKeyboardNoteBinding

class KeyboardNoteAdapter(
    private val onClick: (NoteEntity) -> Unit,
) : ListAdapter<NoteEntity, KeyboardNoteAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemKeyboardNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding, onClick)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        private val binding: ItemKeyboardNoteBinding,
        private val onClick: (NoteEntity) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: NoteEntity) {
            binding.noteTitle.text = note.title.ifBlank { "Untitled" }
            binding.noteBody.text = note.body.replace('\n', ' ').trim()
            binding.root.setOnClickListener { onClick(note) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity) = oldItem == newItem
        }
    }
}
