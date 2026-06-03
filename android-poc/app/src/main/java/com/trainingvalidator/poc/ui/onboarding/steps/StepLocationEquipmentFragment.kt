package com.trainingvalidator.poc.ui.onboarding.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentStepLocationEquipmentBinding
import com.trainingvalidator.poc.ui.onboarding.OnboardingData
import com.trainingvalidator.poc.ui.onboarding.OnboardingViewModel

/** Step 6 — training location; home reveals an equipment picker, gym implies full equipment. */
class StepLocationEquipmentFragment : Fragment() {

    private var _binding: FragmentStepLocationEquipmentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepLocationEquipmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildEquipmentChips()
        binding.cardHome.setOnClickListener { selectLocation("home") }
        binding.cardGym.setOnClickListener { selectLocation("gym") }
        viewModel.data.value.trainingLocation?.let { renderLocation(it) }
    }

    private fun selectLocation(location: String) {
        if (location == "home" && viewModel.data.value.availableEquipment.isEmpty()) {
            // Ensure the step is satisfiable with no extra gear.
            viewModel.toggleEquipment("bodyweight", true)
            syncChipStates()
        }
        viewModel.setTrainingLocation(location)
        renderLocation(location)
    }

    private fun renderLocation(location: String) {
        val isHome = location == "home"
        binding.cardHome.isChecked = isHome
        binding.cardGym.isChecked = !isHome
        tint(binding.iconHome, isHome)
        tint(binding.iconGym, !isHome)
        binding.equipmentSection.visibility = if (isHome) View.VISIBLE else View.GONE
        if (isHome) syncChipStates()
    }

    private fun buildEquipmentChips() {
        if (binding.chipGroupEquipment.childCount > 0) return
        for (code in OnboardingData.HOME_EQUIPMENT) {
            val chip = layoutInflater.inflate(
                R.layout.view_weekday_chip, binding.chipGroupEquipment, false
            ) as Chip
            chip.text = equipmentLabel(code)
            chip.tag = code
            chip.isChecked = viewModel.data.value.availableEquipment.contains(code)
            chip.setOnClickListener {
                viewModel.toggleEquipment(code, chip.isChecked)
            }
            binding.chipGroupEquipment.addView(chip)
        }
    }

    private fun syncChipStates() {
        val selected = viewModel.data.value.availableEquipment
        for (i in 0 until binding.chipGroupEquipment.childCount) {
            val chip = binding.chipGroupEquipment.getChildAt(i) as? Chip ?: continue
            val code = chip.tag as? String ?: continue
            chip.isChecked = selected.contains(code)
        }
    }

    private fun equipmentLabel(code: String): String {
        val resId = when (code) {
            "bodyweight" -> R.string.onb_equip_bodyweight
            "dumbbell" -> R.string.onb_equip_dumbbell
            "bands" -> R.string.onb_equip_bands
            "kettlebell" -> R.string.onb_equip_kettlebell
            "pull_up_bar" -> R.string.onb_equip_pull_up_bar
            "bench" -> R.string.onb_equip_bench
            "mat" -> R.string.onb_equip_mat
            else -> return code
        }
        return getString(resId)
    }

    private fun tint(icon: android.widget.ImageView, selected: Boolean) {
        val color = ContextCompat.getColor(
            requireContext(),
            if (selected) R.color.primary else R.color.text_secondary
        )
        icon.setColorFilter(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
