import '@/lib/env/assert-production-auth-secrets';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import cookieParser from 'cookie-parser';
import os from 'os';
import { AppModule } from './app.module';
import { RequestLoggingInterceptor } from './lib/interceptors/request-logging.interceptor';

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
  app.useGlobalInterceptors(new RequestLoggingInterceptor());
  app.use(cookieParser());
  app.enableCors({
    origin: process.env.ADMIN_APP_ORIGIN?.split(',') || true,
    credentials: true,
  });

  const swaggerConfig = new DocumentBuilder()
    .setTitle('Movit API')
    .setDescription('Movit Backend REST API')
    .setVersion('1.0')
    .addBearerAuth(
      { type: 'http', scheme: 'bearer', bearerFormat: 'JWT', in: 'header' },
      'JWT',
    )
    .build();
  const document = SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('docs', app, document, {
    swaggerOptions: { persistAuthorization: true },
  });

  const port = process.env.PORT ?? 3000;
  await app.listen(port, '0.0.0.0');
  const lanIp = getLanIp();
  const baseUrl = `http://${lanIp ?? 'localhost'}:${port}`;
  console.log(`[Swagger] ${baseUrl}/api/docs`);
  if (lanIp) {
    console.log(`[LAN] http://${lanIp}:${port}/api`);
  } else {
    console.log('[LAN] Unable to detect LAN IP');
  }
}
bootstrap();
