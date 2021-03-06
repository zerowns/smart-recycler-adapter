package smartadapter

/*
 * Created by Manne Öhlund on 2019-06-25.
 * Copyright © 2019 All rights reserved.
 */

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import smartadapter.internal.extension.isMutable
import smartadapter.internal.mapper.ViewHolderMapper
import smartadapter.listener.OnBindViewHolderListener
import smartadapter.listener.OnCreateViewHolderListener
import smartadapter.listener.OnSmartRecycleAdapterCreatedListener
import smartadapter.listener.OnViewAttachedToWindowListener
import smartadapter.listener.OnViewDetachedFromWindowListener
import smartadapter.listener.OnViewRecycledListener
import smartadapter.viewholder.RecyclableViewHolder
import smartadapter.viewholder.SmartAdapterHolder
import smartadapter.viewholder.SmartViewHolder
import smartadapter.widget.ViewTypeResolver
import java.util.ArrayList
import java.util.HashMap
import kotlin.reflect.KClass

/**
 * Type alias for SmartViewHolder, Kotlin class type.
 */
typealias SmartViewHolderType = KClass<out SmartViewHolder<*>>

/**
 * Type alias for adapter data items, Kotlin class type.
 */
typealias ItemType = KClass<*>

/**
 * Type alias for view id, ex: R.id.my_id.
 */
typealias ViewId = Int

/**
 * Type alias for resolved view type in [RecyclerView.Adapter].
 */
typealias ViewType = Int

/**
 * Type alias for position in [RecyclerView.Adapter].
 */
typealias Position = Int

/**
 * SmartRecyclerAdapter is the core implementation of the library.
 * It handles all the implementations of the [ISmartRecyclerAdapter] functionality.
 */
