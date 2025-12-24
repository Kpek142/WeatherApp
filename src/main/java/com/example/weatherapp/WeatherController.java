package com.example.weatherapp;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URL;
import java.net.http.*;
import java.sql.*;
import java.util.Random;

public class WeatherController {
    private final String API_KEY = "1591648561d47c2bfc1eb5e053e029cd";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private JSONArray currentSuggestions;
    private final Random rand = new Random();
    private MediaPlayer mediaPlayer;

    // Твой плейлист
    private final String[] playlist = {
            "femtanyl - DOGMATICA.mp3",
            "Genocide Organ & Prurient - The Holy Lance.mp3",
            "femtanyl - KATAMARI.mp3"
    };

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private ListView<String> suggestionsList;
    @FXML private Label weatherInfo;
    @FXML private Label labelTitle;

    @FXML
    public void initialize() {
        // ЗАГРУЗКА ШРИФТА
        try {
            Font customFont = Font.loadFont(getClass().getResourceAsStream("Depres.otf"), 20);
            if (customFont != null) {
                labelTitle.setFont(Font.font("Depres", 35));
                weatherInfo.setFont(Font.font("Depres", 50));
                searchField.setFont(Font.font("Depres", 15));
            }
        } catch (Exception e) {
            System.err.println("Шрифт не найден!");
        }

        startAcidMode();
        playRandomMusic();

        try {
            initDatabase();
            loadLastCity();
        } catch (Exception e) {}

        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV.length() > 2) fetchSuggestions(newV);
            else hideSuggestions();
        });

        suggestionsList.setOnMouseClicked(e -> {
            int i = suggestionsList.getSelectionModel().getSelectedIndex();
            if (i >= 0) {
                JSONObject city = currentSuggestions.getJSONObject(i);
                getWeather(city.getDouble("lat"), city.getDouble("lon"), city.getString("name"));
                saveLastCity(city.getString("name"), city.getDouble("lat"), city.getDouble("lon"));
                searchField.setText(city.getString("name"));
                hideSuggestions();
            }
        });
    }

    private void playRandomMusic() {
        if (mediaPlayer != null) mediaPlayer.stop();
        try {
            String track = playlist[rand.nextInt(playlist.length)];
            URL res = getClass().getResource(track);
            if (res != null) {
                mediaPlayer = new MediaPlayer(new Media(res.toString()));
                mediaPlayer.setOnEndOfMedia(this::playRandomMusic);
                mediaPlayer.setVolume(0.4);
                mediaPlayer.play();
            }
        } catch (Exception e) { System.out.println("Музыка не найдена"); }
    }

    private void startAcidMode() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.1), e -> {
            Color bg = Color.hsb(rand.nextDouble() * 360, 0.8, 0.9);
            rootPane.setBackground(new Background(new BackgroundFill(bg, CornerRadii.EMPTY, Insets.EMPTY)));
            Color txt = (bg.getBrightness() < 0.5) ? Color.WHITE : Color.BLACK;
            weatherInfo.setTextFill(txt);
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void getWeather(double lat, double lon, String city) {
        String url = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=ru", lat, lon, API_KEY);
        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        JSONObject j = new JSONObject(res);
                        JSONObject m = j.getJSONObject("main");
                        JSONObject w = j.getJSONObject("wind");
                        String desc = j.getJSONArray("weather").getJSONObject(0).getString("description");

                        String report = String.format(
                                "📍 %s\n" +
                                        "🌡 Темп: %.1f°C (как %.1f°C)\n" +
                                        "📊 Мин: %.1f | Макс: %.1f\n" +
                                        "💧 Влажн: %d%% | 📈 Давл: %d\n" +
                                        "🧭 Ветер: %.1f м/с (%s)\n" +
                                        "📝 %s",
                                city.toUpperCase(), m.getDouble("temp"), m.getDouble("feels_like"),
                                m.getDouble("temp_min"), m.getDouble("temp_max"),
                                m.getInt("humidity"), m.getInt("pressure"),
                                w.getDouble("speed"), getDir(w.optInt("deg")), desc.toUpperCase()
                        );
                        weatherInfo.setText(report);
                    } catch (Exception e) { weatherInfo.setText("ОШИБКА"); }
                }));
    }

    private String getDir(int deg) {
        String[] dirs = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        return dirs[(int) Math.round(((deg % 360) / 45.0)) % 8];
    }

    private void fetchSuggestions(String q) {
        String url = "http://api.openweathermap.org/geo/1.0/direct?q=" + q.replace(" ", "%20") + "&limit=5&appid=" + API_KEY;
        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        currentSuggestions = new JSONArray(res);
                        suggestionsList.getItems().clear();
                        for(int i=0; i<currentSuggestions.length(); i++)
                            suggestionsList.getItems().add(currentSuggestions.getJSONObject(i).getString("name"));
                        suggestionsList.setVisible(!suggestionsList.getItems().isEmpty());
                    } catch (Exception e) {}
                }));
    }

    private void hideSuggestions() { suggestionsList.setVisible(false); }
    private void initDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db")) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS last_city (id INTEGER PRIMARY KEY CHECK (id = 1), name TEXT, lat REAL, lon REAL)");
        }
    }
    private void saveLastCity(String n, double lt, double ln) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db");
             PreparedStatement p = c.prepareStatement("INSERT OR REPLACE INTO last_city(id, name, lat, lon) VALUES(1, ?, ?, ?)")) {
            p.setString(1, n); p.setDouble(2, lt); p.setDouble(3, ln); p.executeUpdate();
        } catch (Exception e) {}
    }
    private void loadLastCity() {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db");
             ResultSet r = c.createStatement().executeQuery("SELECT * FROM last_city WHERE id = 1")) {
            if (r.next()) getWeather(r.getDouble("lat"), r.getDouble("lon"), r.getString("name"));
        } catch (Exception e) {}
    }
}