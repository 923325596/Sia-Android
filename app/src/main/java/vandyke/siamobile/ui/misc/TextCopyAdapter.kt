/*
 * Copyright (c) 2017 Nicholas van Dyke
 *
 * This file is subject to the terms and conditions defined in 'LICENSE.md'
 */

package vandyke.siamobile.ui.misc

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import vandyke.siamobile.R

class TextCopyAdapter(val data: ArrayList<String>) : RecyclerView.Adapter<TextHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): TextHolder {
        return TextHolder(LayoutInflater.from(parent?.context).inflate(R.layout.list_item_text_copy, parent, false))
    }

    override fun onBindViewHolder(holder: TextHolder?, position: Int) {
        holder?.text?.text = data[position]
    }

    override fun getItemCount(): Int {
        return data.size
    }
}