package com.example.weatherapp;

// Импорты JavaFX для работы с графическим интерфейсом
import javafx.application.Application;  // Базовый класс всех JavaFX-приложений
import javafx.fxml.FXMLLoader;         // Загружает интерфейс из FXML-файла
import javafx.scene.Scene;             // Сцена - контейнер для всех UI-элементов
import javafx.stage.Stage;             // Stage - окно приложения

import java.io.IOException;            // Для обработки ошибок ввода-вывода

/**
 * Главный класс приложения "Погода"
 * Наследуется от Application - обязательное требование JavaFX
 *
 * Этот класс является точкой входа в программу.
 * Он загружает FXML-разметку, создаёт окно и отображает его.
 */
public class WeatherApp extends Application {

    /**
     * Главный метод JavaFX, который вызывается после запуска приложения.
     * Здесь мы создаём и настраиваем главное окно.
     *
     * @param stage Главное окно приложения (передаётся системой)
     * @throws IOException Если не удалось загрузить FXML-файл
     */
    @Override
    public void start(Stage stage) throws IOException {

        // Создаём загрузчик FXML-файлов
        // getResource("hello-view.fxml") - ищет файл в папке resources
        FXMLLoader fxmlLoader = new FXMLLoader(WeatherApp.class.getResource("hello-view.fxml"));

        // Создаём сцену (холст) с загруженным интерфейсом
        // Размеры: ширина 1440 пикселей, высота 900 пикселей
        Scene scene = new Scene(fxmlLoader.load(), 1440, 900);

        // Устанавливаем заголовок окна (отображается в верхней части)
        stage.setTitle("Weather App (FXML)");

        // Помещаем сцену в окно
        stage.setScene(scene);

        // Показываем окно пользователю (делаем видимым)
        stage.show();
    }

    /**
     * Стандартная точка входа в Java-приложение
     * Метод launch() запускает JavaFX-движок:
     * 1. Инициализирует JavaFX-среду
     * 2. Создаёт экземпляр класса WeatherApp
     * 3. Вызывает метод start() в специальном JavaFX Application Thread
     *
     * @param args Аргументы командной строки (не используются)
     */
    public static void main(String[] args) {
        launch();  // Запускаем JavaFX-приложение
    }
}