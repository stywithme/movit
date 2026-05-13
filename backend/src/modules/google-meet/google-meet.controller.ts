/**
 * Google Meet Controller
 * =======================
 * Exposes HTTP endpoints for per-doctor Google Meet OAuth management.
 *
 * All routes are under /admin/google-meet
 * - GET  /status          → current connection status for the logged-in doctor/admin
 * - GET  /connect         → start OAuth flow (redirect to Google)
 * - GET  /callback        → OAuth callback (Google redirects here with code+state)
 * - POST /disconnect      → unlink Google account
 * - GET  /status/:adminId → super-admin read-only view of a doctor's status
 */

import {
    Controller,
    Get,
    Post,
    Param,
    Query,
    Req,
    Res,
    UseGuards,
    ForbiddenException,
    Logger,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { GoogleMeetService } from './google-meet.service';
import { callbackQuerySchema } from './google-meet.types';
import { getAdminFromRequest } from '@/lib/auth/admin';
import { AdminGuard } from '@/lib/guards/admin.guard';

@Controller('admin/google-meet')
export class GoogleMeetController {
    private readonly logger = new Logger(GoogleMeetController.name);

    constructor(private readonly meetService: GoogleMeetService) { }

    /** GET /admin/google-meet/status — own status */
    @Get('status')
    @UseGuards(AdminGuard)
    async getStatus(@Req() req: Request) {
        const admin = getAdminFromRequest(req);
        if (!admin) throw new ForbiddenException('Not authenticated');
        const status = await this.meetService.getStatus(admin.adminId);
        return { success: true, data: status };
    }

    /** GET /admin/google-meet/status/:adminId — super-admin: view any doctor's status */
    @Get('status/:adminId')
    @UseGuards(AdminGuard)
    async getStatusForDoctor(@Param('adminId') doctorAdminId: string, @Req() req: Request) {
        const admin = getAdminFromRequest(req);
        if (!admin) throw new ForbiddenException('Not authenticated');
        if (!admin.isSuperAdmin) {
            throw new ForbiddenException('Only super admins can view other doctors\' connection status');
        }
        const status = await this.meetService.getStatusForDoctor(doctorAdminId);
        return { success: true, data: status };
    }

    /**
     * GET /admin/google-meet/connect
     * Builds the Google OAuth authorization URL and redirects the doctor's browser.
     * redirectTo query param allows returning to a specific dashboard page.
     */
    @Get('connect')
    @UseGuards(AdminGuard)
    async connect(
        @Req() req: Request,
        @Res() res: Response,
        @Query('redirectTo') redirectTo?: string,
    ) {
        const admin = getAdminFromRequest(req);
        if (!admin) throw new ForbiddenException('Not authenticated');
        if (!admin.isDoctor) {
            throw new ForbiddenException('Only doctors can connect a Google Meet account');
        }

        const authUrl = this.meetService.buildAuthUrl(admin.adminId, redirectTo);
        return res.redirect(authUrl);
    }

    /**
     * GET /admin/google-meet/callback
     * Google redirects here after the user authorizes (or denies) the app.
     * Public route — security is enforced via signed JWT state.
     */
    @Get('callback')
    async callback(
        @Query() query: Record<string, string>,
        @Res() res: Response,
    ) {
        const dashboardUrl = process.env.ADMIN_DASHBOARD_URL || 'http://localhost:3000';
        const profilePath = '/admin/profile';

        // Handle user-denied consent
        if (query.error) {
            this.logger.warn(`Google OAuth denied: ${query.error}`);
            return res.redirect(
                `${dashboardUrl}${profilePath}?gmeet=error&reason=${encodeURIComponent(query.error)}`,
            );
        }

        const parseResult = callbackQuerySchema.safeParse(query);
        if (!parseResult.success) {
            return res.redirect(
                `${dashboardUrl}${profilePath}?gmeet=error&reason=invalid_callback`,
            );
        }

        try {
            const { redirectTo } = await this.meetService.handleCallback(
                parseResult.data.code,
                parseResult.data.state,
            );
            const destination = redirectTo
                ? `${dashboardUrl}${redirectTo.startsWith('/') ? '' : '/'}${redirectTo}?gmeet=connected`
                : `${dashboardUrl}${profilePath}?gmeet=connected`;
            return res.redirect(destination);
        } catch (err: any) {
            this.logger.error(`Google Meet callback error: ${err?.message}`);
            return res.redirect(
                `${dashboardUrl}${profilePath}?gmeet=error&reason=${encodeURIComponent(err?.message ?? 'unknown')}`,
            );
        }
    }

    /** POST /admin/google-meet/disconnect — unlink own Google account */
    @Post('disconnect')
    @UseGuards(AdminGuard)
    async disconnect(@Req() req: Request) {
        const admin = getAdminFromRequest(req);
        if (!admin) throw new ForbiddenException('Not authenticated');
        await this.meetService.disconnect(admin.adminId);
        return { success: true, message: 'Google Meet account disconnected' };
    }
}
