package com.acel.streamlivetool.ui.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.acel.streamlivetool.R
import com.acel.streamlivetool.bean.Anchor
import com.acel.streamlivetool.databinding.ActivityPlayerBinding
import com.acel.streamlivetool.net.ImageLoader.loadImage
import com.acel.streamlivetool.platform.PlatformDispatcher.platformImpl
import com.acel.streamlivetool.ui.custom_view.addItemWhiteTextColor
import com.acel.streamlivetool.ui.custom_view.blackAlphaPopupMenu
import com.acel.streamlivetool.util.ToastUtil.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding

    internal val viewModel by viewModels<PlayerViewModel>()
    internal var fullScreen = false
    internal var landscape = false
    private val orientationEventListener by lazy { OrientationEventListener(this) }

    private var emitDanmuJob: Job? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        observeLiveData()
        initView()
        lifecycle.addObserver(ForegroundServiceListener(this))
        viewModel.setAnchorData(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.statusBarColor = Color.TRANSPARENT
            } else {
                window.statusBarColor = Color.BLACK
            }
            window.navigationBarColor = Color.TRANSPARENT
        }
        //自动切换屏幕
        lifecycle.addObserver(orientationEventListener)
    }

    private val danmakuContext = DanmakuContext.create().apply {
        setMaximumVisibleSizeInScreen(0)
        setMaximumLines(mapOf(Pair(BaseDanmaku.TYPE_SCROLL_RL, 10)))
    }

    private val danmakuParser = object : BaseDanmakuParser() {
        override fun parse(): IDanmakus {
            return Danmakus()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { viewModel.setAnchorData(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.screenRotation()
    }

    private fun initView() {
        binding.playerView.apply {
            setControllerVisibilityListener {
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    if (it == View.GONE)
                        hideSystemUI()
            }
            player = viewModel.player
            keepScreenOn = true
            hideController()
            findViewById<View>(R.id.btn_replay)?.setOnClickListener {
                viewModel.replay()
                it.animate().setDuration(1000).rotationBy(-360f).start()
            }
            findViewById<View>(R.id.btn_zoom).setOnClickListener {
                zoomClick()
            }

            findViewById<TextView>(R.id.current_quality).apply {
                setOnClickListener {
                    val qualityList = viewModel.qualityList.value
                    if (qualityList != null && qualityList.isNotEmpty()) {
                        val popupMenu =
                            blackAlphaPopupMenu(this@PlayerActivity, this)
                        popupMenu.menu.apply {
                            viewModel.qualityList.value?.forEach { quality ->
                                addItemWhiteTextColor(quality?.description)
                                    .setOnMenuItemClickListener {
                                        quality?.let { viewModel.playWithAssignQuality(quality) }
                                        true
                                    }
                            }
                        }
                        popupMenu.show()
                    } else {
                        toast("没有更多的清晰度可以选择。")
                    }
                }
            }

        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            landscapeFullScreen()
        binding.platformIcon.apply {
            setOnClickListener {
                val popupMenu = PopupMenu(this@PlayerActivity, this)
                popupMenu.menu.apply {
                    add("打开app").setOnMenuItemClickListener {
                        viewModel.startAppForCurrentAnchor(this@PlayerActivity)
                        viewModel.stopAll()
                        true
                    }
                    add("悬浮窗")
                }
                popupMenu.show()
            }
        }


        binding.danmakuView.apply {
            enableDanmakuDrawingCache(true)
            setCallback(object : DrawHandler.Callback {
                override fun prepared() {
                    start()
                }

                override fun updateTimer(timer: DanmakuTimer?) {
                }

                override fun danmakuShown(danmaku: BaseDanmaku?) {
                }

                override fun drawingFinished() {
                }

            })
            prepare(danmakuParser, danmakuContext)
            alpha = 0.8f
        }
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                return if (position == 0)
                    DanmuListFragment.newInstance()
                else
                    AnchorListFragment.newInstance()
            }
        }
        binding.danmuNotice.setOnClickListener {
            viewModel.restartDanmu("重新连接弹幕服务器")
        }
        viewModel.danmuStatus.observe(this) {
            binding.danmuNotice.text = it.second
            if (it.first == PlayerViewModel.DanmuState.START)
                startEmitDanmuJob()
            if (it.first == PlayerViewModel.DanmuState.ERROR || it.first == PlayerViewModel.DanmuState.STOP) {
                stopEmitDanmuJob()
                binding.danmakuView.clearDanmakusOnScreen()
            }
        }

    }


    private fun zoomClick() {
        if (!fullScreen)
            fullScreen()
        else {
            normalScreen()
        }
    }

    private fun normalScreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding.playerView.layoutParams.apply {
            this as ConstraintLayout.LayoutParams
            height = 0
            width = 0
            dimensionRatio = "16:9"
            binding.playerView.layoutParams = this
        }
        showSystemUI()
        fullScreen = false
        landscape = false
        disableOrientationListener()
    }

    private fun enableOrientationListener() {
        orientationEventListener.enable()
        lifecycle.addObserver(orientationEventListener)
    }

    private fun disableOrientationListener() {
        orientationEventListener.disable()
        lifecycle.removeObserver(orientationEventListener)
    }

    private fun fullScreen() {
        val resolution = viewModel.videoResolution.value
        if (resolution != null) {
            if (resolution.first < resolution.second) {
                portraitFullScreen()
            } else {
                landscapeFullScreen()
            }
        } else {
            landscapeFullScreen()
        }
    }

    /**
     * 横屏全屏
     */
    private fun landscapeFullScreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        binding.playerView.layoutParams.apply {
            height = ConstraintLayout.LayoutParams.MATCH_PARENT
            width = ConstraintLayout.LayoutParams.MATCH_PARENT
            binding.playerView.layoutParams = this
        }
        hideSystemUI()
        fullScreen = true
        landscape = true
        enableOrientationListener()
    }

    /**
     * 竖屏全屏
     */
    private fun portraitFullScreen() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding.playerView.layoutParams.apply {
            this as ConstraintLayout.LayoutParams
            height = ConstraintLayout.LayoutParams.MATCH_PARENT
            width = ConstraintLayout.LayoutParams.MATCH_PARENT
            dimensionRatio = null
            binding.playerView.layoutParams = this
        }
        navigationBlack()
        fullScreen = true
        landscape = false
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT ->
                normalScreen()
            Configuration.ORIENTATION_LANDSCAPE ->
                landscapeFullScreen()
            else -> {
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startDanmuFromActivity()
    }


    override fun onDestroy() {
        super.onDestroy()
        binding.danmakuView.release()
    }

    private fun emitDanma(msg: String) {
        lifecycleScope.launch {
            val danmaku =
                danmakuContext.mDanmakuFactory?.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL)?.apply {
                    text = msg
                    padding = 3
                    time = binding.danmakuView.currentTime.plus(1200)
                    textSize = 16f.toPx()
                    textColor = Color.WHITE
                    textShadowColor = Color.BLACK
                    priority = 1
                }
            danmaku?.let {
                binding.danmakuView.addDanmaku(it)
            }
        }
    }

    /**
     * sp转px的方法。
     */
    private fun Float.toPx(): Float {
        val fontScale = resources.displayMetrics.scaledDensity
        return (this * fontScale + 0.5f)
    }

    private fun observeLiveData() {
        viewModel.apply {
            anchor.observe(this@PlayerActivity) {
                displayAnchorDetail(it)
                viewModel.getAnchorDetails(it)
            }
            anchorDetails.observe(this@PlayerActivity) {
                displayAnchorDetail(it)
            }

            playerStatus.observe(this@PlayerActivity) {
                if (it != null)
                    when (it) {
                        PlayerViewModel.PlayerState.IS_PLAYING -> {
                            binding.progressBar.visibility = View.GONE
                            binding.errorMsg.visibility = View.GONE
                        }
                        PlayerViewModel.PlayerState.IS_LOADING ->
                            binding.progressBar.visibility = View.VISIBLE
                        PlayerViewModel.PlayerState.IS_ENDED,
                        PlayerViewModel.PlayerState.IS_IDLE,
                        PlayerViewModel.PlayerState.IS_ERROR ->
                            binding.progressBar.visibility = View.GONE
                    }
            }
            playerMessage.observe(this@PlayerActivity) {
                if (it.isNotEmpty()) {
                    binding.errorMsg.apply {
                        visibility = View.VISIBLE
                        text = it
                        fadeIn()
                    }
                    binding.progressBar.visibility = View.GONE
                }
            }
            currentQuality.observe(this@PlayerActivity) {
                binding.playerView.findViewById<TextView>(R.id.current_quality).text =
                    it?.description ?: getString(R.string.no_video_quality_to_choose)
            }
        }
    }

    private fun startEmitDanmuJob() {
        emitDanmuJob?.cancel()
        emitDanmuJob = null
        emitDanmuJob = lifecycleScope.launch(Dispatchers.Default) {
            while (true) {
                viewModel.getPreEmitDanmu()?.let {
                    emitDanma(it.msg)
                }
                delay(20)
            }
        }
    }

    private fun stopEmitDanmuJob() {
        emitDanmuJob?.cancel()
        emitDanmuJob = null
    }

    private fun displayAnchorDetail(it: Anchor) {
        it.apply {
            it.avatar?.let { it1 ->
                binding.avatar.loadImage(it1)
                binding.playerView.findViewById<ImageView>(R.id.controller_avatar).loadImage(it1)
            }
            binding.nickname.text = nickname
            binding.playerView.findViewById<TextView>(R.id.controller_nickname).text = nickname
            binding.include.typeName.text = typeName
            binding.roomId.text = getString(R.string.room_id_format, showId)
            binding.title.text = title
            binding.playerView.findViewById<TextView>(R.id.controller_title).text = title
            platformImpl()?.iconRes?.let { res ->
                binding.platformIcon.setImageResource(res)
            }
        }
    }


    private fun View.fadeIn() {
        alpha = 0f
        animate().setDuration(500).alpha(1f)
    }

    override fun onBackPressed() {
        if (fullScreen)
            normalScreen()
        else
            super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    @Suppress("DEPRECATION")
    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_VISIBLE)
    }

    private fun navigationBlack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = Color.BLACK
        }
    }

}