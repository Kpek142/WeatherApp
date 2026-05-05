package com.example.weatherapp;

// ==================== ИМПОРТЫ ====================
import java.util.Locale;                    // Для форматирования URL с американской локалью
import javafx.animation.KeyFrame;           // Кадр анимации
import javafx.animation.Timeline;           // Временная шкала для анимации
import javafx.application.Platform;         // Для работы с UI из других потоков
import javafx.fxml.FXML;                    // Аннотация для привязки к FXML
import javafx.geometry.Insets;              // Отступы
import javafx.scene.control.*;              // Все элементы управления (Label, TextField и т.д.)
import javafx.scene.image.Image;            // Для загрузки иконок погоды
import javafx.scene.image.ImageView;        // Для отображения иконок
import javafx.scene.layout.*;               // Layout-контейнеры (VBox, Background и т.д.)
import javafx.scene.paint.Color;            // Цвета для кислотного режима
import javafx.scene.media.Media;            // Аудиофайл
import javafx.scene.media.MediaPlayer;      // Проигрыватель музыки
import javafx.scene.text.Font;              // Для загрузки шрифтов
import javafx.util.Duration;                // Длительность для анимации
import org.json.JSONArray;                  // Работа с JSON-массивами
import org.json.JSONObject;                 // Работа с JSON-объектами
import org.slf4j.Logger;                    // Логирование
import org.slf4j.LoggerFactory;             // Фабрика логгеров

import java.net.URI;                        // Для создания URI из строки
import java.net.URL;                        // Для поиска ресурсов (музыка, шрифты)
import java.net.http.*;                     // HTTP-клиент для запросов к API
import java.sql.*;                          // Работа с SQLite БД
import java.util.Random;                    // Случайные числа (цвета, музыка)

/**
 * КОНТРОЛЛЕР ПОГОДНОГО ПРИЛОЖЕНИЯ
 * Реализует паттерн MVC (Model-View-Controller)
 *
 * ОСНОВНЫЕ ФУНКЦИИ:
 * 1. Запрос текущей погоды с OpenWeatherMap API
 * 2. Прогноз на 4 дня
 * 3. Поиск городов с автодополнением
 * 4. Сохранение последнего города в SQLite БД
 * 5. Кислотный режим (меняющийся фон)
 * 6. Фоновая музыка из трёх треков
 * 7. Кастомный шрифт Depres.otf
 */
public class WeatherController {

    // ========== ЛОГИРОВАНИЕ ==========
    // Logger для записи событий (пункты 9-10 ТЗ)
    private static final Logger logger = LoggerFactory.getLogger(WeatherController.class);

    // ========== КОНСТАНТЫ И НАСТРОЙКИ ==========
    private final String API_KEY = "1591648561d47c2bfc1eb5e053e029cd";  // Ключ OpenWeatherMap
    private final HttpClient httpClient = HttpClient.newHttpClient();    // HTTP-клиент для запросов
    private JSONArray currentSuggestions;     // Текущие подсказки городов
    private final Random rand = new Random();   // Генератор случайных чисел
    private MediaPlayer mediaPlayer;            // Плеер для фоновой музыки

    // Плейлист (файлы .mp3 должны лежать в resources)
    private final String[] playlist = {
            "femtanyl - DOGMATICA.mp3",
            "Genocide Organ & Prurient - The Holy Lance.mp3",
            "femtanyl - KATAMARI.mp3"
    };

    // ========== FXML-ПОЛЯ (привязываются к элементам в hello-view.fxml) ==========
    @FXML private VBox rootPane;           // Корневой контейнер (для кислотного фона)
    @FXML private TextField searchField;   // Поле ввода города
    @FXML private ListView<String> suggestionsList;  // Выпадающий список подсказок
    @FXML private Label weatherInfo;       // Основная информация о погоде
    @FXML private Label labelTitle;        // Заголовок "КИСЛОТНАЯ ПОГОДА"
    @FXML private Label forecastLabel;     // Прогноз на 4 дня (Пункт 6 ТЗ)
    @FXML private ImageView weatherIcon;   // Иконка погоды (Пункт 5 ТЗ)

