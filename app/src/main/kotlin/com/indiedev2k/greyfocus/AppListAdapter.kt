package com.indiedev2k.greyfocus

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val selected: MutableSet<String>,
    private val onToggle: (packageName: String, checked: Boolean) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    private var items: List<AppEntry> = emptyList()

    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.icon.setImageDrawable(item.icon)
        holder.label.text = item.label
        holder.pkg.text = item.packageName
        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = item.packageName in selected
        holder.check.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(item.packageName) else selected.remove(item.packageName)
            onToggle(item.packageName, checked)
        }
        holder.itemView.setOnClickListener { holder.check.toggle() }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val label: TextView = view.findViewById(R.id.tvLabel)
        val pkg: TextView = view.findViewById(R.id.tvPackage)
        val check: CheckBox = view.findViewById(R.id.checkbox)
    }
}
