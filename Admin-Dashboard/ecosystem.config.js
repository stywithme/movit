module.exports = {
  apps: [
    {
      name: 'pose-dashboard',
      script: 'node_modules/.bin/next',
      args: 'start',
      instances: 1,
      autorestart: true,
      watch: false,
      max_memory_restart: '512M',
      env_production: {
        NODE_ENV: 'production',
        PORT: 4001,
      },
    },
  ],
};
