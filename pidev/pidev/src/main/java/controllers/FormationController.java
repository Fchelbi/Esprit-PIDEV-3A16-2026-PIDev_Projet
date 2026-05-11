package controllers;

import entities.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import services.*;
import utils.VideoPlayerUtil;

import java.io.File;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class FormationController implements Initializable {

    // ── Table view ────────────────────────────────────────────────────────
    @FXML private TableView<Formation> tvFormations;
    @FXML private TableColumn<Formation, String> colTitle, colDescription, colCategory, colVideo, colQuiz, colActions;
    @FXML private Label lblTotal;

    // ── Search ────────────────────────────────────────────────────────────
    @FXML private TextField tfSearch;

    // ── Sections ──────────────────────────────────────────────────────────
    @FXML private StackPane formationContent;
    @FXML private VBox tableSection;
    @FXML private ScrollPane formScrollPane;

    // ── Form fields ───────────────────────────────────────────────────────
    @FXML private Label lblFormTitle, lblError;
    @FXML private TextField tfTitle, tfVideoUrl;
    @FXML private TextArea taDescription;
    @FXML private ComboBox<String> cbCategory;
    @FXML private ComboBox<String> cbCoach;

    // ── Quiz panel ────────────────────────────────────────────────────────
    @FXML private TextField tfQuizTitle, tfPassingScore;
    @FXML private Label lblQuizError;
    @FXML private VBox questionsArea, questionsList;

    // ── Services ──────────────────────────────────────────────────────────
    private FormationService   formationService;
    private ParticipantService participantService;
    private QuizService        quizService;
    private QuestionService    questionService;
    private ReponseService     reponseService;
    private QuizResultService  quizResultService;
    private CertificateService certificateService;
    private QuizGeneratorAPI   quizGeneratorAPI;
    private serviceUser        userService;
    private services.YouTubeAPIService youtubeService;
    private ObservableList<Formation> formationData = FXCollections.observableArrayList();
    private Formation selectedFormation = null; // formation being edited
    private int coachId = 0; // 0 = admin (all), >0 = coach (own only)

    public void setCoachMode(int coachId) {
        this.coachId = coachId;
        loadFormations();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        formationService   = new FormationService();
        participantService = new ParticipantService();
        quizService        = new QuizService();
        questionService    = new QuestionService();
        reponseService     = new ReponseService();
        quizResultService  = new QuizResultService();
        certificateService = new CertificateService();
        quizGeneratorAPI   = new QuizGeneratorAPI();
        userService        = new serviceUser();

        setupTable();
        setupCombos();
        loadFormations();

        tfSearch.textProperty().addListener((obs, o, n) -> filterTable(n));
    }

    // ════════════════════════════════════════════════════════════════════
    //  TABLE SETUP
    // ════════════════════════════════════════════════════════════════════

    private void setupTable() {
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));

        colVideo.setCellValueFactory(cell -> {
            String url = cell.getValue().getVideoUrl();
            return new SimpleStringProperty(url != null && !url.isBlank() ? "▶ Oui" : "—");
        });

        colQuiz.setCellValueFactory(cell -> {
            try {
                Quiz q = quizService.selectByFormation(cell.getValue().getId());
                return new SimpleStringProperty(q != null ? "✅ Oui" : "—");
            } catch (SQLException e) {
                return new SimpleStringProperty("—");
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            final Button btnEdit   = new Button("✏️");
            final Button btnVideo  = new Button("▶");
            final Button btnStats  = new Button("📊");
            final Button btnDelete = new Button("🗑️");
            {
                String base = "-fx-cursor:hand;-fx-background-radius:5;-fx-padding:4 8;-fx-font-size:12px;-fx-text-fill:white;";
                btnEdit.setStyle(base + "-fx-background-color:#4A6FA5;");
                btnVideo.setStyle(base + "-fx-background-color:#6c5ce7;");
                btnStats.setStyle(base + "-fx-background-color:#00b894;");
                btnDelete.setStyle(base + "-fx-background-color:#d63031;");
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Formation f = getTableView().getItems().get(getIndex());
                btnEdit.setOnAction(e -> editFormation(f));
                btnVideo.setOnAction(e -> previewVideo(f));
                btnDelete.setOnAction(e -> deleteFormation(f));
                btnStats.setOnAction(e -> showFormationStats(f));
                btnVideo.setDisable(f.getVideoUrl() == null || f.getVideoUrl().isBlank());
                HBox box = new HBox(4, btnEdit, btnVideo, btnStats, btnDelete);
                box.setAlignment(Pos.CENTER);
                setGraphic(box);
            }
        });

        tvFormations.setItems(formationData);
    }

    private void setupCombos() {
        cbCategory.getItems().addAll(
                "Communication", "Nutrition", "Sport", "Psychologie",
                "Gestion du stress", "Développement personnel", "Méditation", "Autre"
        );
        try {
            userService.selectALL().stream()
                    .filter(u -> "COACH".equalsIgnoreCase(u.getRole()))
                    .forEach(u -> cbCoach.getItems().add(u.getId_user() + " — " + u.getPrenom() + " " + u.getNom()));
        } catch (Exception ignored) {}
    }

    // ════════════════════════════════════════════════════════════════════
    //  LOAD & FILTER
    // ════════════════════════════════════════════════════════════════════

    private void loadFormations() {
        try {
            List<Formation> list = coachId > 0
                    ? formationService.selectByCoach(coachId)
                    : formationService.selectALL();
            formationData.setAll(list);
            updateCount(list.size());
        } catch (SQLException e) {
            showError(lblError, "Erreur de chargement : " + e.getMessage());
        }
    }

    private void filterTable(String keyword) {
        String kw = keyword == null ? "" : keyword.toLowerCase().trim();
        try {
            List<Formation> all = coachId > 0
                    ? formationService.selectByCoach(coachId)
                    : formationService.selectALL();
            List<Formation> filtered = kw.isEmpty() ? all : all.stream()
                    .filter(f -> f.getTitle().toLowerCase().contains(kw)
                            || (f.getCategory() != null && f.getCategory().toLowerCase().contains(kw))
                            || (f.getDescription() != null && f.getDescription().toLowerCase().contains(kw)))
                    .toList();
            formationData.setAll(filtered);
            updateCount(filtered.size());
        } catch (SQLException e) {
            System.err.println("Filter error: " + e.getMessage());
        }
    }

    private void updateCount(int count) {
        if (lblTotal != null) lblTotal.setText("Total : " + count + " formation(s)");
    }

    // ════════════════════════════════════════════════════════════════════
    //  NAVIGATION BETWEEN SECTIONS
    // ════════════════════════════════════════════════════════════════════

    @FXML
    private void showAddForm() {
        selectedFormation = null;
        clearForm();
        lblFormTitle.setText("➕ Ajouter une Formation");
        showSection(formScrollPane);
    }

    private void editFormation(Formation f) {
        selectedFormation = f;
        tfTitle.setText(f.getTitle());
        taDescription.setText(f.getDescription());
        cbCategory.setValue(f.getCategory());
        tfVideoUrl.setText(f.getVideoUrl() != null ? f.getVideoUrl() : "");
        lblFormTitle.setText("✏️ Modifier — " + f.getTitle());
        showSection(formScrollPane);
        loadQuizPanel(f);
    }

    @FXML
    private void showTable() {
        clearForm();
        showSection(tableSection);
        loadFormations();
    }

    private void showSection(javafx.scene.Node visible) {
        formationContent.getChildren().forEach(n -> {
            n.setVisible(n == visible);
            n.setManaged(n == visible);
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  SAVE / CLEAR / REFRESH
    // ════════════════════════════════════════════════════════════════════

    @FXML
    private void handleSave() {
        String title    = tfTitle.getText().trim();
        String desc     = taDescription.getText().trim();
        String category = cbCategory.getValue();
        String videoUrl = tfVideoUrl.getText().trim();

        if (title.isEmpty() || category == null) {
            showError(lblError, "Le titre et la catégorie sont obligatoires.");
            return;
        }
        lblError.setText("");

        Formation f = selectedFormation != null ? selectedFormation : new Formation();
        f.setTitle(title);
        f.setDescription(desc);
        f.setCategory(category);
        f.setVideoUrl(videoUrl.isEmpty() ? null : videoUrl);
        if (coachId > 0) f.setCoachId(coachId);

        // Parse coach from combo if admin selected one
        if (cbCoach.getValue() != null && !cbCoach.getValue().isEmpty()) {
            try {
                int cid = Integer.parseInt(cbCoach.getValue().split(" — ")[0].trim());
                f.setCoachId(cid);
            } catch (NumberFormatException ignored) {}
        }

        try {
            if (selectedFormation == null) {
                formationService.insertOne(f);
                selectedFormation = formationService.selectALL().stream()
                        .filter(x -> x.getTitle().equals(title)).findFirst().orElse(null);
            } else {
                formationService.updateOne(f);
            }
            loadFormations();
            if (selectedFormation != null) loadQuizPanel(selectedFormation);
            showAlert("✅ Formation enregistrée avec succès !");
        } catch (SQLException e) {
            showError(lblError, "Erreur : " + e.getMessage());
        }
    }

    @FXML private void handleClear()   { clearForm(); }
    @FXML private void handleRefresh() { loadFormations(); }

    @FXML
    private void handleSearch() { /* listener already active */ }

    private void clearForm() {
        tfTitle.clear(); taDescription.clear();
        cbCategory.setValue(null); cbCoach.setValue(null);
        tfVideoUrl.clear(); tfQuizTitle.clear(); tfPassingScore.setText("70");
        lblError.setText(""); lblQuizError.setText("");
        if (questionsArea != null) { questionsArea.setVisible(false); questionsArea.setManaged(false); }
        selectedFormation = null;
    }

    // ════════════════════════════════════════════════════════════════════
    //  VIDEO CHOOSER & YOUTUBE SEARCH (API 1: YouTube oEmbed validation)
    // ════════════════════════════════════════════════════════════════════

    @FXML
    private void handleChooseVideo() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir une vidéo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vidéos", "*.mp4", "*.avi", "*.mkv"));
        File f = fc.showOpenDialog(tfVideoUrl.getScene().getWindow());
        if (f != null) tfVideoUrl.setText(f.getAbsolutePath());
    }

    @FXML
    private void handleSearchYouTube() {
        // Initialize YouTubeAPIService if not already done
        if (youtubeService == null) {
            youtubeService = new services.YouTubeAPIService();
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("🔍 Rechercher sur YouTube");
        dialog.setHeaderText("Rechercher une vidéo pour votre formation");

        ButtonType selectBtn = new ButtonType("Sélectionner", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectBtn, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().setPrefHeight(540);

        // Search field
        TextField tfQuery = new TextField();
        tfQuery.setPromptText("Ex: stress management, communication skills...");
        tfQuery.setStyle("-fx-padding:10;-fx-font-size:14px;-fx-background-radius:8;"
                + "-fx-border-color:#E2E8F0;-fx-border-radius:8;-fx-border-width:1.5;");

        Button btnSearch = new Button("🔍 Chercher");
        btnSearch.setStyle("-fx-background-color:#d63031;-fx-text-fill:white;"
                + "-fx-font-weight:bold;-fx-padding:10 20;-fx-background-radius:8;-fx-cursor:hand;");

        HBox searchRow = new HBox(10, tfQuery, btnSearch);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfQuery, Priority.ALWAYS);

        Label lblStatus = new Label("");
        lblStatus.setStyle("-fx-font-size:12px;-fx-text-fill:#718096;");

        // Results list
        ListView<services.YouTubeAPIService.YouTubeVideo> listResults = new ListView<>();
        listResults.setPrefHeight(380);
        VBox.setVgrow(listResults, Priority.ALWAYS);
        listResults.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(services.YouTubeAPIService.YouTubeVideo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                VBox cell = new VBox(3);
                cell.setPadding(new Insets(6));
                Label tl = new Label("🎬 " + item.title);
                tl.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#2D3748;");
                tl.setWrapText(true);
                Label cl = new Label("📺 " + item.channelTitle);
                cl.setStyle("-fx-text-fill:#718096;-fx-font-size:11px;");
                cell.getChildren().addAll(tl, cl);
                setGraphic(cell);
            }
        });

        VBox content = new VBox(10, searchRow, lblStatus, listResults);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);

        // Search action — called on button click or Enter key
        Runnable doSearch = () -> {
            String query = tfQuery.getText().trim();
            if (query.length() < 2) {
                lblStatus.setText("❌ Entrez au moins 2 caractères");
                return;
            }
            lblStatus.setText("⏳ Recherche en cours...");
            listResults.getItems().clear();
            new Thread(() -> {
                try {
                    List<services.YouTubeAPIService.YouTubeVideo> results =
                            youtubeService.searchVideos(query, 10);
                    javafx.application.Platform.runLater(() -> {
                        listResults.getItems().addAll(results);
                        lblStatus.setText("✅ " + results.size() + " résultat(s) trouvé(s)");
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() ->
                            lblStatus.setText("❌ Erreur : " + ex.getMessage()));
                }
            }, "yt-search-thread").start();
        };

        btnSearch.setOnAction(e -> doSearch.run());
        tfQuery.setOnAction(e -> doSearch.run());

        // Pre-fill search with formation title if available
        if (selectedFormation != null && !selectedFormation.getTitle().isBlank()) {
            tfQuery.setText(selectedFormation.getTitle());
        }

        dialog.setResultConverter(btn -> {
            if (btn == selectBtn) {
                services.YouTubeAPIService.YouTubeVideo sel =
                        listResults.getSelectionModel().getSelectedItem();
                return sel != null ? sel.youtubeUrl : null;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(youtubeUrl -> {
            if (tfVideoUrl != null) tfVideoUrl.setText(youtubeUrl);
        });
    }
    // ════════════════════════════════════════════════════════════════════
    //  QUIZ PANEL
    // ════════════════════════════════════════════════════════════════════

    private void loadQuizPanel(Formation f) {
        if (questionsArea == null) return;
        try {
            Quiz quiz = quizService.selectByFormation(f.getId());
            if (quiz != null) {
                tfQuizTitle.setText(quiz.getTitle());
                tfPassingScore.setText(String.valueOf(quiz.getPassingScore()));
                questionsArea.setVisible(true);
                questionsArea.setManaged(true);
                loadQuestionsList(quiz);
            }
        } catch (SQLException ignored) {}
    }

    @FXML
    private void handleSaveQuiz() {
        if (selectedFormation == null) { showError(lblQuizError, "Enregistrez la formation d'abord."); return; }
        String title = tfQuizTitle.getText().trim();
        if (title.isEmpty()) { showError(lblQuizError, "Donnez un titre au quiz."); return; }
        int passing = 70;
        try { passing = Integer.parseInt(tfPassingScore.getText().trim()); } catch (NumberFormatException ignored) {}
        try {
            Quiz existing = quizService.selectByFormation(selectedFormation.getId());
            if (existing == null) {
                Quiz q = new Quiz();
                q.setTitle(title); q.setFormationId(selectedFormation.getId()); q.setPassingScore(passing);
                quizService.insertOne(q);
            } else {
                existing.setTitle(title); existing.setPassingScore(passing);
                quizService.updateOne(existing);
            }
            lblQuizError.setText("");
            questionsArea.setVisible(true); questionsArea.setManaged(true);
            Quiz saved = quizService.selectByFormation(selectedFormation.getId());
            if (saved != null) loadQuestionsList(saved);
            showAlert("✅ Quiz enregistré !");
        } catch (SQLException e) { showError(lblQuizError, "Erreur : " + e.getMessage()); }
    }

    @FXML
    private void handleDeleteQuiz() {
        if (selectedFormation == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le quiz ?");
        confirm.setContentText("Toutes les questions et résultats seront supprimés.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Quiz q = quizService.selectByFormation(selectedFormation.getId());
                    if (q != null) quizService.deleteOne(q);
                    questionsArea.setVisible(false); questionsArea.setManaged(false);
                    tfQuizTitle.clear();
                } catch (SQLException e) { showError(lblQuizError, "Erreur : " + e.getMessage()); }
            }
        });
    }

    private void loadQuestionsList(Quiz quiz) {
        if (questionsList == null) return;
        questionsList.getChildren().clear();
        try {
            List<Question> questions = questionService.selectByQuiz(quiz.getId());
            for (Question q : questions) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setStyle("-fx-background-color:#F7FAFC;-fx-padding:8 10;-fx-background-radius:6;");
                Label lbl = new Label("Q: " + q.getQuestionText());
                lbl.setWrapText(true); lbl.setMaxWidth(200);
                lbl.setStyle("-fx-font-size:12px;-fx-text-fill:#2D3748;");
                HBox sp = new HBox(); HBox.setHgrow(sp, Priority.ALWAYS);
                Button btnDel = new Button("🗑️");
                btnDel.setStyle("-fx-background-color:#E53E3E;-fx-text-fill:white;-fx-background-radius:4;-fx-cursor:hand;-fx-padding:3 7;");
                btnDel.setOnAction(e -> {
                    try { questionService.deleteOne(q); loadQuestionsList(quiz); } catch (SQLException ex) { System.err.println(ex.getMessage()); }
                });
                row.getChildren().addAll(lbl, sp, btnDel);
                questionsList.getChildren().add(row);
            }
        } catch (SQLException e) { System.err.println("Load questions: " + e.getMessage()); }
    }

    @FXML
    private void handleAddQuestion() {
        if (selectedFormation == null) return;
        try {
            Quiz quiz = quizService.selectByFormation(selectedFormation.getId());
            if (quiz == null) { showError(lblQuizError, "Enregistrez le quiz d'abord."); return; }
            showAddQuestionDialog(quiz);
        } catch (SQLException e) { showError(lblQuizError, "Erreur : " + e.getMessage()); }
    }

    private void showAddQuestionDialog(Quiz quiz) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("➕ Ajouter une Question");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(480);

        VBox form = new VBox(12);
        form.setPadding(new Insets(16));
        TextField tfQ = new TextField(); tfQ.setPromptText("Texte de la question");
        TextField tfPts = new TextField("1"); tfPts.setPromptText("Points");

        Label lblOpts = new Label("Options (la bonne réponse en premier) :");
        lblOpts.setStyle("-fx-font-weight:bold;");
        TextField[] opts = new TextField[4];
        for (int i = 0; i < 4; i++) { opts[i] = new TextField(); opts[i].setPromptText("Option " + (i + 1)); }
        form.getChildren().addAll(new Label("Question :"), tfQ, new Label("Points :"), tfPts, lblOpts);
        for (TextField opt : opts) form.getChildren().add(opt);
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                Question q = new Question();
                q.setQuizId(quiz.getId());
                q.setQuestionText(tfQ.getText().trim());
                int pts = 1;
                try { pts = Integer.parseInt(tfPts.getText().trim()); } catch (NumberFormatException ignored) {}
                q.setPoints(pts);
                questionService.insertOne(q);
                List<Question> all = questionService.selectByQuiz(quiz.getId());
                if (!all.isEmpty()) {
                    Question inserted = all.get(all.size() - 1);
                    for (int i = 0; i < 4; i++) {
                        if (opts[i].getText().isBlank()) continue;
                        Reponse r = new Reponse();
                        r.setQuestionId(inserted.getId());
                        r.setOptionText(opts[i].getText().trim());
                        r.setCorrect(i == 0);
                        reponseService.insertOne(r);
                    }
                }
                loadQuestionsList(quiz);
            } catch (SQLException e) { System.err.println("Add question: " + e.getMessage()); }
            return null;
        });
        dialog.showAndWait();
    }

    // ════════════════════════════════════════════════════════════════════
    //  API 2: OpenRouter AI — generate quiz questions
    // ════════════════════════════════════════════════════════════════════

    @FXML
    private void handleAutoGenerateQuestions() {
        if (selectedFormation == null) { showError(lblQuizError, "Enregistrez la formation d'abord."); return; }
        try {
            Quiz quiz = quizService.selectByFormation(selectedFormation.getId());
            if (quiz == null) { showError(lblQuizError, "Créez le quiz d'abord."); return; }

            lblQuizError.setStyle("-fx-text-fill:#6c5ce7;");
            lblQuizError.setText("🤖 Génération IA en cours...");

            new Thread(() -> {
                try {
                    List<QuizGeneratorAPI.GeneratedQuestion> generated =
                            quizGeneratorAPI.generateQuestions(selectedFormation.getTitle(), selectedFormation.getDescription(), 5);
                    for (QuizGeneratorAPI.GeneratedQuestion gq : generated) {
                        Question q = new Question();
                        q.setQuizId(quiz.getId());
                        q.setQuestionText(gq.questionText);
                        q.setPoints(gq.points);
                        questionService.insertOne(q);
                        List<Question> all = questionService.selectByQuiz(quiz.getId());
                        if (!all.isEmpty()) {
                            Question inserted = all.get(all.size() - 1);
                            for (int i = 0; i < gq.options.size(); i++) {
                                Reponse r = new Reponse();
                                r.setQuestionId(inserted.getId());
                                r.setOptionText(gq.options.get(i));
                                r.setCorrect(i == gq.correctIndex);
                                reponseService.insertOne(r);
                            }
                        }
                    }
                    Platform.runLater(() -> {
                        lblQuizError.setStyle("-fx-text-fill:#00b894;");
                        lblQuizError.setText("✅ " + generated.size() + " questions générées !");
                        loadQuestionsList(quiz);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError(lblQuizError, "❌ Erreur IA : " + e.getMessage()));
                }
            }, "ai-quiz-thread").start();
        } catch (SQLException e) { showError(lblQuizError, "Erreur : " + e.getMessage()); }
    }

    // ════════════════════════════════════════════════════════════════════
    //  VIDEO PREVIEW
    // ════════════════════════════════════════════════════════════════════

    private void previewVideo(Formation f) {
        String url = f.getVideoUrl();
        if (url == null || url.isBlank()) { showAlert("Aucune vidéo disponible."); return; }
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("▶ " + f.getTitle());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(740, 520);
        VBox container = VideoPlayerUtil.isYouTubeUrl(url)
                ? VideoPlayerUtil.createYouTubePlayer(url)
                : (new File(url).exists() ? VideoPlayerUtil.createLocalPlayer(url) : VideoPlayerUtil.createErrorMessage("Fichier non trouvé", url));
        dialog.getDialogPane().setContent(container);
        dialog.setOnCloseRequest(e -> VideoPlayerUtil.stopMedia(container));
        dialog.showAndWait();
    }

    // ════════════════════════════════════════════════════════════════════
    //  STATISTICS — per formation (API 3: DictionaryAPI for topic info)
    // ════════════════════════════════════════════════════════════════════

    private void showFormationStats(Formation f) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("📊 Statistiques — " + f.getTitle());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(520, 440);

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getChildren().add(new Label("⏳ Chargement..."));
        dialog.getDialogPane().setContent(content);

        new Thread(() -> {
            try {
                List<Participant> parts = participantService.selectByFormation(f.getId());
                Quiz quiz = quizService.selectByFormation(f.getId());
                List<QuizResult> results = quiz != null
                        ? quizResultService.selectALL().stream().filter(r -> r.getQuizId() == quiz.getId()).toList()
                        : List.of();
                long passed = results.stream().filter(QuizResult::isPassed).count();
                double avg  = results.stream().mapToDouble(QuizResult::getPercentage).average().orElse(0);
                double taux = results.isEmpty() ? 0 : (passed * 100.0 / results.size());

                Platform.runLater(() -> {
                    content.getChildren().clear();
                    Label title = new Label(f.getTitle());
                    title.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#2D3748;");

                    VBox statsBox = new VBox(10);
                    statsBox.setStyle("-fx-background-color:white;-fx-padding:16;-fx-background-radius:10;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),6,0,0,2);");
                    statsBox.getChildren().addAll(
                            statRow("👥 Inscrits",          String.valueOf(parts.size())),
                            statRow("📝 Quiz associé",      quiz != null ? quiz.getTitle() : "Aucun"),
                            statRow("🎯 Tentatives",        String.valueOf(results.size())),
                            statRow("✅ Réussites",         String.valueOf(passed)),
                            statRow("📈 Taux de réussite",  String.format("%.0f%%", taux)),
                            statRow("⭐ Score moyen",       String.format("%.1f%%", avg))
                    );

                    ProgressBar pb = new ProgressBar(taux / 100.0);
                    pb.setPrefWidth(420);
                    pb.setStyle("-fx-accent:" + (taux >= 70 ? "#00b894" : taux >= 40 ? "#fdcb6e" : "#d63031") + ";");

                    content.getChildren().addAll(title, statsBox, new Label("Progression globale :"), pb);
                });
            } catch (Exception e) {
                Platform.runLater(() -> { content.getChildren().clear(); content.getChildren().add(new Label("Erreur : " + e.getMessage())); });
            }
        }, "fstats-thread").start();

        dialog.showAndWait();
    }

    private HBox statRow(String label, String value) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label); lbl.setMinWidth(200);
        lbl.setStyle("-fx-font-size:13px;-fx-text-fill:#636e72;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#2d3436;");
        row.getChildren().addAll(lbl, val);
        return row;
    }

    // ════════════════════════════════════════════════════════════════════
    //  DELETE
    // ════════════════════════════════════════════════════════════════════

    private void deleteFormation(Formation f) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer"); confirm.setHeaderText(null);
        confirm.setContentText("Supprimer \"" + f.getTitle() + "\" ? Cette action est irréversible.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try { formationService.deleteOne(f); loadFormations(); }
                catch (SQLException e) { showAlert("Erreur : " + e.getMessage()); }
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Info"); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private void showError(Label lbl, String msg) {
        if (lbl == null) return;
        lbl.setStyle("-fx-text-fill:#E53E3E;-fx-font-size:12px;-fx-background-color:#FFF5F5;-fx-padding:6 10;-fx-background-radius:5;");
        lbl.setText(msg);
    }
}