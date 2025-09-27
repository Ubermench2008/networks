Пример работы простого TCP-клиента (WSL2) и сервера (Windows).

---

## 1. Подготовка

### Структура проекта
tcpc/
 ├── FileWorker.java
 ├── FileTransferClient.java
 └── FileTransferServer.java

Все классы объявлены в пакете `tcpc`.

### Компиляция
# из WSL2
cd ~/tcpc
javac *.java

---

## 2. Запуск сервера (Windows)

Через IntelliJ IDEA или вручную:

cd C:\Users\redst\IdeaProjects\tcpc
javac tcpc\FileTransferServer.java
java tcpc.FileTransferServer 1234

Вывод:
Server is listening on port 1234

---

## 3. Определение IP Windows

В PowerShell:
ipconfig | findstr /R /C:"IPv4"

Используй LAN-IP (например, 192.168.31.26).

---

## 4. Запуск клиента (WSL2)

Файл для отправки должен быть доступен в WSL2 (например, ~/tcpc/file.txt).

cd ~/tcpc
java -cp . tcpc.FileTransferClient /home/redst/tcpc/file.txt 192.168.31.26 1234

---

## 5. Проверка доступности

nc -vz 192.168.31.26 1234

Если succeeded, можно отправлять файл.

---

## 6. Типичные ошибки

- Connection refused — клиент подключается на 127.0.0.1 или 10.255.255.254, где сервер не слушает.  
  Решение: использовать LAN-IP Windows.

- Пустой файл на сервере — указан неверный путь.  
  Решение: использовать абсолютный путь.

- EOFException на сервере — тестировал соединение через nc, который не шлёт нужный протокол.  
  Решение: использовать клиентскую программу.

---

## 7. Чек-лист

1. Сервер слушает на 0.0.0.0:1234 в Windows.  
2. Фаервол Windows разрешает входящие на порт 1234.  
3. Клиент в WSL2 подключается к LAN-IP Windows (192.168.31.26).  
4. Файл существует и читается.  
5. nc -vz 192.168.31.26 1234 успешен.  
6. Запуск клиента:
   java -cp . tcpc.FileTransferClient file.txt 192.168.31.26 1234
