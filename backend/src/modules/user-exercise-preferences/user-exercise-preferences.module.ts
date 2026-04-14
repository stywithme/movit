import { Module } from '@nestjs/common';
import { MobileExercisePreferencesController } from './mobile-exercise-preferences.controller';

@Module({
  controllers: [MobileExercisePreferencesController],
})
export class UserExercisePreferencesModule {}
