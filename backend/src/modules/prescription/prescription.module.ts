import { Module } from '@nestjs/common';
import { PrescriptionController } from './prescription.controller';

@Module({
  controllers: [PrescriptionController],
})
export class PrescriptionModule {}
