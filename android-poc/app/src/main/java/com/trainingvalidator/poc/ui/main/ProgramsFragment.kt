package com.trainingvalidator.poc.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.databinding.FragmentProgramsBinding
import com.trainingvalidator.poc.ui.ProgramListActivity

/**
 * ProgramsFragment - entry point for program browsing.
 */
class ProgramsFragment : Fragment() {

    private var _binding: FragmentProgramsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgramsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBrowsePrograms.setOnClickListener {
            startActivity(Intent(requireContext(), ProgramListActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
