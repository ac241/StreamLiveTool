package com.acel.streamlivetool.platform.huya

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.acel.streamlivetool.R
import com.acel.streamlivetool.bean.Anchor
import com.acel.streamlivetool.bean.AnchorStatus
import com.acel.streamlivetool.platform.IPlatform
import com.acel.streamlivetool.platform.egameqq.bean.LongZhuAnchor
import com.acel.streamlivetool.platform.egameqq.bean.Param
import com.acel.streamlivetool.util.TextUtil
import com.google.gson.Gson


object EgameqqImpl : IPlatform {
    override val platform: String = "egameqq"
    override val platformShowNameRes: Int = R.string.egameqq
    val egameqqService = retrofit.create(EgameqqApi::class.java)

    private fun getHtml(queryAnchor: Anchor): String? {
        val html: String? = egameqqService.getHtml(queryAnchor.showId).execute().body()
        return html
    }

    override fun getAnchor(queryAnchor: Anchor): Anchor? {
        val html = getHtml(queryAnchor)
        html?.let {
            val tempRoomId = TextUtil.subString(html, "roomId: '", "'}")
            val roomId = tempRoomId?.split("_")?.get(1)
            queryAnchor.roomId = roomId
            val longZhuAnchor = getLongZhuAnchor(queryAnchor)
            longZhuAnchor?.let {
                return Anchor(
                    platform,
                    it.data.key.retBody.data.nickName,
                    it.data.key.retBody.data.aliasId.toString(),
                    it.data.key.retBody.data.uid.toString()
                )
            }
        }

        return null
    }

    private fun getLongZhuAnchor(queryAnchor: Anchor): LongZhuAnchor? {
        val param =
            Param(Param.Key(param = Param.Key.ParamX(anchorUid = queryAnchor.roomId.toInt())))

        val longZhuAnchor = egameqqService.getAnchor(Gson().toJson(param)).execute().body()
        return longZhuAnchor
    }

    override fun getStatus(queryAnchor: Anchor): AnchorStatus? {
        val longZhuAnchor = getLongZhuAnchor(queryAnchor)

        longZhuAnchor?.let {
            return AnchorStatus(
                queryAnchor.platform,
                queryAnchor.roomId,
                it.data.key.retBody.data
                    .isLive == 1
            )
        }
        return null
    }

    override fun getStreamingLiveUrl(queryAnchor: Anchor): String? {
        val html = getHtml(queryAnchor)
        html?.let {
            return TextUtil.subString(html, "\"playUrl\":\"", "\",")
        }
        return null
    }

    override fun startApp(context: Context, anchor: Anchor) {
        val intent = Intent()
        val uri =
            Uri.parse(
                "qgameapi://video/room?aid=${anchor.roomId}"
            )
        intent.setData(uri)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.setAction(Intent.ACTION_VIEW)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}