package com.acel.streamlivetool.util

import com.acel.streamlivetool.bean.Anchor
import com.acel.streamlivetool.ui.main.adapter.AnchorPlaceHolder

object AnchorListUtil {
    /**
     * 对anchor list排序
     */
    fun sortAnchorListByStatus(anchorList: MutableList<Anchor>) {
        //状态排序
        anchorList.sortWith(Comparator { o1, o2 ->
            if (o1.status == o2.status)
                return@Comparator 0
            if (o2.status)
                return@Comparator 1
            if (o1.status)
                return@Comparator -1
            return@Comparator 0
        })
        //ID再排序一次
        anchorList.sortWith(Comparator { o1, o2 ->
            if (o1.status == o2.status) {
                if (o1.id < o2.id)
                    return@Comparator -1
                else
                    return@Comparator 1
            } else {
                return@Comparator 0
            }
        })
    }

    /**
     * 插入直播状态分组提示
     */
    fun insertStatusPlaceHolder(list: MutableList<Anchor>) {
        list.remove(AnchorPlaceHolder.anchorIsLiving)
        list.remove(AnchorPlaceHolder.anchorNotLiving)
        var livingIndex: Int = -1
        var sleepingIndex: Int = -1
        run breaking@{
            list.forEachIndexed { index, anchor ->
                if (anchor.status) {
                    if (livingIndex == -1)
                        livingIndex = 0
                } else {
                    sleepingIndex = index
                    return@breaking
                }
            }
        }
        if (livingIndex != -1)
            list.add(livingIndex, AnchorPlaceHolder.anchorIsLiving)
        if (sleepingIndex != -1)
            list.add(
                if (list.contains(AnchorPlaceHolder.anchorIsLiving)) sleepingIndex + 1 else sleepingIndex,
                AnchorPlaceHolder.anchorNotLiving
            )
    }

    /**
     * 获取直播中的主播
     */
    fun getLivingAnchors(anchorList: List<Anchor>): List<Anchor> {
        val list = mutableListOf<Anchor>()
        anchorList.forEach {
            if (it.status)
                list.add(it)
        }
        return list
    }
}