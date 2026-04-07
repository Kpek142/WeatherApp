package com.example.weatherapp;

// Базовый класс для всех наших ошибок
public class WeatherAppException extends Exception {
    public WeatherAppException(String message) { super(message); }
}

// Ошибка, если API вернул 404
class CityNotFoundException extends WeatherAppException {
    public CityNotFoundException(String city) {
        super("Город '" + city + "' не найден!");
    }
}

// Ошибка, если нет интернета
class NetworkException extends WeatherAppException {
    public NetworkException() {
        super("Проблемы с интернет-соединением.");
    }
}