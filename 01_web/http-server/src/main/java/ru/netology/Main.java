package ru.netology;

public class Main {

  public static void main(String[] args) {
    // Порт зашит, как в задании, хотя его можно вывести в конфигурацию
    Server server = new Server(9999);

    server.start();
  }
}