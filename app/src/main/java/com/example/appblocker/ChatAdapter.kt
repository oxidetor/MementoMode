package com.example.appblocker

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    private val TAG = "ChatAdapter"
    private val messages = mutableListOf<ChatMessage>()
    private var recyclerView: RecyclerView? = null

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.textViewMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_message_user
        } else {
            R.layout.item_message_ai
        }
        
        val view = LayoutInflater.from(parent.context)
            .inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.text
        
        // Debug log each message being bound
        Log.d(TAG, "Binding message at position $position: ${message.text.take(20)}...")
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    fun addMessage(message: ChatMessage) {
        Log.d(TAG, "Adding message: ${message.text.take(20)}...")
        messages.add(message)
        notifyItemInserted(messages.size - 1)
        
        // Let's not auto-scroll - instead we'll leave that to explicit calls to scrollToLatest
        // from the service where we have more control
    }

    fun clear() {
        val size = messages.size
        Log.d(TAG, "Clearing $size messages from adapter")
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }
    
    // Attach to RecyclerView when set as adapter
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        Log.d(TAG, "Adapter attached to RecyclerView with ${messages.size} messages")
    }
    
    // Detach from RecyclerView
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
        Log.d(TAG, "Adapter detached from RecyclerView")
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }
} 