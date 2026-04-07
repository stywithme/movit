package com.trainingvalidator.poc.ui.report

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.storage.ReportStorage
import com.trainingvalidator.poc.training.report.PostTrainingReport

/**
 * Wraps a single exercise's full report inside a multi-exercise vertical pager.
 *
 * Each instance loads its [PostTrainingReport] from [ReportStorage] by ID and
 * sets up a horizontal [ViewPager2] with [RepPagerAdapter] — reusing all
 * existing report page fragments (Hero, Overview, Best/Worst, Form, Safety,
 * Control, Tips).
 */
class ExerciseReportContainerFragment : Fragment() {

    companion object {
        private const val TAG = "ExerciseReportContainer"
        private const val ARG_REPORT_ID = "report_id"
        private const val ARG_IS_ARABIC = "is_arabic"

        fun newInstance(reportId: String, isArabic: Boolean): ExerciseReportContainerFragment {
            return ExerciseReportContainerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_REPORT_ID, reportId)
                    putBoolean(ARG_IS_ARABIC, isArabic)
                }
            }
        }
    }

    private var reportId: String = ""
    private var isArabic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reportId = arguments?.getString(ARG_REPORT_ID) ?: ""
        isArabic = arguments?.getBoolean(ARG_IS_ARABIC) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_exercise_report_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = view.findViewById<ViewPager2>(R.id.viewPagerPages)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressLoading)

        if (reportId.isBlank()) {
            Log.e(TAG, "No report ID provided")
            return
        }

        progressBar.visibility = View.VISIBLE

        val report = ReportStorage(requireContext()).getById(reportId)
        if (report != null) {
            setupPager(viewPager, report)
        } else {
            Log.w(TAG, "Report not found: $reportId")
        }
        progressBar.visibility = View.GONE
    }

    private fun setupPager(viewPager: ViewPager2, report: PostTrainingReport) {
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        viewPager.adapter = RepPagerAdapter(this, report, isArabic)
    }
}
