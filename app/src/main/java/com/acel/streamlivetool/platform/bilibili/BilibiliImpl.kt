package com.acel.streamlivetool.platform.bilibili

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.acel.streamlivetool.R
import com.acel.streamlivetool.bean.Anchor
import com.acel.streamlivetool.bean.AnchorAttribute
import com.acel.streamlivetool.bean.AnchorsCookieMode
import com.acel.streamlivetool.platform.IPlatform

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
//        return getAnchorFromHtml()
        val roomInfo = bilibiliService.getRoomInfo(queryAnchor.showId).execute().body()

        return if (roomInfo?.code == 0) {
            val roomId = roomInfo.data.roomId
            val ownerName = getAnchorName(roomId)
            Anchor(platform, ownerName.toString(), roomId.toString(), roomId.toString())
        } else
            null
    }

    private fun getAnchorName(roomId: Int): String? {
        val staticRoomInfo = bilibiliService.getStaticInfo(roomId).execute().body()
        return staticRoomInfo?.data?.uname
    }

    override fun getAnchorAttribute(queryAnchor: Anchor): AnchorAttribute? {
        val staticRoomInfo =
            bilibiliService.getStaticInfo(queryAnchor.roomId.toInt()).execute().body()
        return if (staticRoomInfo?.code == 0) {
            val roomStatus = staticRoomInfo.data.liveStatus
            AnchorAttribute(
                queryAnchor,
                roomStatus == 1,
                staticRoomInfo.data.title,
                staticRoomInfo.data.face,
                staticRoomInfo.data.userCover
            )
        } else
            null
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

    override fun getAnchorsWithCookieMode(): AnchorsCookieMode {
        readCookie().run {
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
                                keyFrame = it.keyframe
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

    fun getAnchorsWithCookieModeBackup1(): AnchorsCookieMode {
        readCookie().run {
            if (this.isEmpty())
                return super.getAnchorsWithCookieMode()
            else {
                val list = mutableListOf<Anchor>()
                for (i in 1..2) {
                    val following = bilibiliService.getFollowing(this, i).execute().body()
                    following?.data?.list?.forEach {
                        list.add(
                            Anchor(
                                platform = platform,
                                nickname = it.uname,
                                showId = it.roomid.toString(),
                                roomId = it.roomid.toString(),
                                status = it.live_status == 1,
                                title = it.title,
                                avatar = it.face,
                                keyFrame = "todo"
                            )
                        )
                    }
                }
                return if (list.size != 0)
                    AnchorsCookieMode(true, list)
                else
                    super.getAnchorsWithCookieMode()
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