package com.acel.streamlivetool.platform.impl.douyu

import com.acel.streamlivetool.platform.impl.douyu.bean.*
import com.acel.streamlivetool.platform.impl.douyu.bean.FollowResponse
import retrofit2.Call
import retrofit2.http.*

interface DouyuApi {

    @GET("https://m.douyu.com/{id}")
    fun getRoomInfo(@Path("id") id: String): Call<String>

    @GET("https://open.douyucdn.cn/api/RoomApi/room/{id}")
    fun getRoomInfoFromOpen(@Path("id") id: String): Call<RoomInfo>

    @GET("https://open.douyucdn.cn/api/RoomApi/room/{id}")
    fun getRoomInfoMsg(@Path("id") id: String): Call<RoomInfoMsg>

    @GET("https://www.douyu.com/betard/{id}")
    fun getRoomInfoBetard(@Path("id") id: String): Call<BetardRoomInfo>

    @GET("https://www.douyu.com/swf_api/homeH5Enc")
    fun getH5Enc(@Query("rids") rids: String): Call<H5Enc>

    @Suppress("unused")
    @Headers("Content-Type:application/x-www-form-urlencoded; charset=utf-8")
    @Multipart
    @POST("https://www.douyu.com/lapi/live/getH5Play/{id}")
    fun getLiveInfo(
        @Path("id") id: String,
        @Part("v") v: String,
        @Part("did") did: String,
        @Part("tt") tt: Int,
        @Part("sign") sign: String,
        @Part("cdn") cdn: String = "",
        @Part("iar") iar: String = "1",
        @Part("ive") ive: String = "0",
        @Part("rate") rate: String = "0"
    ): Call<String>

    @FormUrlEncoded
//    @Multipart
    @POST("https://www.douyu.com/lapi/live/getH5Play/{id}")
    fun getLiveInfo(
        @Path("id") id: String,
        @FieldMap map: MutableMap<String, String>
    ): Call<String>


    @GET("https://www.douyu.com/wgapi/livenc/liveweb/follow/list")
    fun getFollowed(
        @Header("Cookie") cookie: String,
        @Query("page") page: Int = 1
    ): Call<Followed>

    @GET("https://www.douyu.com/japi/search/api/getSearchRec")
    fun search(@Query("kw") keyword: String): Call<SearchResult>

    @FormUrlEncoded
    @POST("https://www.douyu.com/wgapi/livenc/liveweb/follow/add")
    fun follow(
        @Header("Cookie") cookie: String,
        @Field("rid") rid: String,
        @Field("ctn") ctn: String
    ): Call<FollowResponse>

    @GET("https://www.douyu.com/curl/csrfApi/getCsrfCookie")
    fun initCsrf(
        @Header("Cookie") cookie: String
    ): Call<String>

}
