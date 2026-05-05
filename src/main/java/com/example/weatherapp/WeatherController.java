package com.example.weatherapp;

// ==================== ИМПОРТЫ ====================
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
import java.util.*;

public class WeatherController {

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
    @FXML private Label forecastLabel;
    @FXML private ImageView weatherIcon;

    // Поля для иконок прогноза
    @FXML private ImageView forecastIcon1;
    @FXML private ImageView forecastIcon2;
    @FXML private ImageView forecastIcon3;
    @FXML private ImageView forecastIcon4;

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

        // Обработка нажатия Enter в поле поиска
        searchField.setOnAction(e -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) {
                handleSearchQuery(query);
            }
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
            forecastLabel.setStyle("-fx-font-family: 'Depres'; -fx-font-size: 14;");
        } catch (Exception e) {
            logger.warn("Шрифт Depres.otf не найден");
        }
    }

    /**
     * ОБРАБОТКА ПОИСКОВОГО ЗАПРОСА ПРИ НАЖАТИИ ENTER
     * Если город не найден - показывает сообщение об ошибке
     */
    private void handleSearchQuery(String query) {
        // Проверяем, есть ли подсказки
        if (currentSuggestions != null && currentSuggestions.length() > 0) {
            // Если есть подсказки, выбираем первую
            JSONObject city = currentSuggestions.getJSONObject(0);
            handleCitySelection(city);
        } else {
            // Если подсказок нет, пробуем найти город напрямую
            searchCityDirectly(query);
        }
    }

    /**
     * ПРЯМОЙ ПОИСК ГОРОДА (если нет подсказок)
     */
    private void searchCityDirectly(String query) {
        String url = String.format(Locale.US,
                "http://api.openweathermap.org/geo/1.0/direct?q=%s&limit=1&appid=%s",
                query.replace(" ", "%20"), API_KEY);

        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        JSONArray results = new JSONArray(res);
                        if (results.length() > 0) {
                            // Город найден
                            JSONObject city = results.getJSONObject(0);
                            handleCitySelection(city);
                        } else {
                            // Город не найден - показываем messageBox
                            showCityNotFoundMessage(query);
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка при поиске города: {}", query, e);
                        showCityNotFoundMessage(query);
                    }
                }));
    }

    /**
     * ПОКАЗ СООБЩЕНИЯ "ГОРОД НЕ НАЙДЕН"
     */
    private void showCityNotFoundMessage(String cityName) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("🏙️ Город не найден");
        alert.setHeaderText("Ошибка поиска");
        alert.setContentText(String.format(
                "Город \"%s\" не найден в базе данных.\n\n" +
                        "Возможные причины:\n" +
                        "• Опечатка в названии города\n" +
                        "• Город не существует\n" +
                        "• Проблемы с интернет-соединением\n\n" +
                        "Пожалуйста, проверьте название и попробуйте снова.",
                cityName
        ));

        // Добавляем кастомную иконку (по желанию)
        // alert.setGraphic(new ImageView(new Image("file:warning_icon.png")));

        // Настраиваем стиль для кислотного режима
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #1a1a2e;" +
                        "-fx-border-color: #ff6b6b;" +
                        "-fx-border-width: 2px;"
        );
        dialogPane.lookup(".content.label").setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;"
        );

        alert.showAndWait();

        // Очищаем поле поиска и даем фокус
        searchField.clear();
        searchField.requestFocus();

        logger.warn("Город не найден: {}", cityName);
    }

    private void handleCitySelection(JSONObject city) {
        double lat = city.getDouble("lat");
        double lon = city.getDouble("lon");
        String name = city.getString("name");

        String fullName = buildFullCityName(city);

        searchField.setText(name);
        logger.info("Выбран город: {}", fullName);
        getWeather(lat, lon, fullName);
        saveLastCity(name, lat, lon);
        hideSuggestions();
    }

    private String buildFullCityName(JSONObject city) {
        StringBuilder sb = new StringBuilder(city.getString("name"));

        if (city.has("state") && !city.isNull("state") && !city.getString("state").isEmpty()) {
            sb.append(", ").append(city.getString("state"));
        }

        if (city.has("country") && !city.isNull("country") && !city.getString("country").isEmpty()) {
            sb.append(", ").append(city.getString("country"));
        }

        return sb.toString();
    }

    private void getWeather(double lat, double lon, String city) {
        String currentUrl = String.format(Locale.US,
                "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=ru",
                lat, lon, API_KEY);

        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(currentUrl)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(currentRes -> {
                    String forecastUrl = String.format(Locale.US,
                            "https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&appid=%s&units=metric&lang=ru",
                            lat, lon, API_KEY);

                    httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(forecastUrl)).build(), HttpResponse.BodyHandlers.ofString())
                            .thenApply(HttpResponse::body)
                            .thenAccept(forecastRes -> Platform.runLater(() -> {
                                try {
                                    JSONObject j = new JSONObject(currentRes);
                                    if (j.getInt("cod") != 200) throw new CityNotFoundException(city);

                                    JSONObject m = j.getJSONObject("main");
                                    JSONObject w = j.getJSONObject("wind");
                                    String desc = j.getJSONArray("weather").getJSONObject(0).getString("description");
                                    String iconCode = j.getJSONArray("weather").getJSONObject(0).getString("icon");

                                    weatherIcon.setImage(new Image("https://openweathermap.org/img/wn/" + iconCode + "@2x.png"));

                                    double[] minMax = getTodayMinMax(new JSONObject(forecastRes), j);
                                    double todayMin = minMax[0];
                                    double todayMax = minMax[1];

                                    String report = String.format(
                                            "📍 %s\n" +
                                                    "🌡 Темп: %.1f°C (как %.1f°C)\n" +
                                                    "⬇ Мин: %.1f°C | ⬆ Макс: %.1f°C\n" +
                                                    "💧 Влажн: %d%% | 📈 Давл: %d\n" +
                                                    "🧭 Ветер: %.1f м/с (%s)\n" +
                                                    "📝 %s",
                                            city.toUpperCase(),
                                            m.getDouble("temp"),
                                            m.getDouble("feels_like"),
                                            todayMin,
                                            todayMax,
                                            m.getInt("humidity"),
                                            m.getInt("pressure"),
                                            w.getDouble("speed"),
                                            getDir(w.optInt("deg")),
                                            desc.toUpperCase()
                                    );
                                    weatherInfo.setText(report);

                                    get4DayForecast(lat, lon);

                                } catch (CityNotFoundException e) {
                                    showError(e.getMessage());
                                } catch (Exception e) {
                                    logger.error("Критическая ошибка парсинга", e);
                                    weatherInfo.setText("ОШИБКА ДАННЫХ");
                                }
                            }));
                });
    }

    private double[] getTodayMinMax(JSONObject forecastData, JSONObject currentWeather) {
        double minTemp = Double.MAX_VALUE;
        double maxTemp = Double.MIN_VALUE;

        try {
            long timezone = currentWeather.getLong("timezone");
            LocalDate today = LocalDate.now(ZoneId.of("UTC"));

            JSONArray list = forecastData.getJSONArray("list");

            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.getJSONObject(i);
                long timestamp = item.getLong("dt");

                LocalDate itemDate = Instant.ofEpochSecond(timestamp)
                        .atZone(ZoneId.ofOffset("UTC", java.time.ZoneOffset.ofTotalSeconds((int) timezone)))
                        .toLocalDate();

                if (itemDate.equals(today)) {
                    JSONObject main = item.getJSONObject("main");
                    double tempMin = main.getDouble("temp_min");
                    double tempMax = main.getDouble("temp_max");

                    if (tempMin < minTemp) minTemp = tempMin;
                    if (tempMax > maxTemp) maxTemp = tempMax;
                }
            }

            if (minTemp == Double.MAX_VALUE || maxTemp == Double.MIN_VALUE) {
                JSONObject main = currentWeather.getJSONObject("main");
                minTemp = main.getDouble("temp_min");
                maxTemp = main.getDouble("temp_max");
            }

        } catch (Exception e) {
            logger.error("Ошибка при расчете мин/макс температуры", e);
            JSONObject main = currentWeather.getJSONObject("main");
            minTemp = main.getDouble("temp_min");
            maxTemp = main.getDouble("temp_max");
        }

        return new double[]{minTemp, maxTemp};
    }

    private void get4DayForecast(double lat, double lon) {
        String url = String.format(Locale.US,
                "https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&appid=%s&units=metric&lang=ru",
                lat, lon, API_KEY);

        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        JSONObject root = new JSONObject(res);
                        JSONArray list = root.getJSONArray("list");

                        Map<String, DayForecast> dayMap = new LinkedHashMap<>();

                        for (int i = 0; i < list.length(); i++) {
                            JSONObject item = list.getJSONObject(i);
                            String dateTime = item.getString("dt_txt");
                            String date = dateTime.substring(5, 10);

                            JSONObject main = item.getJSONObject("main");
                            double temp = main.getDouble("temp");
                            double tempMin = main.getDouble("temp_min");
                            double tempMax = main.getDouble("temp_max");

                            JSONArray weatherArray = item.getJSONArray("weather");
                            String desc = weatherArray.getJSONObject(0).getString("description");
                            String icon = weatherArray.getJSONObject(0).getString("icon");

                            DayForecast day = dayMap.getOrDefault(date, new DayForecast());
                            day.update(temp, tempMin, tempMax, desc, icon);
                            dayMap.put(date, day);
                        }

                        StringBuilder sb = new StringBuilder("📅 ПРОГНОЗ НА 4 ДНЯ:\n");
                        List<DayForecast> forecastDays = new ArrayList<>();

                        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd"));
                        boolean todaySkipped = false;

                        for (Map.Entry<String, DayForecast> entry : dayMap.entrySet()) {
                            if (!todaySkipped && entry.getKey().equals(today)) {
                                todaySkipped = true;
                                continue;
                            }
                            if (forecastDays.size() < 4) {
                                forecastDays.add(entry.getValue());
                                sb.append(String.format("%s -> ср: %.1f°C (мин: %.1f°C, макс: %.1f°C) | %s\n",
                                        entry.getKey(),
                                        entry.getValue().getAvgTemp(),
                                        entry.getValue().getMinTemp(),
                                        entry.getValue().getMaxTemp(),
                                        entry.getValue().getDominantDesc()));
                            }
                        }

                        forecastLabel.setText(sb.toString());
                        loadForecastIcons(forecastDays);

                    } catch (Exception e) {
                        logger.error("Ошибка прогноза", e);
                    }
                }));
    }

    private void loadForecastIcons(List<DayForecast> forecastDays) {
        ImageView[] forecastIcons = {forecastIcon1, forecastIcon2, forecastIcon3, forecastIcon4};

        for (ImageView icon : forecastIcons) {
            if (icon != null) {
                icon.setImage(null);
            }
        }

        for (int i = 0; i < forecastDays.size() && i < forecastIcons.length; i++) {
            if (forecastIcons[i] != null && forecastDays.get(i) != null) {
                String iconCode = forecastDays.get(i).getDominantIcon();
                if (iconCode != null && !iconCode.isEmpty()) {
                    try {
                        Image icon = new Image("https://openweathermap.org/img/wn/" + iconCode + "@2x.png", true);
                        forecastIcons[i].setImage(icon);

                        final int index = i;
                        Tooltip tip = new Tooltip(
                                String.format("День %d: %.1f°C, %s",
                                        index + 1,
                                        forecastDays.get(index).getAvgTemp(),
                                        forecastDays.get(index).getDominantDesc())
                        );
                        Tooltip.install(forecastIcons[index], tip);

                    } catch (Exception e) {
                        logger.error("Ошибка загрузки иконки прогноза для дня {}", i + 1, e);
                    }
                }
            }
        }
    }

    private static class DayForecast {
        private double minTemp = Double.MAX_VALUE;
        private double maxTemp = Double.MIN_VALUE;
        private double sumTemp = 0;
        private int count = 0;
        private Map<String, Integer> descCount = new HashMap<>();
        private Map<String, Integer> iconCount = new HashMap<>();

        public void update(double temp, double tempMin, double tempMax, String desc, String icon) {
            if (tempMin < minTemp) minTemp = tempMin;
            if (tempMax > maxTemp) maxTemp = tempMax;
            sumTemp += temp;
            count++;
            descCount.put(desc, descCount.getOrDefault(desc, 0) + 1);
            iconCount.put(icon, iconCount.getOrDefault(icon, 0) + 1);
        }

        public double getMinTemp() { return minTemp; }
        public double getMaxTemp() { return maxTemp; }
        public double getAvgTemp() { return sumTemp / count; }

        public String getDominantDesc() {
            return descCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
        }

        public String getDominantIcon() {
            return iconCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
        }
    }

    private String getDir(int deg) {
        String[] dirs = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        return dirs[(int) Math.round(((deg % 360) / 45.0)) % 8];
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Произошла ошибка");
        alert.setContentText(message);

        // Стилизация для кислотного режима
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #1a1a2e;" +
                        "-fx-border-color: #ff6b6b;" +
                        "-fx-border-width: 2px;"
        );
        dialogPane.lookup(".content.label").setStyle(
                "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;"
        );

        alert.showAndWait();
    }

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
        String url = String.format(Locale.US,
                "http://api.openweathermap.org/geo/1.0/direct?q=%s&limit=5&appid=%s",
                q.replace(" ", "%20"), API_KEY);
        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        currentSuggestions = new JSONArray(res);
                        suggestionsList.getItems().clear();

                        if (currentSuggestions.length() == 0) {
                            // Если подсказок нет, но пользователь что-то ввел
                            suggestionsList.setVisible(false);
                        } else {
                            for(int i = 0; i < currentSuggestions.length(); i++) {
                                JSONObject city = currentSuggestions.getJSONObject(i);
                                String displayName = buildFullCityName(city);
                                suggestionsList.getItems().add(displayName);
                            }
                            suggestionsList.setVisible(true);
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка поиска", e);
                        suggestionsList.setVisible(false);
                    }
                }));
    }

    private void hideSuggestions() {
        suggestionsList.setVisible(false);
    }

    private void initDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db")) {
            c.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS last_city (id INTEGER PRIMARY KEY CHECK (id = 1), name TEXT, lat REAL, lon REAL)");
        }
    }

    private void saveLastCity(String n, double lt, double ln) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db");
             PreparedStatement p = c.prepareStatement(
                     "INSERT OR REPLACE INTO last_city(id, name, lat, lon) VALUES(1, ?, ?, ?)")) {
            p.setString(1, n);
            p.setDouble(2, lt);
            p.setDouble(3, ln);
            p.executeUpdate();
        } catch (Exception e) {
            logger.error("Ошибка сохранения в БД");
        }
    }

    private void loadLastCity() {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db");
             ResultSet r = c.createStatement().executeQuery("SELECT * FROM last_city WHERE id = 1")) {
            if (r.next()) {
                String cityName = r.getString("name");
                double lat = r.getDouble("lat");
                double lon = r.getDouble("lon");
                getWeather(lat, lon, cityName);
            }
        } catch (Exception e) {
            logger.error("Ошибка загрузки из БД");
        }
    }
}
