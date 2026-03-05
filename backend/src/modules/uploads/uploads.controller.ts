import {
  Body,
  Controller,
  Delete,
  Post,
  Query,
  Res,
  UploadedFile,
  UseInterceptors,
  UseGuards,
} from '@nestjs/common';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import type { Response } from 'express';
import { FileInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
import { randomUUID } from 'crypto';
import {
  buildObjectName,
  deleteObjectFromGcs,
  getUploadLimits,
  isAllowedMime,
  parseObjectNameFromUrl,
  uploadBufferToGcs,
  type UploadCategory,
} from '@/lib/storage';

function resolveCategory(value?: string): UploadCategory | null {
  if (
    value === 'camera-position-image' ||
    value === 'exercise-image' ||
    value === 'exercise-audio'
  ) {
    return value;
  }
  return null;
}

@UseGuards(CaslGuard)
@Controller('uploads')
export class UploadsController {
  @Post()
  @CheckPermission('create', 'Upload')
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      limits: { fileSize: 30 * 1024 * 1024 },
    })
  )
  async upload(
    @Query('type') type: string,
    @UploadedFile() file: Express.Multer.File | undefined,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const category = resolveCategory(type);
      if (!category) {
        res.status(400);
        return { success: false, error: 'Invalid upload type' };
      }

      if (!file) {
        res.status(400);
        return { success: false, error: 'File is required' };
      }

      if (!isAllowedMime(category, file.mimetype)) {
        res.status(400);
        return { success: false, error: 'Unsupported file type' };
      }

      const maxBytes = getUploadLimits(category);
      if (file.size > maxBytes) {
        res.status(400);
        return {
          success: false,
          error: `File exceeds ${Math.round(maxBytes / 1024 / 1024)}MB limit`,
        };
      }

      const id = randomUUID();
      const objectName = buildObjectName(category, file.originalname, id);
      const result = await uploadBufferToGcs(objectName, file.buffer, file.mimetype);

      return {
        success: true,
        data: {
          url: result.url,
          objectName: result.objectName,
          size: file.size,
          contentType: file.mimetype,
        },
      };
    } catch (error) {
      console.error('Upload error:', error);
      res.status(500);
      return { success: false, error: 'Upload failed' };
    }
  }

  @Delete()
  @CheckPermission('delete', 'Upload')
  async remove(@Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      const objectName = body?.objectName || (body?.url ? parseObjectNameFromUrl(body.url) : null);
      if (!objectName) {
        res.status(400);
        return { success: false, error: 'objectName or url is required' };
      }

      await deleteObjectFromGcs(objectName);
      return { success: true, message: 'File deleted' };
    } catch (error) {
      console.error('Delete error:', error);
      res.status(500);
      return { success: false, error: 'Delete failed' };
    }
  }
}
