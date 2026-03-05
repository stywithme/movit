// casl.types.ts

// These should match the string values in the database permissions
export type Action = 'manage' | 'read' | 'create' | 'update' | 'delete' | 'publish' | 'duplicate';

export type Subject =
    | 'Admin'
    | 'Role'
    | 'User'
    | 'Exercise'
    | 'Workout'
    | 'Program'
    | 'ProgramMap'
    | 'ProgramAnalytics'
    | 'Recipe'
    | 'MealPlan'
    | 'TrainingProvider'
    | 'Muscle'
    | 'Equipment'
    | 'Level'
    | 'LevelAnalytics'
    | 'AssessmentTemplate'
    | 'AssessmentAnalytics'
    | 'Reassessment'
    | 'ProgressionRule'
    | 'Config'
    | 'Reports'
    | 'Analytics'
    | 'Attribute'
    | 'PosePosition'
    | 'FeedbackMessage'
    | 'Upload'
    | 'all'; // 'all' is a special subject for wildcards
