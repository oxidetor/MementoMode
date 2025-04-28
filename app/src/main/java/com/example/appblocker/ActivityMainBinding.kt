package com.example.appblocker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.FrameLayout

/**
 * Manual ViewBinding class for MainActivity
 */
class ActivityMainBinding private constructor(
    val root: ConstraintLayout,
    val toolbar: MaterialToolbar,
    val bottomNavigation: BottomNavigationView,
    val fragmentContainer: FrameLayout
) {
    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): ActivityMainBinding {
            val root = inflater.inflate(R.layout.activity_main, parent, attachToParent) as ConstraintLayout
            return bind(root)
        }

        fun inflate(inflater: LayoutInflater): ActivityMainBinding {
            return inflate(inflater, null, false)
        }

        fun bind(view: View): ActivityMainBinding {
            return ActivityMainBinding(
                root = view as ConstraintLayout,
                toolbar = view.findViewById(R.id.toolbar),
                bottomNavigation = view.findViewById(R.id.bottomNavigation),
                fragmentContainer = view.findViewById(R.id.fragment_container)
            )
        }
    }
} 