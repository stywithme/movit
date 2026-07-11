code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T17:34:45.021Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 597ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:10.886Z] POST /api/mobile/auth/login | 201 OK | 544ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:11.048Z] GET /api/mobile/training-profile | 200 OK | 48ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
[2026-07-11T18:00:11.637Z] GET /api/mobile/home | 200 OK | 233ms | 192.168.68.133 | ktor-client
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:00:11.661Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 554ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:11.802Z] GET /api/mobile/plan | 200 OK | 21ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:14.985Z] GET /api/mobile/explore | 200 OK | 60ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:00:36.117Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 711ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:46.322Z] GET /api/mobile/explore?updatedAfter=2026-07-11T18%3A00%3A14.926Z | 200 OK | 253ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:50.657Z] GET /api/mobile/workout-templates/800d3683-2c26-4aab-bd1a-1cb72410b305/training-config | 200 OK | 99ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:50.826Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 10ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:00:51.293Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 432ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:51.421Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 7ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:51.472Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 18ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:00:51.710Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 193ms | 192.168.68.133 | ktor-client
[2026-07-11T18:00:51.765Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 11ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:07.070Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 76ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
[2026-07-11T18:01:07.438Z] GET /api/mobile/workout-templates/800d3683-2c26-4aab-bd1a-1cb72410b305/training-config | 200 OK | 462ms | 192.168.68.133 | ktor-client
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:07.575Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 460ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:07.624Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 7ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:07.652Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 6ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:07.702Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 7ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:07.993Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 253ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:08.048Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 12ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:08.098Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 8ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
[2026-07-11T18:01:08.345Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 34ms | 192.168.68.133 | ktor-client
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:08.502Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 360ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:08.858Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 458ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:08.930Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 7ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:08.977Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 7ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:09.387Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 366ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:09.541Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 10ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:10.492Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 6ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[2026-07-11T18:01:10.761Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 221ms | 192.168.68.133 | ktor-client
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:10.802Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 6ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:10.843Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 10ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:11.098Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 216ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:11.150Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 14ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:13.715Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 13ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:13.798Z] GET /api/mobile/workout-templates/800d3683-2c26-4aab-bd1a-1cb72410b305/training-config | 200 OK | 132ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:14.212Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 457ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:14.267Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 14ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:14.643Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 273ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:14.982Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 11ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:15.127Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 11ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:15.152Z] GET /api/mobile/explore?updatedAfter=2026-07-11T18%3A00%3A46.069Z | 200 OK | 23ms | 192.168.68.133 | ktor-client
[MobileSync] messageLibrary: total=2662, withAudio=0, withoutAudio=2662
prisma:error 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
[Mobile Sync] Error: PrismaClientKnownRequestError: 
Invalid `prisma.plannedWorkoutReport.findMany()` invocation in
D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:70

  540 if (updatedAfterDate) {
  541     reportWhere.updatedAt = { gt: updatedAfterDate };
  542 }
→ 543 const reportRows = await prisma.plannedWorkoutReport.findMany(
The column `(not available)` does not exist in the current database.
    at Vr.handleRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:228:13)
    at Vr.handleAndLogRequestError (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:174:12)
    at Vr.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:143:12)
    at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
    at async a (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\getPrismaClient.ts:805:24)
    at async Object.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.service.js:543:36)
    at async MobileSyncController.sync (D:\laragon\www\movit\backend\dist\src\modules\mobile-sync\mobile-sync.controller.js:43:30) {
  code: 'P2022',
  meta: {
    modelName: 'PlannedWorkoutReport',
    driverAdapterError: DriverAdapterError: ColumnNotFound
        at PrismaPgAdapter.onError (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:687:11)
        at PrismaPgAdapter.performIO (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:682:12)
        at process.processTicksAndRejections (node:internal/process/task_queues:103:5)
        at async PrismaPgAdapter.queryRaw (D:\laragon\www\movit\backend\node_modules\@prisma\adapter-pg\dist\index.js:602:30)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:182:26)
        at async e.interpretNode (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:268:41)
        at async e.run (D:\laragon\www\movit\backend\node_modules\@prisma\client-engine-runtime\src\interpreter\query-interpreter.ts:93:23)
        at async e.execute (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\LocalExecutor.ts:81:12)
        at async Dt.request (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\core\engines\client\ClientEngine.ts:461:22)
        at async Object.singleLoader (D:\laragon\www\movit\backend\node_modules\@prisma\client\src\runtime\RequestHandler.ts:112:26) {
      cause: [Object]
    }
  },
  clientVersion: '7.3.0'
}
[2026-07-11T18:01:15.549Z] GET /api/mobile/sync?includeReports=summary | 500 FAIL | 387ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:15.766Z] GET /api/mobile/workout-templates/triple_alternating/training-config | 404 FAIL | 13ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:17.016Z] GET /api/mobile/home | 304 FAIL | 69ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:25.784Z] GET /api/mobile/explore?updatedAfter=2026-07-11T18%3A01%3A15.130Z | 200 OK | 19ms | 192.168.68.133 | ktor-client
[2026-07-11T18:01:26.628Z] GET /api/mobile/home | 304 FAIL | 45ms | 192.168.68.133 | ktor-client
