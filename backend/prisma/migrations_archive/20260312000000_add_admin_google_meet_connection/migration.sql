-- CreateTable
CREATE TABLE "admin_google_meet_connections" (
    "id" TEXT NOT NULL,
    "adminId" TEXT NOT NULL,
    "googleSub" TEXT NOT NULL,
    "googleEmail" TEXT NOT NULL,
    "encryptedRefreshToken" TEXT NOT NULL,
    "accessToken" TEXT,
    "accessTokenExpiresAt" TIMESTAMP(3),
    "scope" TEXT,
    "lastError" TEXT,
    "connectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "disconnectedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "admin_google_meet_connections_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "admin_google_meet_connections_adminId_key" ON "admin_google_meet_connections"("adminId");

-- CreateIndex
CREATE INDEX "admin_google_meet_connections_adminId_idx" ON "admin_google_meet_connections"("adminId");

-- CreateIndex
CREATE INDEX "admin_google_meet_connections_googleSub_idx" ON "admin_google_meet_connections"("googleSub");

-- AddForeignKey
ALTER TABLE "admin_google_meet_connections" ADD CONSTRAINT "admin_google_meet_connections_adminId_fkey" FOREIGN KEY ("adminId") REFERENCES "admins"("id") ON DELETE CASCADE ON UPDATE CASCADE;
