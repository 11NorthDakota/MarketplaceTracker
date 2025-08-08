# MarketTracker — отслеживание цен на Wildberries

**MarketTracker** — сервис, предназначенный для автоматического мониторинга цен товаров на [Wildberries](https://www.wildberries.ru/) с уведомлениями в Telegram.

---

##  Возможности

- Отслеживание товаров по артикулу
- Уведомления о снижении цены в Telegram
- Ежечасная проверка обновлений цен
- Сохранение истории цен
---

##  Стек технологий

- **Java 21**
- **Spring Boot**
- **PostgreSQL**
- **Telegram Bot API**
- **Jsoup** (парсинг данных с WB)
- **Docker / Docker Compose**

---

## Как это работает

1. Пользователь отправляет артикул товара боту.
2. Сервис получает данные о товаре с WB.
3. Цена сохраняется в БД.
4. Раз в час запускается задача по обновлению цен.
5. Если цена изменилась — отправляется уведомление в Telegram.

---

##  Быстрый старт

### 1. Клонируй проект

```bash
git clone https://github.com/your-username/MarketTracker.git
cd MarketTracker
```

### 2. Настрой docker-compose.yml

```environment:
  TG_NAME: <YOUR_TELEGRAM_BOT_TOKEN>
  TG_TOKEN: <YOUR_BOT_USERNAME>
  db_url: <YOUR_DATABASE_URL>
  db_username: <YOUR_DATABASE_USERNAME>
  db_password: <YOUR_DATABASE_PASSWORD>
```

### 3. Собери и запусти с Docker

```bash
docker-compose up --build
```

---

##  Примеры команд в Telegram

```
/start — запуск бота
/delete — прекратить отслеживание
/list — список отслеживаемых товаров
```
