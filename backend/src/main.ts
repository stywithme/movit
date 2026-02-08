import { NestFactory } from '@nestjs/core';
import cookieParser from 'cookie-parser';
import os from 'os';
import { AppModule } from './app.module';

function getLanIp(): string | null {
  const interfaces = os.networkInterfaces();
  for (const networkInterfaces of Object.values(interfaces)) {
    if (!networkInterfaces) continue;
    for (const network of networkInterfaces) {
      if (network.family === 'IPv4' && !network.internal) {
        return network.address;
      }
    }
  }
  return null;
}

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  app.setGlobalPrefix('api');
  app.use(cookieParser());
  app.enableCors({
    origin: process.env.ADMIN_APP_ORIGIN?.split(',') || true,
    credentials: true,
  });
  const port = process.env.PORT ?? 3000;
  await app.listen(port, '0.0.0.0');
  const lanIp = getLanIp();
  if (lanIp) {
    console.log(`[LAN] http://${lanIp}:${port}/api`);
  } else {
    console.log('[LAN] Unable to detect LAN IP');
  }
}
bootstrap();
