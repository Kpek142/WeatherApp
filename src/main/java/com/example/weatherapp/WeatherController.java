package com.example.weatherapp;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.net.http.*;
import java.sql.*;
import java.util.Random;

public class WeatherController {
    // Логирование (Пункты 9-10)
    private static final Logger logger = LoggerFactory.getLogger(WeatherController.class);

    private final String API_KEY = "1591648561d47c2bfc1eb5e053e029cd";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private JSONArray currentSuggestions;
    private final Random rand = new Random();
    private MediaPlayer mediaPlayer;

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
    @FXML private Label forecastLabel; // Для прогноза на 4 дня (Пункт 6)
    @FXML private ImageView weatherIcon; // Для иконок (Пункт 5)

    @FXML
    public void initialize() {
        logger.info("Приложение запущено");
        setupFonts();
        startAcidMode();
        playRandomMusic();

        try {
            initDatabase();
            loadLastCity();
        } catch (Exception e) {
            logger.error("Ошибка БД при запуске", e);
        }

        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV.length() > 2) fetchSuggestions(newV);
            else hideSuggestions();
        });

        suggestionsList.setOnMouseClicked(e -> {
            int i = suggestionsList.getSelectionModel().getSelectedIndex();
            if (i >= 0) {
                JSONObject city = currentSuggestions.getJSONObject(i);
                handleCitySelection(city);
            }
        });
    }

    private void setupFonts() {
        try {
            Font.loadFont(getClass().getResourceAsStream("Depres.otf"), 20);
            labelTitle.setStyle("-fx-font-family: 'Depres'; -fx-font-size: 35;");
            weatherInfo.setStyle("-fx-font-family: 'Depres'; -fx-font-size: 20;");
        } catch (Exception e) {
            logger.warn("Шрифт Depres.otf не найден");
        }
    }

    private void handleCitySelection(JSONObject city) {
        double lat = city.getDouble("lat");
        double lon = city.getDouble("lon");
        String name = city.getString("name");

        logger.info("Выбран город: {}", name);
        getWeather(lat, lon, name);
        saveLastCity(name, lat, lon);
        searchField.setText(name);
        hideSuggestions();
    }

    // ТЕКУЩАЯ ПОГОДА (Пункт 5)
    private void getWeather(double lat, double lon, String city) {
        String url = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=ru", lat, lon, API_KEY);

        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        JSONObject j = new JSONObject(res);
                        if (j.getInt("cod") != 200) throw new CityNotFoundException(city);

                        JSONObject m = j.getJSONObject("main");
                        JSONObject w = j.getJSONObject("wind");
                        String desc = j.getJSONArray("weather").getJSONObject(0).getString("description");
                        String iconCode = j.getJSONArray("weather").getJSONObject(0).getString("icon");

                        // Ставим иконку
                        weatherIcon.setImage(new Image("https://openweathermap.org/img/wn/" + iconCode + "@2x.png"));

                        String report = String.format(
                                "📍 %s\n" +
                                        "🌡 Темп: %.1f°C (как %.1f°C)\n" +
                                        "💧 Влажн: %d%% | 📈 Давл: %d\n" +
                                        "🧭 Ветер: %.1f м/с (%s)\n" +
                                        "📝 %s",
                                city.toUpperCase(), m.getDouble("temp"), m.getDouble("feels_like"),
                                m.getInt("humidity"), m.getInt("pressure"),
                                w.getDouble("speed"), getDir(w.optInt("deg")), desc.toUpperCase()
                        );
                        weatherInfo.setText(report);

                        // Загружаем прогноз на 4 дня (Пункт 6)
                        get4DayForecast(lat, lon);

                    } catch (CityNotFoundException e) {
                        showError(e.getMessage());
                    } catch (Exception e) {
                        logger.error("Критическая ошибка парсинга", e);
                        weatherInfo.setText("ОШИБКА ДАННЫХ");
                    }
                }));
    }

    // ПРОГНОЗ НА 4 ДНЯ (Пункт 6)
    private void get4DayForecast(double lat, double lon) {
        String url = String.format("https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&appid=%s&units=metric&lang=ru", lat, lon, API_KEY);

        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        JSONObject root = new JSONObject(res);
                        JSONArray list = root.getJSONArray("list");
                        StringBuilder sb = new StringBuilder("📅 ПРОГНОЗ НА 4 ДНЯ:\n");

                        // Берем записи с шагом 8 (раз в 24 часа)
                        for (int i = 7; i < list.length() && i < 33; i += 8) {
                            JSONObject day = list.getJSONObject(i);
                            String date = day.getString("dt_txt").substring(5, 16);
                            double t = day.getJSONObject("main").getDouble("temp");
                            String d = day.getJSONArray("weather").getJSONObject(0).getString("description");
                            sb.append(String.format("%s -> %.1f°C | %s\n", date, t, d));
                        }
                        forecastLabel.setText(sb.toString());
                    } catch (Exception e) {
                        logger.error("Ошибка прогноза", e);
                    }
                }));
    }

    private String getDir(int deg) {
        String[] dirs = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        return dirs[(int) Math.round(((deg % 360) / 45.0)) % 8];
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // --- ОСТАЛЬНЫЕ МЕТОДЫ (МУЗЫКА, БД, ПОИСК) ---

    private void playRandomMusic() {
        if (mediaPlayer != null) mediaPlayer.stop();
        try {
            String track = playlist[rand.nextInt(playlist.length)];
            URL res = getClass().getResource(track);
            if (res != null) {
                mediaPlayer = new MediaPlayer(new Media(res.toString()));
                mediaPlayer.setOnEndOfMedia(this::playRandomMusic);
                mediaPlayer.setVolume(0.3);
                mediaPlayer.play();
            }
        } catch (Exception e) { logger.error("Музыка не найдена"); }
    }

    private void startAcidMode() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.5), e -> {
            Color bg = Color.hsb(rand.nextDouble() * 360, 0.5, 0.8);
            rootPane.setBackground(new Background(new BackgroundFill(bg, CornerRadii.EMPTY, Insets.EMPTY)));
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
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
                    } catch (Exception e) { logger.error("Ошибка поиска"); }
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
        } catch (Exception e) { logger.error("Ошибка сохранения в БД"); }
    }

    private void loadLastCity() {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db");
             ResultSet r = c.createStatement().executeQuery("SELECT * FROM last_city WHERE id = 1")) {
            if (r.next()) getWeather(r.getDouble("lat"), r.getDouble("lon"), r.getString("name"));
        } catch (Exception e) { logger.error("Ошибка загрузки из БД"); }
    }
}

