package com.campusbite.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavOptions
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import com.campusbite.R
import com.campusbite.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: CampusViewModel by viewModels()
    private var messageBar: Snackbar? = null
    private val controller get() = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge (targetSdk 35+): keep content clear of the status bar.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, keyboard.bottom), left = bars.left, right = bars.right)
            WindowInsetsCompat.CONSUMED
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (controller.currentDestination?.id == R.id.detailFragment) controller.popBackStack()
            androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, controller)
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (controller.currentDestination?.id != item.itemId) {
                if (!controller.popBackStack(item.itemId, false)) {
                    androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, controller)
                }
            }
        }
        navHost.navController.addOnDestinationChangedListener { _, _, _ ->
            messageBar?.dismiss()
            androidx.core.view.WindowInsetsControllerCompat(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.uid.collect { uid ->
                binding.bottomNav.show(uid != null)
                val destination = controller.currentDestination?.id
                if (uid == null && destination != R.id.loginFragment) controller.navigate(R.id.loginFragment, null, NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build())
                else if (uid != null && destination == R.id.loginFragment) controller.navigate(R.id.homeFragment, null, NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build())
            } }
            launch { vm.state.collect { state ->
                val count = state.cart.sumOf { it.quantity }
                if (count == 0) binding.bottomNav.removeBadge(R.id.cartFragment) else binding.bottomNav.getOrCreateBadge(R.id.cartFragment).number = count
            } }
            launch { vm.messages.collect {
                messageBar?.dismiss()
                messageBar = Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).apply {
                    if (binding.bottomNav.visibility == android.view.View.VISIBLE) anchorView = binding.bottomNav
                    show()
                }
            } }
        } }
    }

    fun selectTab(id: Int, savedOnly: Boolean = false) {
        if (controller.currentDestination?.id == R.id.detailFragment) controller.popBackStack()
        binding.bottomNav.selectedItemId = id
        if (savedOnly) binding.root.post {
            val host = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
            (host.childFragmentManager.primaryNavigationFragment as? DashboardFragment)?.showSaved()
        }
    }
}
