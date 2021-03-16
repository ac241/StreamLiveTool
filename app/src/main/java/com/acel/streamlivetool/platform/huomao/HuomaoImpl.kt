package com.acel.streamlivetool.platform.huomao

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.acel.streamlivetool.R
import com.acel.streamlivetool.bean.Anchor
import com.acel.streamlivetool.bean.StreamingLive
import com.acel.streamlivetool.platform.IPlatform
import com.acel.streamlivetool.platform.bean.ResultGetAnchorListByCookieMode
import com.acel.streamlivetool.platform.huomao.bean.RoomInfo
import com.acel.streamlivetool.util.AnchorUtil
import com.acel.streamlivetool.util.TextUtil
import com.acel.streamlivetool.util.UnicodeUtil
import com.google.gson.Gson
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*


class HuomaoImpl : IPlatform {
    companion object {
        val INSTANCE by lazy {
            HuomaoImpl()
        }
        private const val SECRETKEY = "6FE26D855E1AEAE090E243EB1AF73685"
    }

    override val platform: String = "huomao"
    override val platformShowNameRes: Int = R.string.huomao
    override val iconRes: Int = R.drawable.ic_huomao
    override val supportCookieMode: Boolean = true
    private val huomaoService: HuomaoApi = retrofit.create(HuomaoApi::class.java)

    override fun getAnchor(queryAnchor: Anchor): Anchor? {
//        return getAnchorFromHtml()
        val roomInfo = getRoomInfo(queryAnchor)
        return if (roomInfo != null) {
            Anchor(
                platform,
                UnicodeUtil.decodeUnicode(roomInfo.nickname),
                roomInfo.roomNumber,
                roomInfo.id
            )
        } else {
            null
        }
    }

    private fun getRoomInfo(queryAnchor: Anchor): RoomInfo? {
        val html = huomaoService.getRoomInfo(queryAnchor.showId).execute().body()
        val channelOneInfo = html?.let {
            val temp = TextUtil.subString(it, "channelOneInfo = ", "};")
            if (temp == null) null else "$temp}"
        }
        return if (channelOneInfo != null)
            Gson().fromJson(channelOneInfo, RoomInfo::class.java)
        else
            null
    }

    override fun updateAnchorData(queryAnchor: Anchor): Boolean {
        val roomInfo: RoomInfo? = getRoomInfo(queryAnchor)
        return if (roomInfo != null) {
            queryAnchor.apply {
                status = roomInfo.isLive == 1
                title = UnicodeUtil.decodeUnicode(roomInfo.channel)
                avatar = roomInfo.headimg.big
                keyFrame = roomInfo.image
                typeName = roomInfo.gameCname
                online = AnchorUtil.formatOnlineNumber(roomInfo.views)
            }
            true
        } else false
    }

    override fun supportCookieMode(): Boolean = true

    override fun getStreamingLive(
        queryAnchor: Anchor,
        queryQuality: StreamingLive.Quality?
    ): StreamingLive? {
        val tagFrom = "huomaoh5room"
        val time = (Date().time / 1000).toString()
        val roomInfo = getRoomInfo(queryAnchor)
        if (roomInfo != null) {
            val stream = roomInfo.stream
            val signStr = stream + tagFrom + time + SECRETKEY
            val md = MessageDigest.getInstance("MD5")
            //对字符串加密
            md.update(signStr.toByteArray())
            val secretBytes = md.digest()
            val token = BigInteger(1, secretBytes).toString(16)
            val formMap = mutableMapOf<String, String>()
            formMap["streamtype"] = "live"
            formMap["VideoIDS"] = stream
            formMap["time"] = time
            formMap["cdns"] = "1"
            formMap["from"] = tagFrom
            formMap["token"] = token
            val liveData = huomaoService.getLiveData(formMap).execute().body()
            liveData?.streamList?.get(0)?.list?.forEach {
                if (it.type == "BD")
                    return StreamingLive(url = it.url, null, null)
            }
        }
        return null
    }

    override fun startApp(context: Context, anchor: Anchor) {
        val intent = Intent()
        val uri = Uri.parse("sharehuomao://huomao/scheme?cid=${anchor.roomId}&type=1&screenType=0")
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.action = "android.intent.action.VIEW"
        context.startActivity(intent)
    }

    override fun searchAnchor(keyword: String): List<Anchor>? {
        val result =
            UnicodeUtil.cnToUnicode(keyword)?.let { huomaoService.search(it).execute().body() }
        val list = mutableListOf<Anchor>()
        result?.apply {
            val resultList = result.data.anchor.list
            resultList.forEach {
                list.add(
                    Anchor(
                        platform = platform,
                        nickname = it.nickname.replace(
                            "<i style=\"color: red;font-style: normal\">",
                            ""
                        )
                            .replace("</i>", ""),
                        showId = it.room_number,
                        roomId = it.cid,
                        status = it.is_live == 1,
                        avatar = it.img.big
                    )
                )
            }
        }
        return list
    }

    override fun getAnchorsByCookieMode(): ResultGetAnchorListByCookieMode {
        if (getCookie().isEmpty())
            return super.getAnchorsByCookieMode()
        val subscribe = huomaoService.getUsersSubscribe(getCookie()).execute().body()
        subscribe?.let {
            val list = subscribe.data.usersSubChannels
            val anchorList = mutableListOf<Anchor>()
            list.forEach {
                anchorList.add(
                    Anchor(
                        platform = platform,
                        nickname = it.nickname,
                        showId = it.room_number,
                        roomId = it.id,
                        status = it.is_live == 1,
                        title = it.channel,
                        avatar = it.headimg.big,
                        keyFrame = it.image,
                        typeName = it.gameCname,
                        online = it.views,
                        liveTime = it.event_starttime
                    )
                )
            }
            return ResultGetAnchorListByCookieMode(
                success = true,
                isCookieValid = true,
                anchorList = anchorList
            )
        }
        return super.getAnchorsByCookieMode()
    }

    override fun getLoginUrl(): String {
        return "https://www.huomao.com/channel/all"
    }

    override fun checkLoginOk(cookie: String): Boolean {
        return cookie.contains("user_")
    }

    override fun loginWithPcAgent(): Boolean = true
}