@Suppress("UNCHECKED_CAST")
open class SmartRecyclerAdapter
    internal constructor(private var items: MutableList<Any>)
        : RecyclerView.Adapter<SmartViewHolder<Any>>(), ISmartRecyclerAdapter {

    override var smartItemCount: Int = 0
    override var viewHolderMapper: ViewHolderMapper = ViewHolderMapper()
    override var viewTypeResolver: ViewTypeResolver? = null
    final override val viewHolderBinders = mutableListOf<SmartViewHolderBinder>()

    init {
        setItems(items, false)
        updateItemCount()

        viewHolderBinders.forEach {
            (it as? OnSmartRecycleAdapterCreatedListener)?.onSmartRecycleAdapterCreated(this)
        }
    }

    override fun getItemViewType(position: Position): ViewType {
        return viewHolderMapper.getItemViewType(viewTypeResolver, items[position], position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: ViewType): SmartViewHolder<Any> {
        val smartViewHolder = viewHolderMapper.createViewHolder<SmartViewHolder<Any>>(parent, viewType)
        if (smartViewHolder is SmartAdapterHolder && smartViewHolder.smartRecyclerAdapter == null) {
            smartViewHolder.smartRecyclerAdapter = this
        }
        viewHolderBinders.forEach {
            if ((it.viewHolderType == SmartViewHolder::class
                        || it.viewHolderType.isInstance(smartViewHolder))
                && it is OnCreateViewHolderListener) {
                it.onCreateViewHolder(this, smartViewHolder)
            }
        }
        return smartViewHolder
    }

    override fun onBindViewHolder(smartViewHolder: SmartViewHolder<Any>, position: Position) {
        smartViewHolder.bind(items[position])
        viewHolderBinders.forEach {
            if ((it.viewHolderType == SmartViewHolder::class || it.viewHolderType == smartViewHolder::class) && it is OnBindViewHolderListener) {
                it.onBindViewHolder(this, smartViewHolder)
            }
        }
    }

    override fun onBindViewHolder(
        smartViewHolder: SmartViewHolder<Any>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        super.onBindViewHolder(smartViewHolder, position, payloads)
        if (items.size != position) {
            smartViewHolder.bind(items[position], payloads)
        }
    }

    override fun onViewRecycled(smartViewHolder: SmartViewHolder<Any>) {
        super.onViewRecycled(smartViewHolder)
        smartViewHolder.unbind()
        viewHolderBinders.forEach {
            if ((it.viewHolderType == SmartViewHolder::class || it.viewHolderType == smartViewHolder::class) && it is OnViewRecycledListener) {
                it.onViewRecycled(this, smartViewHolder)
            }
        }
    }

    override fun onFailedToRecycleView(smartViewHolder: SmartViewHolder<Any>): Boolean {
        return if (smartViewHolder is RecyclableViewHolder) {
            smartViewHolder.onFailedToRecycleView()
        } else {
            super.onFailedToRecycleView(smartViewHolder)
        }
    }

    override fun onViewAttachedToWindow(holder: SmartViewHolder<Any>) {
        super.onViewAttachedToWindow(holder)
        (holder as? OnViewAttachedToWindowListener)?.onViewAttachedToWindow(holder)
        viewHolderBinders.forEach {
            (it as? OnViewAttachedToWindowListener)?.onViewAttachedToWindow(holder)
        }
    }

    override fun onViewDetachedFromWindow(holder: SmartViewHolder<Any>) {
        super.onViewDetachedFromWindow(holder)
        (holder as? OnViewDetachedFromWindowListener)?.onViewDetachedFromWindow(holder)
        viewHolderBinders.forEach {
            (it as? OnViewAttachedToWindowListener)?.onViewAttachedToWindow(holder)
        }
    }

    override fun getItemCount(): Int = smartItemCount

    override fun <T : Any> getItemCount(type: KClass<out T>): Int {
        return items.count { it::class == type }
    }

    override fun getItem(index: Int): Any = items[index]

    override fun getItems(): MutableList<Any> = items

    override fun <T : Any> getItems(type: KClass<out T>): ArrayList<T> {
        return items.filter {
            it::class == type
        } as ArrayList<T>
    }

    override fun setItems(items: MutableList<*>) {
        setItems(items, true)
    }

    final override fun setItems(items: MutableList<*>, notifyDataSetChanged: Boolean) {
        this.items = items as MutableList<Any>
        if (notifyDataSetChanged) {
            smartNotifyDataSetChanged()
        }
    }

    override fun addItem(item: Any) {
        this.addItem(item, true)
    }

    override fun addItem(item: Any, notifyDataSetChanged: Boolean) {
        this.items.add(item)
        if (notifyDataSetChanged) {
            smartNotifyDataSetChanged()
        }
    }

    override fun addItem(index: Int, item: Any) {
        addItem(index, item, true)
    }

    override fun addItem(index: Int, item: Any, notifyDataSetChanged: Boolean) {
        this.items.add(index, item)
        if (notifyDataSetChanged) {
            smartNotifyItemInserted(index)
        }
    }

    override fun addItems(items: List<Any>) {
        this.addItems(items, true)
    }

    override fun addItems(items: List<Any>, notifyDataSetChanged: Boolean) {
        this.items.addAll(items)
        if (notifyDataSetChanged) {
            smartNotifyItemRangeInserted(itemCount, items.size)
        }
    }

    override fun addItems(index: Int, items: List<Any>) {
        this.addItems(index, items, true)
    }

    override fun addItems(index: Int, items: List<Any>, notifyDataSetChanged: Boolean) {
        this.items.addAll(index, items)
        if (notifyDataSetChanged) {
            smartNotifyItemRangeInserted(index, items.size)
        }
    }

    override fun removeItem(index: Int): Boolean {
        return this.removeItem(index, true)
    }

    override fun removeItem(index: Int, notifyDataSetChanged: Boolean): Boolean {
        if (items.isNotEmpty()) {
            this.items.removeAt(index)
            if (notifyDataSetChanged) {
                smartNotifyItemRemoved(index)
            }
            return true
        }
        return false
    }

    override fun replaceItem(index: Int, item: Any) {
        replaceItem(index, item, true)
    }

    override fun replaceItem(index: Int, item: Any, notifyDataSetChanged: Boolean) {
        this.items[index] = item
        if (notifyDataSetChanged) {
            smartNotifyItemChanged(index)
        }
    }

    override fun clear() {
        this.items.clear()
        smartNotifyDataSetChanged()
    }

    override fun smartNotifyDataSetChanged() {
        updateItemCount()
        notifyDataSetChanged()
    }

    override fun smartNotifyItemChanged(position: Position) {
        updateItemCount()
        notifyItemChanged(position)
    }

    override fun smartNotifyItemRangeChanged(positionStart: Int, itemCount: Int) {
        updateItemCount()
        notifyItemRangeChanged(positionStart, itemCount)
    }

    override fun smartNotifyItemInserted(position: Position) {
        updateItemCount()
        notifyItemInserted(position)
    }

    override fun smartNotifyItemRangeInserted(positionStart: Int, itemCount: Int) {
        updateItemCount()
        notifyItemRangeInserted(positionStart, itemCount)
    }

    override fun smartNotifyItemRemoved(position: Position) {
        updateItemCount()
        notifyItemRemoved(position)
    }

    override fun smartNotifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
        updateItemCount()
        notifyItemRangeRemoved(positionStart, itemCount)
    }

    final override fun updateItemCount() {
        smartItemCount = items.size
    }

    override fun map(itemType: ItemType, viewHolderType: SmartViewHolderType) {
        viewHolderMapper.addMapping(itemType, viewHolderType)
    }

    internal fun setDataTypeViewHolderMapper(dataTypeViewHolderMapper: HashMap<String, SmartViewHolderType>) {
        viewHolderMapper.setDataTypeViewHolderMapper(dataTypeViewHolderMapper)
    }

    internal fun setSmartRecyclerAdapterMapper(smartRecyclerAdapterMapper: HashMap<SmartViewHolderType, SmartRecyclerAdapter>) {
        viewHolderMapper.setSmartRecyclerAdapterMapper(smartRecyclerAdapterMapper)
    }

    override fun addBinder(viewHolderBinder: SmartViewHolderBinder) {
        viewHolderBinders.add(viewHolderBinder)
    }

    companion object {

        /**
         * Builder of [SmartRecyclerAdapter] for easy implementation.
         * @return SmartAdapterBuilder
         */
        fun items(items: List<*>): SmartAdapterBuilder =
            SmartAdapterBuilder(SmartRecyclerAdapter(items.let {
                (if (it.isMutable()) it else it.toMutableList()) as MutableList<Any>
            }))

        /**
         * Builder of [SmartRecyclerAdapter] for easy implementation.
         * @return SmartAdapterBuilder
         */
        fun empty(): SmartAdapterBuilder =
                SmartAdapterBuilder(SmartRecyclerAdapter(mutableListOf()))
    }
}
