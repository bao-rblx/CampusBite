package com.campusbite.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.campusbite.R
import com.campusbite.databinding.FragmentFormBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentFormBinding? = null
    private val binding get() = _binding
    private val vm: CampusViewModel by activityViewModels()
    private var register = false
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        register = state?.getBoolean("register") ?: false
        return FragmentFormBinding.inflate(inflater, container, false).also { _binding = it }.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { render() }
    private fun render() {
        val box = binding?.content ?: return
        box.removeAllViews()
        box.label("CAMPUSBITE  /  YOUR CAMPUS, YOUR TABLE", 11f, true)
        box.addView(ImageView(requireContext()).apply { setImageResource(R.drawable.food_rice); scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, ViewGroup.LayoutParams(-1, requireContext().dp(180)))
        box.label(if (register) "A better lunch\nstarts here." else "Good food.\nZero lunch queues.", 34f, true)
        box.label("Find your campus favourites, order ahead and pick up after class.", 15f)
        box.button("Explore the demo") { vm.submit { vm.initializeDemo(); vm.demo() } }
        box.label(if (vm.cloudConfigured) "Or sign in to your campus account" else "Email accounts need a connected Firebase project.", 12f)
        val name = if (register) box.field("Your name") else null
        val email = box.field("Email address", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = box.field("Password · at least 8 characters", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val confirmation = if (register) box.field("Confirm password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) else null
        email.edit.doAfterTextChanged { email.layout.error = if (it.isNullOrBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(it.toString()).matches()) null else "Enter a valid email address" }
        password.edit.doAfterTextChanged { password.layout.error = if (it.isNullOrEmpty() || it.length >= 8) null else "Use at least 8 characters" }
        confirmation?.edit?.doAfterTextChanged { confirmation.layout.error = if (it.toString() == password.value) null else "Passwords do not match" }
        val signIn = box.button(if (register) "Create account" else "Sign in") {
            vm.submit { vm.authenticate(email.value, password.value, name?.value, confirmation?.value.orEmpty()) }
        }
        signIn.isEnabled = vm.cloudConfigured
        box.button(if (register) "Already have an account? Sign in" else "New here? Create an account") { register = !register; render() }
        if (!register) box.button("Forgot password") { vm.submit("Password reset email sent.") { vm.resetPassword(email.value) } }
        box.label(if (vm.cloudConfigured) "Demo mode uses sample food and local orders." else "Demo is ready to use. Email accounts and live ordering become available when Firebase is connected.", 12f)
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("register", register); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
