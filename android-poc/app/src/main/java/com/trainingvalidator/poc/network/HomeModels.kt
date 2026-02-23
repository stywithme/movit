package com.trainingvalidator.poc.network

data class HomeResponse(
    val success: Boolean,
    val data: HomeData?,
    val timestamp: String,
    val error: String? = null
)

data class HomeData(
    val userStats: UserStatsData?,
    val levelProfile: LevelProfileData?,
    val activePlan: ActivePlanData?,
    val todayPlan: TodayPlanData?
)
