package com.acel.streamlivetool.platform.bilibili

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.acel.streamlivetool.R
import com.acel.streamlivetool.bean.Anchor
import com.acel.streamlivetool.platform.IPlatform
import com.acel.streamlivetool.platform.bean.AnchorsCookieMode
import com.acel.streamlivetool.platform.bean.ResultUpdateAnchorByCookie
import com.acel.streamlivetool.platform.bilibili.bean.RoomInfo
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Type
import java.util.*

class BilibiliImpl : IPlatform {
    companion object {
        val INSTANCE by lazy {
            BilibiliImpl()
        }
    }

    override val platform: String = "bilibili"
    override val platformShowNameRes: Int = R.string.bilibili
    override val supportCookieMode: Boolean = true

    private val bilibiliService: BilibiliApi = retrofit.create(BilibiliApi::class.java)
    override fun getAnchor(queryAnchor: Anchor): Anchor? {
        val gson = GsonBuilder().registerTypeAdapter(RoomInfo::class.java,
            object : JsonDeserializer<RoomInfo> {
                override fun deserialize(
                    json: JsonElement,
                    typeOfT: Type,
                    context: JsonDeserializationContext?
                ): RoomInfo {
                    val jsonObject = json.asJsonObject
                    val data = jsonObject.get("data")
                    return if (data.isJsonObject)
                        Gson().fromJson<RoomInfo>(json.toString(), RoomInfo::class.java)
                    else {
                        val code = jsonObject.get("code").asInt
                        val message = jsonObject.get("message").asString
                        val msg = jsonObject.get("msg").asString
                        RoomInfo(code, null, message, msg)
                    }
                }
            }).create()

        val json = bilibiliService.getRoomInfo(queryAnchor.showId).execute().body()
        val roomInfo = gson.fromJson<RoomInfo>(json, RoomInfo::class.java)
        return if (roomInfo?.code == 0) {
            val roomId = roomInfo.data?.room_id
            val ownerName = roomId?.toLong()?.let { getAnchorName(it) }
            Anchor(platform, ownerName.toString(), roomId.toString(), roomId.toString())
        } else
            null
    }

    private fun getAnchorName(roomId: Long): String? {
//        val staticRoomInfo = bilibiliService.getStaticInfo(roomId).execute().body()
//        return staticRoomInfo?.data?.uname
        val h5Info =
            bilibiliService.getH5InfoByRoom(roomId).execute().body()
        return h5Info?.data?.anchor_info?.base_info?.uname
    }

    override fun updateAnchorData(queryAnchor: Anchor): Boolean {
        val h5Info =
            bilibiliService.getH5InfoByRoom(queryAnchor.roomId.toLong()).execute().body()
        return if (h5Info?.code == 0) {
            queryAnchor.apply {
                status = h5Info.data.room_info.live_status == 1
                title = h5Info.data.room_info.title
                avatar = h5Info.data.anchor_info.base_info.face
                keyFrame = h5Info.data.room_info.cover
                typeName = h5Info.data.room_info.area_name
            }
            true
        } else false
    }

    override fun supportUpdateAnchorsByCookie(): Boolean = true

