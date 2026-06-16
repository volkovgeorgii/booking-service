# Booking service
## Сервис бронирования билетов на концерт

### Запуск 
Склонировать проект. Затем в папке где лежит `docker-compose.yml` 
запустить консоль и выполнить `docker compose up -d`

### Запуск тестов
Открыть PowerShell перейти в папку с проектом и выполнить:
`$env:TEST_POSTGRES_URL="jdbc:postgresql://localhost:5433/booking_db"; $env:TEST_POSTGRES_USER="booking";                $env:TEST_POSTGRES_PASSWORD="booking"; $env:TEST_REDIS_HOST="localhost"; $env:TEST_REDIS_PORT="6379"; mvn test          "-Dsurefire.useFile=false"`           
