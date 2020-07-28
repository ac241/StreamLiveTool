package com.acel.streamlivetool.ui.main

import android.Manifest
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.acel.streamlivetool.R
import com.acel.streamlivetool.base.BaseActivity
import com.acel.streamlivetool.base.MyApplication
import com.acel.streamlivetool.bean.Anchor
import com.acel.streamlivetool.platform.IPlatform
import com.acel.streamlivetool.platform.PlatformDispatcher
import com.acel.streamlivetool.ui.overlay.ListOverlayWindowManager
import com.acel.streamlivetool.ui.overlay.PlayerOverlayWindowManager
import com.acel.streamlivetool.ui.settings.SettingsActivity
import com.acel.streamlivetool.util.ToastUtil.toast
import com.acel.streamlivetool.util.defaultSharedPreferences
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_main.*
import permissions.dispatcher.*
import kotlin.properties.Delegates

@RuntimePermissions
class MainActivity : BaseActivity() {
    private val groupFragment by lazy { GroupFragment.newInstance() }
    private val cookieFragment by lazy { CookieFragment.newInstance() }
    private val addAnchorFragment by lazy { AddAnchorFragment.instance }
    private val useCookieMode by lazy {
        val platforms = mutableListOf<IPlatform>()
        val sortPlatformArray = MyApplication.application.resources.getStringArray(R.array.platform)
        val showSet = defaultSharedPreferences.getStringSet(
            MyApplication.application.getString(R.string.pref_key_cookie_mode_platform_showable),
            setOf()
        )
        if (showSet != null)
            sortPlatformArray.forEach {
                if (!showSet.contains(it))
                    return@forEach
                val platform = PlatformDispatcher.getPlatformImpl(it)
                if (platform != null) {
                    if (platform.supportCookieMode)
                        platforms.add(platform)
                }
            }
        platforms.size > 0
    }

    override fun createdDo() {
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int {
                return if (useCookieMode) 2 else 1
            }

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> groupFragment
                    1 -> cookieFragment
                    else -> groupFragment
                }
            }
        }
        if (useCookieMode)
            TabLayoutMediator(
                tabLayout,
                viewPager,
                TabLayoutMediator.TabConfigurationStrategy { _, _ ->
                }
            ).attach()
    }

    override fun getResLayoutId(): Int {
        return R.layout.activity_main
    }

    companion object {
        enum class ListItemType {
            Text, Graphic;
        }
    }

    @NeedsPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)
    fun showListOverlayWindow(anchorList: List<Anchor>) {
        ListOverlayWindowManager.instance.toggleShow(
            this,
            anchorList
        )
    }

    @NeedsPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)
    fun showPlayerOverlayWindow(anchor: Anchor) {
        PlayerOverlayWindowManager.instance.play(anchor)
    }

    @Suppress("UNUSED_PARAMETER")
    @OnShowRationale(Manifest.permission.SYSTEM_ALERT_WINDOW)
    internal fun showRationaleForSystemAlertWindow(request: PermissionRequest?) {
    }

    @OnPermissionDenied(Manifest.permission.SYSTEM_ALERT_WINDOW)
    internal fun showDeniedForSystemAlertWindow() {
        toast("无权限")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        onActivityResult(requestCode)
    }

    fun playStream(anchor: Anchor) {
        showPlayerOverlayWindowWithPermissionCheck(anchor)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_main_setting -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.action_cookie_anchor -> {
                showAddAnchorFragment()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showAddAnchorFragment() {
        addAnchorFragment.show(supportFragmentManager, "add_anchor_fragment")
    }

    private var backPressedTime by Delegates.observable(0L) { _, old, new ->
        // 2次的时间间隔小于2秒就退出
        if (new - old < 1000) {
            finish()
        } else {
            toast("双击返回键退出")
        }
    }

    override fun onBackPressed() {
        backPressedTime = System.currentTimeMillis()
    }

}