    @Suppress("DeferredResultUnused")
    override fun updateAnchorsDataByCookie(queryList: List<Anchor>): ResultUpdateAnchorByCookie {
        getCookie().let { cookie ->
            if (cookie.isEmpty())
                return super.updateAnchorsDataByCookie(queryList)
            var cookieOk = true
            var message = ""
            val failedList = Collections.synchronizedList(mutableListOf<Anchor>()).also {
                it.addAll(queryList)
            }
            runBlocking {
                async(Dispatchers.IO) {
                    val result = bilibiliService.liveAnchor(cookie).execute().body()
                    result?.let liveLet@{
                        if (result.code != 0) {
                            cookieOk = false
                            message = result.message
                        }
                        if (it.data.total_count == 0)
                            return@liveLet
                        val rooms = result.data.rooms
                        queryList.forEach goOn@{ anchor ->
                            rooms.forEach { room ->
                                if (room.roomid.toString() == anchor.roomId) {
                                    anchor.apply {
                                        status = true
                                        title = room.title
                                        avatar = room.face
                                        keyFrame = room.cover
                                        typeName = room.live_tag_name
                                    }
                                    failedList.remove(anchor)
                                    return@goOn
                                }
                            }
                        }
                    }
                }
                async(Dispatchers.IO) {
                    val result = bilibiliService.unLiveAnchor(cookie).execute().body()
                    if (result?.data?.total_count == 0)
                        return@async
                    val rooms = result?.data?.rooms
                    queryList.forEach goOn@{ anchor ->
                        rooms?.forEach { room ->
                            if (room.roomid.toString() == anchor.roomId) {
                                anchor.apply {
                                    status = false
                                    title = "${room.live_desc} 直播了 ${room.area_v2_name}"
                                    avatar = room.face
                                    typeName = room.area_v2_name
                                }
                                failedList.remove(anchor)
                                return@goOn
                            }
                        }
                    }
                }
            }
            failedList.setHintWhenFollowListDidNotContainsTheAnchor()
            return ResultUpdateAnchorByCookie(cookieOk, message)
        }
    }

    override fun getStreamingLiveUrl(queryAnchor: Anchor): String? {
        val roomPlayInfo = bilibiliService.getRoomPlayInfo(queryAnchor.roomId).execute().body()
        return if (roomPlayInfo != null) {
            if (roomPlayInfo.code == 0) {
                roomPlayInfo.data.play_url.durl[0].url
            } else
                null
        } else
            null
    }

    override fun startApp(context: Context, anchor: Anchor) {
        val intent = Intent()
        val uri = Uri.parse("bilibili://live/${anchor.roomId}")
        intent.data = uri
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        intent.action = "android.intent.action.VIEW"
        context.startActivity(intent)
    }

    override fun searchAnchor(keyword: String): List<Anchor>? {
        val result = bilibiliService.search(keyword).execute().body()
        val list = mutableListOf<Anchor>()
        result?.apply {
            val resultList = result.data.result
            resultList.forEach {
                list.add(
                    Anchor(
                        platform,
                        it.uname.replace("<em class=\"keyword\">", "").replace("</em>", ""),
                        it.roomid.toString(),
                        it.roomid.toString(),
                        it.is_live,
                        avatar = "http:${it.uface}"
                    )
                )
            }
        }
        return list
    }

    override fun getAnchorsWithCookieMode(): AnchorsCookieMode {
        getCookie().run {
            if (this.isEmpty())
                return super.getAnchorsWithCookieMode()
            else {
                var cookieOk = true
                val list = mutableListOf<Anchor>()
                var page = 1
                while (true) {
                    if (page >= 10)
                        break
                    val livingList = bilibiliService.getLivingList(this, page).execute().body()
                    if (livingList?.code == 401) {
                        cookieOk = false
                        break
                    }
                    livingList?.data?.rooms?.forEach {
                        list.add(
                            Anchor(
                                platform = platform,
                                nickname = it.uname,
                                showId = it.roomid.toString(),
                                roomId = it.roomid.toString(),
                                status = it.live_status == 1,
                                title = it.title,
                                avatar = it.face,
                                keyFrame = it.keyframe,
                                typeName = it.area_v2_name
                            )
                        )
                    }
                    val count = livingList?.data?.count
                    if (count != null)
                        if (list.size >= count)
                            break
                    page++
                }
                return AnchorsCookieMode(cookieOk, list)
            }
        }
    }

    override fun checkLoginOk(cookie: String): Boolean {
        if (cookie.contains("SESSDATA") && cookie.contains("DedeUserID"))
            return true
        return false
    }

    override fun getLoginUrl(): String {
        return "https://passport.bilibili.com/login"
    }

}