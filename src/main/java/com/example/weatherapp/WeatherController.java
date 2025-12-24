package com.example.weatherapp;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.util.Random;

public class WeatherController {

    private final String API_KEY = "1591648561d47c2bfc1eb5e053e029cd";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private JSONArray currentSuggestions;
    private final Random rand = new Random();

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private ListView<String> suggestionsList;
    @FXML private Label weatherInfo;
    @FXML private Label labelTitle; // Нужно добавить ID в FXML

    @FXML
    public void initialize() {
        initDatabase();
        loadLastCity();

        // Таймер на 5 секунд для смены цвета
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.1), event -> changeTheme()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() > 2) fetchCitySuggestions(newVal);
            else hideSuggestions();
        });

        suggestionsList.setOnMouseClicked(event -> {
            int index = suggestionsList.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                JSONObject city = currentSuggestions.getJSONObject(index);
                String name = city.getString("name");
                getWeather(city.getDouble("lat"), city.getDouble("lon"), name);
                saveLastCity(name, city.getDouble("lat"), city.getDouble("lon"));
                searchField.setText(name);
                hideSuggestions();
            }
        });
    }

    @FXML
    private void changeTheme() {
        // Генерируем "вырвиглазный" яркий цвет
        Color bgColor = Color.hsb(rand.nextDouble() * 360, 0.8 + rand.nextDouble() * 0.2, 0.8 + rand.nextDouble() * 0.2);

        // Устанавливаем фон
        rootPane.setBackground(new Background(new BackgroundFill(bgColor, CornerRadii.EMPTY, Insets.EMPTY)));

        // Определяем контрастный цвет для текста (черный или белый)
        double brightness = (bgColor.getRed() * 0.299) + (bgColor.getGreen() * 0.587) + (bgColor.getBlue() * 0.114);
        Color textColor = brightness < 0.5 ? Color.ORANGE : Color.YELLOW;

        // Применяем цвет к тексту
        weatherInfo.setTextFill(textColor);
        labelTitle.setTextFill(textColor);
    }

    // --- Остальная логика без изменений ---
    private void fetchCitySuggestions(String query) {
        String url = String.format("http://api.openweathermap.org/geo/1.0/direct?q=%s&limit=5&appid=%s", query.replace(" ", "%20"), API_KEY);
        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> Platform.runLater(() -> {
                    currentSuggestions = new JSONArray(response);
                    suggestionsList.getItems().clear();
                    for (int i = 0; i < currentSuggestions.length(); i++) {
                        JSONObject city = currentSuggestions.getJSONObject(i);
                        suggestionsList.getItems().add(city.getString("name") + ", " + city.optString("country"));
                    }
                    if (!suggestionsList.getItems().isEmpty()) showSuggestions(); else hideSuggestions();
                }));
    }

    private void getWeather(double lat, double lon, String cityName) {
        String url = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=ru", lat, lon, API_KEY);
        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(response -> Platform.runLater(() -> {
                    JSONObject json = new JSONObject(response);
                    double temp = json.getJSONObject("main").getDouble("temp");
                    String desc = json.getJSONArray("weather").getJSONObject(0).getString("description");
                    weatherInfo.setText(String.format("Город: %s\nТемпература: %.1f°C\nОсадки: %s", cityName, temp, desc));
                }));
    }

    private void showSuggestions() { suggestionsList.setVisible(true); suggestionsList.setManaged(true); }
    private void hideSuggestions() { suggestionsList.setVisible(false); suggestionsList.setManaged(false); }
    private void initDatabase() { try (Connection conn = DriverManager.getConnection("jdbc:sqlite:weather.db")) { conn.createStatement().execute("CREATE TABLE IF NOT EXISTS last_city (id INTEGER PRIMARY KEY, name TEXT, lat REAL, lon REAL)"); } catch (SQLException e) { e.printStackTrace(); } }
    private void saveLastCity(String name, double lat, double lon) { try (Connection conn = DriverManager.getConnection("jdbc:sqlite:weather.db"); PreparedStatement pstmt = conn.prepareStatement("INSERT OR REPLACE INTO last_city(id, name, lat, lon) VALUES(1, ?, ?, ?)")) { pstmt.setString(1, name); pstmt.setDouble(2, lat); pstmt.setDouble(3, lon); pstmt.executeUpdate(); } catch (SQLException e) { e.printStackTrace(); } }
    private void loadLastCity() { try (Connection conn = DriverManager.getConnection("jdbc:sqlite:weather.db"); ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM last_city WHERE id = 1")) { if (rs.next()) getWeather(rs.getDouble("lat"), rs.getDouble("lon"), rs.getString("name")); } catch (SQLException e) { e.printStackTrace(); } }
}