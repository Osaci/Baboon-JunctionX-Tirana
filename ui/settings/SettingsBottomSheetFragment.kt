package com.example.baboonchat.ui.settings

import android.app.Dialog
import android.content.res.ColorStateList
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.baboonchat.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.constraintlayout.widget.ConstraintLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.compose.ui.graphics.Color
import com.example.baboonchat.ui.theme.ThemeManager

/**
 * A bottom sheet dialog fragment that displays app settings
 */
class SettingsBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private var themeChangeListener: ThemeChangeListener? = null
    private var autoScrollChangeListener: AutoScrollChangeListener? = null
    private lateinit var themeManager: ThemeManager

    // Theme colors list for easy reference
    private val themes = listOf(
        ThemeOption("light", "Light"),
        ThemeOption("dark", "Dark"),
        ThemeOption("brown", "Brown"),
        ThemeOption("yellow", "Yellow"),
        ThemeOption("red", "Red"),
        ThemeOption("green", "Green"),
        ThemeOption("purple", "Purple"),
        ThemeOption("cyan", "Cyan")
    )

    // Interfaces for listeners
    interface ThemeChangeListener {
        fun onThemeChanged(theme: String)
    }

    interface AutoScrollChangeListener {
        fun onAutoScrollChanged(enabled: Boolean)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the View
        val view = inflater.inflate(R.layout.fragment_settings_bottom_sheet, container, false)

        // Initialize ThemeManager
        themeManager = ThemeManager(requireContext())

        // Initialize SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Get theme background color
        val modalBgColor = themeManager.getThemeColor(ThemeManager.ThemeColorType.MODAL_BACKGROUND)
        
        // Apply theme to view
        view.setBackgroundColor(modalBgColor)

        return view
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SharedPreferences
        //sharedPreferences = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        //val themeManager = ThemeManager(requireContext())
        themeManager.styleSettingsBottomSheet(view)

        
        // Set up Auto-Scroll toggle
        val autoScrollSwitch = view.findViewById<Switch>(R.id.auto_scroll_switch)
        val isAutoScrollEnabled = sharedPreferences.getBoolean("auto_scroll_enabled", true)
        autoScrollSwitch.isChecked = isAutoScrollEnabled

        autoScrollSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
            sharedPreferences.edit().putBoolean("auto_scroll_enabled", isChecked).apply()
            // Notify listener
            autoScrollChangeListener?.onAutoScrollChanged(isChecked)
        }

        // Set up theme selection
        setupThemeSelectors(view)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // Apply theme colors to dialog once created
        dialog.setOnShowListener { dialogInterface -> 
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let {
                // Get theme colors
                val themeManager = ThemeManager(requireContext())
                val currentTheme = themeManager.getCurrentTheme()
                val modalBgColor = themeManager.getThemeColor(ThemeManager.ThemeColorType.MODAL_BACKGROUND)

                it.setBackgroundColor(modalBgColor)
            }
        }
        return dialog
    }

    private fun setupThemeSelectors(view: View) {
        // Get the current theme
        val currentTheme = sharedPreferences.getString("app_theme", "light") ?: "light"

        // First row of themes (light, dark, brown, yellow)
        setupThemeOption(view, R.id.light_theme_card, R.id.light_theme_indicator, "light", currentTheme)
        setupThemeOption(view, R.id.dark_theme_card, R.id.dark_theme_indicator, "dark", currentTheme)
        setupThemeOption(view, R.id.brown_theme_card, R.id.brown_theme_indicator, "brown", currentTheme)
        setupThemeOption(view, R.id.yellow_theme_card, R.id.yellow_theme_indicator, "yellow", currentTheme)

        // Second row of themes (red, green, purple, cyan)
        setupThemeOption(view, R.id.red_theme_card, R.id.red_theme_indicator, "red", currentTheme)
        setupThemeOption(view, R.id.green_theme_card, R.id.green_theme_indicator, "green", currentTheme)
        setupThemeOption(view, R.id.purple_theme_card, R.id.purple_theme_indicator, "purple", currentTheme)
        setupThemeOption(view, R.id.cyan_theme_card, R.id.cyan_theme_indicator, "cyan", currentTheme)
    }

    private fun setupThemeOption(
        view: View,
        cardViewId: Int,
        indicatorId: Int,
        themeName: String,
        currentTheme: String
    ) {
        val themeCard = view.findViewById<CardView>(cardViewId)
        val themeIndicator = view.findViewById<ImageView>(indicatorId)

        // Show selection indicator if this is the current theme
        themeIndicator.visibility = if (themeName == currentTheme) View.VISIBLE else View.INVISIBLE

        themeCard.setOnClickListener {
            // Save theme preference
            sharedPreferences.edit().putString("app_theme", themeName).apply()

            // Update all indicators (hide all, then show the selected one)
            themes.forEach { theme ->
                val indicator = when (theme.id) {
                    "light" -> view.findViewById<ImageView>(R.id.light_theme_indicator)
                    "dark" -> view.findViewById<ImageView>(R.id.dark_theme_indicator)
                    "brown" -> view.findViewById<ImageView>(R.id.brown_theme_indicator)
                    "yellow" -> view.findViewById<ImageView>(R.id.yellow_theme_indicator)
                    "red" -> view.findViewById<ImageView>(R.id.red_theme_indicator)
                    "green" -> view.findViewById<ImageView>(R.id.green_theme_indicator)
                    "purple" -> view.findViewById<ImageView>(R.id.purple_theme_indicator)
                    "cyan" -> view.findViewById<ImageView>(R.id.cyan_theme_indicator)
                    else -> null
                }
                indicator?.visibility = if (theme.id == themeName) View.VISIBLE else View.INVISIBLE
            }
            
            val themeManager = ThemeManager(requireContext())
            themeManager.styleSettingsBottomSheet(view)

            // Notify listener about theme change
            themeChangeListener?.onThemeChanged(themeName)
        }
    }

    fun setThemeChangeListener(listener: ThemeChangeListener) {
        this.themeChangeListener = listener
    }

    fun setAutoScrollChangeListener(listener: AutoScrollChangeListener) {
        this.autoScrollChangeListener = listener
    }

    companion object {
        const val TAG = "SettingsBottomSheet"

        fun newInstance(): SettingsBottomSheetFragment {
            return SettingsBottomSheetFragment()
        }
    }

    // Theme option data class for organization
    data class ThemeOption(val id: String, val displayName: String)
}