    /**
     * ИНИЦИАЛИЗАЦИЯ КОНТРОЛЛЕРА
     * Вызывается автоматически после загрузки FXML-файла.
     * Настраивает всё приложение: шрифты, музыку, БД, обработчики событий.
     */
    @FXML
    public void initialize() {
        logger.info("Приложение запущено");
        setupFonts();           // Загружаем кастомный шрифт Depres.otf
        startAcidMode();        // Запускаем кислотную анимацию фона
        playRandomMusic();      // Включаем случайный трек из плейлиста

        // Пытаемся инициализировать БД и загрузить последний выбранный город
        try {
            initDatabase();
            loadLastCity();
        } catch (Exception e) {
            logger.error("Ошибка БД при запуске", e);
        }

        // СЛУШАТЕЛЬ ПОЛЯ ВВОДА: при вводе >2 символов ищем города
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV.length() > 2) fetchSuggestions(newV);
            else hideSuggestions();
        });

        // СЛУШАТЕЛЬ СПИСКА ПОДСКАЗОК: при клике на город загружаем погоду
        suggestionsList.setOnMouseClicked(e -> {
            int i = suggestionsList.getSelectionModel().getSelectedIndex();
            if (i >= 0) {
                JSONObject city = currentSuggestions.getJSONObject(i);
                handleCitySelection(city);
            }
        });
    }

    /**
     * ЗАГРУЗКА КАСТОМНОГО ШРИФТА
     * Пытается загрузить Depres.otf из ресурсов
     * Если шрифт не найден — просто предупреждение, приложение работает дальше
     */
    private void setupFonts() {
        try {
            Font.loadFont(getClass().getResourceAsStream("Depres.otf"), 20);
            labelTitle.setStyle("-fx-font-family: 'Depres'; -fx-font-size: 35;");
            weatherInfo.setStyle("-fx-font-family: 'Depres'; -fx-font-size: 20;");
        } catch (Exception e) {
            logger.warn("Шрифт Depres.otf не найден");
        }
    }

    /**
     * ОБРАБОТКА ВЫБРАННОГО ГОРОДА
     * @param city JSON-объект с данными города (lat, lon, name)
     *
     * Действия:
     * 1. Извлекаем координаты и название
     * 2. Загружаем погоду
     * 3. Сохраняем город в БД
     * 4. Обновляем поле ввода и скрываем подсказки
     */
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

    /**
     * ЗАПРОС ТЕКУЩЕЙ ПОГОДЫ (Пункт 5 ТЗ)
     * @param lat Широта
     * @param lon Долгота
     * @param city Название города
     *
     * Использует асинхронный HTTP-запрос к OpenWeatherMap API
     * После получения ответа обновляет UI через Platform.runLater()
     */
    private void getWeather(double lat, double lon, String city) {
        // Формируем URL для текущей погоды (метрическая система, русский язык)
        String url = String.format(Locale.US, "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=ru", lat, lon, API_KEY);

        // Асинхронный GET-запрос
        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)          // Получаем тело ответа (JSON)
                .thenAccept(res -> Platform.runLater(() -> {  // Переключаемся на UI-поток
                    try {
                        JSONObject j = new JSONObject(res);
                        if (j.getInt("cod") != 200) throw new CityNotFoundException(city);

                        // Парсим JSON
                        JSONObject m = j.getJSONObject("main");      // Температура, давление
                        JSONObject w = j.getJSONObject("wind");      // Ветер
                        String desc = j.getJSONArray("weather").getJSONObject(0).getString("description");
                        String iconCode = j.getJSONArray("weather").getJSONObject(0).getString("icon");

                        // Загружаем иконку с CDN OpenWeatherMap
                        weatherIcon.setImage(new Image("https://openweathermap.org/img/wn/" + iconCode + "@2x.png"));

                        // Формируем красивый отчёт
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

                        // Загружаем прогноз на 4 дня
                        get4DayForecast(lat, lon);

                    } catch (CityNotFoundException e) {
                        showError(e.getMessage());
                    } catch (Exception e) {
                        logger.error("Критическая ошибка парсинга", e);
                        weatherInfo.setText("ОШИБКА ДАННЫХ");
                    }
                }));
    }

    /**
     * ЗАПРОС ПРОГНОЗА НА 4 ДНЯ (Пункт 6 ТЗ)
     * @param lat Широта
     * @param lon Долгота
     *
     * Использует API forecast (данные каждые 3 часа)
     * Берём записи с шагом 8 (24 часа), начиная с i=7 (чтобы не брать сегодня)
     */
    private void get4DayForecast(double lat, double lon) {
        String url = String.format(Locale.US, "https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f&appid=%s&units=metric&lang=ru", lat, lon, API_KEY);

        httpClient.sendAsync(HttpRequest.newBuilder().uri(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(res -> Platform.runLater(() -> {
                    try {
                        JSONObject root = new JSONObject(res);
                        JSONArray list = root.getJSONArray("list");
                        StringBuilder sb = new StringBuilder("📅 ПРОГНОЗ НА 4 ДНЯ:\n");

                        // Берём записи с шагом 8 (раз в 24 часа), всего 4 дня
                        for (int i = 7; i < list.length() && i < 33; i += 8) {
                            JSONObject day = list.getJSONObject(i);
                            String date = day.getString("dt_txt").substring(5, 16);  // Формат: MM-DD HH:MM
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

    /**
     * ПРЕОБРАЗОВАНИЕ ГРАДУСОВ В НАПРАВЛЕНИЕ ВЕТРА
     * @param deg Угол в градусах (0° = север)
     * @return Сторона света: С, СВ, В, ЮВ, Ю, ЮЗ, З, СЗ
     */
    private String getDir(int deg) {
        String[] dirs = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        return dirs[(int) Math.round(((deg % 360) / 45.0)) % 8];
    }

    /**
     * ПОКАЗ ОШИБКИ В ВИДЕ ДИАЛОГОВОГО ОКНА
     * @param message Текст ошибки
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ==================== ФОНОВАЯ МУЗЫКА ====================

    /**
     * ЗАПУСК СЛУЧАЙНОГО ТРЕКА
     * Выбирает случайный MP3 из плейлиста и воспроизводит его
     * При окончании трека рекурсивно вызывает саму себя (зацикливание)
     */
    private void playRandomMusic() {
        if (mediaPlayer != null) mediaPlayer.stop();
        try {
            String track = playlist[rand.nextInt(playlist.length)];
            URL res = getClass().getResource(track);
            if (res != null) {
                mediaPlayer = new MediaPlayer(new Media(res.toString()));
                mediaPlayer.setOnEndOfMedia(this::playRandomMusic);  // Зацикливание
                mediaPlayer.setVolume(0.3);  // Громкость 30%
                mediaPlayer.play();
            }
        } catch (Exception e) { logger.error("Музыка не найдена"); }
    }

    // ==================== КИСЛОТНЫЙ РЕЖИМ ====================

    /**
     * ЗАПУСК "КИСЛОТНОЙ" АНИМАЦИИ ФОНА
     * Каждые 0.5 секунды фон меняет цвет (HSV-модель)
     * Цикл бесконечный (Timeline.INDEFINITE)
     */
    private void startAcidMode() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.5), e -> {
            Color bg = Color.hsb(rand.nextDouble() * 360, 0.5, 0.8);
            rootPane.setBackground(new Background(new BackgroundFill(bg, CornerRadii.EMPTY, Insets.EMPTY)));
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    // ==================== ПОИСК ГОРОДОВ ====================

    /**
     * ПОИСК ПОДСКАЗОК ГОРОДОВ ПО API OpenWeatherMap Geo
     * @param q Поисковый запрос (название города)
     *
     * Возвращает до 5 подходящих городов и отображает их в suggestionsList
     */
    private void fetchSuggestions(String q) {
        String url = String.format(Locale.US, "http://api.openweathermap.org/geo/1.0/direct?q=%s&limit=5&appid=%s", q.replace(" ", "%20"), API_KEY);
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

    /**
     * СКРЫТЬ ПОДСКАЗКИ ГОРОДОВ
     */
    private void hideSuggestions() { suggestionsList.setVisible(false); }

    // ==================== РАБОТА С БАЗОЙ ДАННЫХ (SQLite) ====================

    /**
     * ИНИЦИАЛИЗАЦИЯ БАЗЫ ДАННЫХ
     * Создаёт таблицу last_city, если она не существует
     * Таблица содержит только одну запись (id = 1) с последним выбранным городом
     */
    private void initDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db")) {
            c.createStatement().execute("CREATE TABLE IF NOT EXISTS last_city (id INTEGER PRIMARY KEY CHECK (id = 1), name TEXT, lat REAL, lon REAL)");
        }
    }

    /**
     * СОХРАНЕНИЕ ПОСЛЕДНЕГО ГОРОДА В БД
     * @param n Название города
     * @param lt Широта
     * @param ln Долгота
     *
     * Использует INSERT OR REPLACE (заменяет запись с id=1)
     */
    private void saveLastCity(String n, double lt, double ln) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db");
             PreparedStatement p = c.prepareStatement("INSERT OR REPLACE INTO last_city(id, name, lat, lon) VALUES(1, ?, ?, ?)")) {
            p.setString(1, n); p.setDouble(2, lt); p.setDouble(3, ln); p.executeUpdate();
        } catch (Exception e) { logger.error("Ошибка сохранения в БД"); }
    }

    /**
     * ЗАГРУЗКА ПОСЛЕДНЕГО ГОРОДА ИЗ БД
     * Если запись существует — автоматически загружает погоду для этого города
     */
    private void loadLastCity() {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:weather.db");
             ResultSet r = c.createStatement().executeQuery("SELECT * FROM last_city WHERE id = 1")) {
            if (r.next()) getWeather(r.getDouble("lat"), r.getDouble("lon"), r.getString("name"));
        } catch (Exception e) { logger.error("Ошибка загрузки из БД"); }
    }
}