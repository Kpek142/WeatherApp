module com.example.weatherapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media; // Добавлено для музыки
    requires org.json;
    requires java.net.http;
    requires java.sql;

    opens com.example.weatherapp to javafx.fxml;
    exports com.example.weatherapp;
}