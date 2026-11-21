package com.campusbite.ui

import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.campusbite.databinding.FragmentFormBinding
import com.campusbite.viewmodel.CampusViewModel
import com.google.android.material.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class LoginFragment : Fragment() {
    private var _binding: FragmentFormBinding? = null
    private val binding get() = _binding
    private val vm: CampusViewModel by activityViewModels()
    private var register = false

    // Only non-secret fields survive configuration changes. Passwords are never
    // written to the saved instance state bundle.
    private var savedName = ""
    private var savedEmail = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        register = state?.getBoolean("register") ?: false
        savedName = state?.getString("name") ?: ""
        savedEmail = state?.getString("email") ?: ""
        return FragmentFormBinding.inflate(inflater, container, false).also { _binding = it }.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { render() }
    private fun render() {
        val box = binding?.content ?: return
        box.removeAllViews()
        box.label("CAMPUSBITE  ✦  YOUR CAMPUS, YOUR TABLE", 11f, true)
        val bannerCard = MaterialCardView(requireContext()).apply {
            radius = requireContext().dp(24).toFloat()
            cardElevation = requireContext().dp(4).toFloat()
            strokeWidth = requireContext().dp(1)
            setStrokeColor(MaterialColors.getColor(this, R.attr.colorOutlineVariant))
        }
        val banner = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        ImageLoader.load(banner, "https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=800&q=80", "Rice")
        bannerCard.addView(banner, ViewGroup.LayoutParams(-1, requireContext().dp(180)))
        box.addView(bannerCard, ViewGroup.LayoutParams(-1, -2))
        box.label(if (register) "A better lunch\nstarts here." else "Good food.\nZero lunch queues.", 32f, true)
        box.label("Find your campus favourites, order ahead and pick up after class.", 15f)

        box.label("Demo mode — no account needed", 13f, true)
        box.button("Continue as Student") { vm.submit { vm.initializeDemo(); vm.demo("STUDENT") } }
        box.button("Continue as Vendor (demo)") { vm.submit { vm.initializeDemo(); vm.demo("VENDOR") } }
        box.button("Continue as Manager (demo)") { vm.submit { vm.initializeDemo(); vm.demo("MANAGER") } }

        box.label("Or sign in to your campus account", 12f)
        val name = if (register) box.field("Your name", savedName) else null

        val email = box.field("Email address", savedEmail, type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = box.field(if (register) "Password · at least 8 characters" else "Password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirmation = if (register) box.field("Confirm password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) else null

        name?.edit?.doAfterTextChanged { savedName = it?.toString().orEmpty() }
        email.edit.doAfterTextChanged {
            savedEmail = it?.toString().orEmpty()
            email.layout.error = if (it.isNullOrBlank() || Patterns.EMAIL_ADDRESS.matcher(it.toString()).matches()) null else "Enter a valid email address"
        }
        password.edit.doAfterTextChanged {
            password.layout.error = if (it.isNullOrEmpty() || it.length >= if (register) 8 else 6) null
                else "Use at least ${if (register) 8 else 6} characters"
        }
        confirmation?.edit?.doAfterTextChanged {
            confirmation.layout.error = if (it.toString() == password.value) null else "Passwords do not match"
        }

        val signIn = box.button(if (register) "Create account" else "Sign in") {
            vm.submit { vm.authenticate(email.value, password.value, name?.value, confirmation?.value.orEmpty()) }
        }
        signIn.isEnabled = vm.cloudConfigured
        box.button(if (register) "Already have an account? Sign in" else "New here? Create an account") { register = !register; render() }
        if (!register) box.button("Forgot password") { vm.submit("Password reset email sent.") { vm.resetPassword(email.value) } }
        box.label("Your session is kept secure on this device.", 12f)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("register", register)
        outState.putString("name", savedName)
        outState.putString("email", savedEmail)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
