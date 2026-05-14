package controllers;

import entities.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import services.*;
import utils.LightDialog;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

public class AdminHome {

    // ── Labels ────────────────────────────────────────────────────────────
    @FXML private Label lblWelcome, lblHeaderAvatar;
    @FXML private Label lblNbPatients, lblNbCoaches, lblNbAdmins, lblNbTotal;
    @FXML private Label lblPctPatients, lblPctCoaches, lblPctAdmins;
    @FXML private ProgressBar pbPatients, pbCoaches, pbAdmins;
    @FXML private Label lblNbFormations, lblNbAvecVideo, lblNbAvecQuiz, lblNbCategories;
    @FXML private WebView wvUserChart, wvFormationChart;
    @FXML private StackPane contentArea;
    @FXML private ScrollPane dashboardPane;
    @FXML private ImageView imgHeaderPhoto;
    @FXML private VBox navDashboard, navUtilisateurs, navFormations, navProfil;
    @FXML private HBox indicDashboard, indicUtilisateurs, indicFormations, indicProfil;

    // ── Services ──────────────────────────────────────────────────────────
    private final serviceUser us = new serviceUser();
    private FormationService   formationService;
    private QuizService        quizService;
    private ParticipantService participantService;
    private QuizResultService  quizResultService;

    private User currentUser;
    private VBox currentActiveNav;

    private static final String OPENROUTER_URL   = "https://openrouter.ai/api/v1/chat/completions";
    private static final String OPENROUTER_KEY   = "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";
    private static final String OPENROUTER_MODEL = "mistralai/mistral-7b-instruct:free";

    private static final String NAV_NORMAL    = "-fx-padding:12 20 12 16;-fx-cursor:hand;-fx-background-color:transparent;-fx-background-radius:0 10 10 0;";
    private static final String NAV_ACTIVE    = "-fx-padding:12 20 12 16;-fx-cursor:hand;-fx-background-color:rgba(255,255,255,0.13);-fx-background-radius:0 10 10 0;";
    private static final String INDIC_HIDDEN  = "-fx-min-width:4;-fx-max-width:4;-fx-min-height:30;-fx-background-color:transparent;-fx-background-radius:0 2 2 0;";
    private static final String INDIC_VISIBLE = "-fx-min-width:4;-fx-max-width:4;-fx-min-height:30;-fx-background-color:#E8956D;-fx-background-radius:0 2 2 0;";

    // ════════════════════════════════════════════════════════════════════
    //  INIT
    // ════════════════════════════════════════════════════════════════════

    @FXML void initialize() {
        try { formationService   = new FormationService();   } catch (Exception ignored) {}
        try { quizService        = new QuizService();        } catch (Exception ignored) {}
        try { participantService = new ParticipantService(); } catch (Exception ignored) {}
        try { quizResultService  = new QuizResultService();  } catch (Exception ignored) {}
    }

    public void setUser(User user) {
        this.currentUser = user;
        refreshUserData();
        refreshStats(null);
        setActiveNav(navDashboard, indicDashboard);
    }

    // ════════════════════════════════════════════════════════════════════
    //  USER DATA
    // ════════════════════════════════════════════════════════════════════

    public void refreshUserData() {
        try {
            User updated = us.getUserById(currentUser.getId_user());
            if (updated != null) this.currentUser = updated;
        } catch (SQLException e) { System.err.println("refreshUserData: " + e.getMessage()); }
        lblWelcome.setText(currentUser.getPrenom() + " " + currentUser.getNom());
        lblHeaderAvatar.setText(currentUser.getPrenom().substring(0, 1).toUpperCase());
        updateHeaderPhoto();
    }

    private void updateHeaderPhoto() {
        if (currentUser.getPhoto() != null && !currentUser.getPhoto().isEmpty()) {
            File f = new File(currentUser.getPhoto());
            if (f.exists()) {
                imgHeaderPhoto.setImage(new Image(f.toURI() + "?t=" + System.currentTimeMillis(), 40, 40, false, true));
                imgHeaderPhoto.setVisible(true); lblHeaderAvatar.setVisible(false); return;
            }
        }
        imgHeaderPhoto.setVisible(false); lblHeaderAvatar.setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════════
    //  STATS
    // ════════════════════════════════════════════════════════════════════

    @FXML void refreshStats(ActionEvent event) { loadUserStats(); loadFormationStats(); }

    private void loadUserStats() {
        try {
            List<User> users = us.selectALL();
            int p = 0, c = 0, a = 0;
            for (User u : users) {
                switch (u.getRole().toUpperCase()) {
                    case "PATIENT" -> p++;
                    case "COACH"   -> c++;
                    case "ADMIN"   -> a++;
                }
            }
            int total = users.size();
            lblNbPatients.setText(String.valueOf(p));
            lblNbCoaches.setText(String.valueOf(c));
            lblNbAdmins.setText(String.valueOf(a));
            lblNbTotal.setText(String.valueOf(total));
            if (total > 0) {
                double pp = p / (double) total, pc = c / (double) total, pa = a / (double) total;
                if (pbPatients != null) pbPatients.setProgress(pp);
                if (pbCoaches  != null) pbCoaches.setProgress(pc);
                if (pbAdmins   != null) pbAdmins.setProgress(pa);
                if (lblPctPatients != null) lblPctPatients.setText(String.format("%.0f%%", pp * 100));
                if (lblPctCoaches  != null) lblPctCoaches.setText(String.format("%.0f%%",  pc * 100));
                if (lblPctAdmins   != null) lblPctAdmins.setText(String.format("%.0f%%",   pa * 100));
            }
            loadUserChart(p, c, a);
        } catch (SQLException e) { LightDialog.showError("Erreur", "Impossible de charger les stats utilisateurs."); }
    }

    private void loadFormationStats() {
        if (formationService == null) return;
        try {
            List<Formation> formations = formationService.selectALL();
            int total = formations.size(), avecVideo = 0, avecQuiz = 0;
            Set<String> categories = new HashSet<>();
            for (Formation f : formations) {
                if (f.getVideoUrl() != null && !f.getVideoUrl().isBlank()) avecVideo++;
                if (f.getCategory() != null && !f.getCategory().isBlank()) categories.add(f.getCategory().trim());
                if (quizService != null) {
                    try { if (quizService.selectByFormation(f.getId()) != null) avecQuiz++; } catch (Exception ignored) {}
                }
            }
            if (lblNbFormations != null) lblNbFormations.setText(String.valueOf(total));
            if (lblNbAvecVideo  != null) lblNbAvecVideo.setText(String.valueOf(avecVideo));
            if (lblNbAvecQuiz   != null) lblNbAvecQuiz.setText(String.valueOf(avecQuiz));
            if (lblNbCategories != null) lblNbCategories.setText(String.valueOf(categories.size()));
            loadFormationDonutChart(formations);
        } catch (SQLException e) {
            System.err.println("Formation stats: " + e.getMessage());
            for (Label lbl : new Label[]{lblNbFormations, lblNbAvecVideo, lblNbAvecQuiz, lblNbCategories})
                if (lbl != null) lbl.setText("—");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  CHARTS
    // ════════════════════════════════════════════════════════════════════

    private void loadUserChart(int patients, int coaches, int admins) {
        if (wvUserChart == null) return;
        if (patients + coaches + admins == 0) return;
        String html = """
        <!DOCTYPE html><html><head>
        <script src="https://www.gstatic.com/charts/loader.js"></script>
        <script>
          google.charts.load('current',{packages:['corechart']});
          google.charts.setOnLoadCallback(draw);
          function draw() {
            var data = google.visualization.arrayToDataTable([
              ['Rôle','Nombre'],
              ['Patients',%d],['Coaches',%d],['Admins',%d]
            ]);
            new google.visualization.PieChart(document.getElementById('chart'))
              .draw(data,{pieHole:0.45,legend:{position:'bottom',textStyle:{color:'#4A5568',fontSize:12}},
                backgroundColor:'transparent',colors:['#E8956D','#4A6FA5','#6C5CE7'],
                chartArea:{width:'90%%',height:'80%%'},pieSliceTextStyle:{color:'white',fontSize:12}});
          }
        </script></head>
        <body style="margin:0;background:transparent;">
          <div id="chart" style="width:100%%;height:270px;"></div>
        </body></html>""".formatted(patients, coaches, admins);
        wvUserChart.getEngine().loadContent(html);
    }

    private void loadFormationDonutChart(List<Formation> formations) {
        if (wvFormationChart == null || formations.isEmpty()) return;
        Map<String, Long> byCategory = new LinkedHashMap<>();
        try {
            List<Participant> allParts = participantService != null ? participantService.selectALL() : List.of();
            for (Formation f : formations) {
                String cat = f.getCategory() != null && !f.getCategory().isBlank() ? f.getCategory() : "Autre";
                long enrolled = allParts.stream().filter(p -> p.getFormationId() == f.getId()).count();
                byCategory.merge(cat, enrolled + 1, Long::sum);
            }
        } catch (Exception e) {
            for (Formation f : formations) {
                String cat = f.getCategory() != null && !f.getCategory().isBlank() ? f.getCategory() : "Autre";
                byCategory.merge(cat, 1L, Long::sum);
            }
        }
        StringBuilder rows = new StringBuilder();
        byCategory.forEach((cat, count) ->
                rows.append(String.format("['%s',%d],\n", cat.replace("'", "\\'"), count)));
        String html = """
        <!DOCTYPE html><html><head>
        <script src="https://www.gstatic.com/charts/loader.js"></script>
        <script>
          google.charts.load('current',{packages:['corechart']});
          google.charts.setOnLoadCallback(draw);
          function draw() {
            var data = google.visualization.arrayToDataTable([
              ['Catégorie','Inscriptions'],%s
            ]);
            new google.visualization.PieChart(document.getElementById('chart'))
              .draw(data,{pieHole:0.4,legend:{position:'right',textStyle:{color:'#4A5568',fontSize:11}},
                backgroundColor:'transparent',colors:['#E8956D','#4A6FA5','#6C5CE7','#00B894','#FDCB6E','#E17055','#74B9FF'],
                chartArea:{width:'85%%',height:'82%%'},pieSliceTextStyle:{color:'white',fontSize:11},
                title:'Inscriptions par catégorie',titleTextStyle:{color:'#2D3748',fontSize:12,bold:true}});
          }
        </script></head>
        <body style="margin:0;background:transparent;">
          <div id="chart" style="width:100%%;height:280px;"></div>
        </body></html>""".formatted(rows.toString());
        wvFormationChart.getEngine().loadContent(html);
    }

    // ════════════════════════════════════════════════════════════════════
    //  🤖 AI ANALYSIS — Real OpenRouter Mistral-7B
    // ════════════════════════════════════════════════════════════════════

    @FXML
    void analyzeWithAI(MouseEvent event) {
        // Gather stats
        int nbFormations = 0, nbPatients = 0, nbCoaches = 0;
        long nbResults = 0, nbPassed = 0;
        try {
            List<User> users = us.selectALL();
            nbPatients = (int) users.stream().filter(u -> "PATIENT".equalsIgnoreCase(u.getRole())).count();
            nbCoaches  = (int) users.stream().filter(u -> "COACH".equalsIgnoreCase(u.getRole())).count();
            if (formationService != null) nbFormations = formationService.selectALL().size();
            if (quizResultService != null) {
                List<QuizResult> results = quizResultService.selectALL();
                nbResults = results.size();
                nbPassed  = results.stream().filter(QuizResult::isPassed).count();
            }
        } catch (Exception e) { System.err.println("AI stats: " + e.getMessage()); }

        double taux = nbResults > 0 ? (nbPassed * 100.0 / nbResults) : 0;

        // Show loading dialog
        Alert loading = new Alert(Alert.AlertType.INFORMATION);
        loading.setTitle("🤖 Analyse IA");
        loading.setHeaderText("Analyse en cours...");
        loading.setContentText("⏳ Mistral-7B analyse vos statistiques EchoCare...");
        loading.show();

        final int fFormations = nbFormations, fPatients = nbPatients, fCoaches = nbCoaches;
        final long fResults = nbResults, fPassed = nbPassed;
        final double fTaux = taux;

        new Thread(() -> {
            try {
                String prompt = "Tu es un consultant expert en e-learning et santé mentale. "
                        + "Voici les statistiques de la plateforme EchoCare (application de bien-être mental) : "
                        + fFormations + " formations disponibles, "
                        + fPatients + " patients inscrits, "
                        + fCoaches + " coaches actifs, "
                        + fResults + " tentatives de quiz au total, "
                        + fPassed + " quiz réussis, "
                        + String.format("%.0f%%", fTaux) + " taux de réussite global. "
                        + "En 4-5 phrases courtes et concrètes en français : "
                        + "1) Donne une analyse de la situation actuelle. "
                        + "2) Identifie le point fort principal. "
                        + "3) Donne 2 recommandations prioritaires pour améliorer les résultats. "
                        + "Sois direct et pratique.";

                String body = "{\"model\":\"" + OPENROUTER_MODEL + "\","
                        + "\"messages\":[{\"role\":\"user\",\"content\":"
                        + jsonStr(prompt) + "}],"
                        + "\"max_tokens\":600,\"temperature\":0.7}";

                URL url = new URL(OPENROUTER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + OPENROUTER_KEY);
                conn.setRequestProperty("HTTP-Referer", "https://echocare.app");
                conn.setRequestProperty("X-Title", "EchoCare");
                conn.setDoOutput(true);
                conn.setConnectTimeout(20_000);
                conn.setReadTimeout(60_000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }

                String raw = sb.toString();
                System.out.println("[AI] Response (300): " + raw.substring(0, Math.min(300, raw.length())));

                // Extract content field
                String analysis = extractStringField(raw, "content");
                if (analysis == null || analysis.isBlank())
                    analysis = "Analyse non disponible. Vérifiez votre connexion internet.";

                final String finalAnalysis = analysis;
                Platform.runLater(() -> {
                    loading.close();
                    Alert result = new Alert(Alert.AlertType.INFORMATION);
                    result.setTitle("🤖 Analyse IA — EchoCare");
                    result.setHeaderText("📊 Analyse Mistral-7B de vos statistiques");
                    result.setContentText(finalAnalysis);
                    result.getDialogPane().setPrefWidth(580);
                    result.getDialogPane().setStyle("-fx-font-size:13px;");
                    result.showAndWait();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    loading.close();
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Erreur IA");
                    err.setContentText("Impossible de contacter l'IA : " + e.getMessage()
                            + "\n\nVérifiez votre connexion internet.");
                    err.showAndWait();
                });
            }
        }, "ai-analysis-thread").start();
    }

    // ════════════════════════════════════════════════════════════════════
    //  📄 EXPORT RAPPORT + Fun Trivia
    // ════════════════════════════════════════════════════════════════════

    @FXML
    void exportReport(MouseEvent event) {
        try {
            List<User> users = us.selectALL();
            long nbP = users.stream().filter(u -> "PATIENT".equalsIgnoreCase(u.getRole())).count();
            long nbC = users.stream().filter(u -> "COACH".equalsIgnoreCase(u.getRole())).count();
            long nbA = users.stream().filter(u -> "ADMIN".equalsIgnoreCase(u.getRole())).count();
            List<Formation> formations = formationService != null ? formationService.selectALL() : List.of();
            long nbResults = quizResultService != null ? quizResultService.selectALL().size() : 0;
            long nbPassed  = quizResultService != null
                    ? quizResultService.selectALL().stream().filter(QuizResult::isPassed).count() : 0;
            double taux = nbResults > 0 ? (nbPassed * 100.0 / nbResults) : 0;

            String report = String.format("""
                ╔══════════════════════════════════════════╗
                ║        RAPPORT STATISTIQUES ECHOCARE     ║
                ╠══════════════════════════════════════════╣
                ║  Généré le : %s
                ║  Par       : %s %s
                ╠══════════════════════════════════════════╣
                ║  UTILISATEURS                            ║
                ║  • Total        : %d
                ║  • Patients     : %d
                ║  • Coaches      : %d
                ║  • Admins       : %d
                ╠══════════════════════════════════════════╣
                ║  FORMATIONS                              ║
                ║  • Total        : %d
                ╠══════════════════════════════════════════╣
                ║  QUIZ                                    ║
                ║  • Tentatives   : %d
                ║  • Réussites    : %d
                ║  • Taux réussite: %.0f%%
                ╚══════════════════════════════════════════╝
                """,
                    java.time.LocalDate.now(),
                    currentUser.getPrenom(), currentUser.getNom(),
                    users.size(), nbP, nbC, nbA,
                    formations.size(), nbResults, nbPassed, taux);

            String path = System.getProperty("user.home") + "/EchoCare_Rapport_" + java.time.LocalDate.now() + ".txt";
            java.nio.file.Files.writeString(java.nio.file.Path.of(path), report);

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("📄 Rapport exporté");
            a.setHeaderText("✅ Rapport enregistré !");
            a.setContentText("Fichier : " + path);
            ButtonType openBtn = new ButtonType("📂 Ouvrir");
            ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(openBtn, ok);
            a.showAndWait().ifPresent(b -> {
                if (b == openBtn) { try { java.awt.Desktop.getDesktop().open(new java.io.File(path)); } catch (Exception ignored) {} }
            });
            showTriviaFact();
        } catch (Exception e) { LightDialog.showError("Erreur export", e.getMessage()); }
    }

    private void showTriviaFact() {
        String[][] localQuestions = {
                {"L'écoute active consiste à reformuler ce que dit l'interlocuteur.", "Vrai"},
                {"Le langage non-verbal représente moins de 10% de la communication.", "Faux"},
                {"L'intelligence émotionnelle peut se développer avec la pratique.", "Vrai"},
                {"Un feedback constructif doit toujours commencer par une critique.", "Faux"},
                {"La communication assertive permet d'exprimer ses besoins sans agressivité.", "Vrai"},
                {"Les soft skills ne sont pas évaluables en entreprise.", "Faux"},
                {"L'empathie est une compétence clé du leadership.", "Vrai"},
                {"Un conflit en équipe est toujours négatif pour la productivité.", "Faux"},
                {"La communication non-violente (CNV) favorise la résolution de conflits.", "Vrai"},
                {"Écouter activement signifie simplement ne pas parler.", "Faux"}
        };
        new Thread(() -> {
            int idx = (int)(System.currentTimeMillis() / 5000 % localQuestions.length);
            String questionText = localQuestions[idx][0];
            String correctAnswer = localQuestions[idx][1];
            Platform.runLater(() -> {
                javafx.stage.Stage dialogStage = new javafx.stage.Stage();
                dialogStage.setTitle("🧠 Fun & Learn");
                dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
                VBox root = new VBox(20); root.setStyle("-fx-padding:30;-fx-background-color:#F7FAFC;"); root.setAlignment(javafx.geometry.Pos.CENTER); root.setPrefWidth(480);
                Label lblTitle = new Label("🧠 Fun & Learn — Soft Skills"); lblTitle.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#2D3748;");
                Label lblQ = new Label(questionText); lblQ.setWrapText(true); lblQ.setMaxWidth(420);
                lblQ.setStyle("-fx-font-size:14px;-fx-text-fill:#4A5568;-fx-font-style:italic;-fx-background-color:white;-fx-padding:16;-fx-background-radius:10;-fx-border-color:#E2E8F0;-fx-border-radius:10;");
                Label lblResult = new Label(""); lblResult.setWrapText(true); lblResult.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");
                HBox buttons = new HBox(16); buttons.setAlignment(javafx.geometry.Pos.CENTER);
                Button btnVrai = new Button("✅  VRAI"); btnVrai.setStyle("-fx-background-color:#00B894;-fx-text-fill:white;-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:12 30;-fx-background-radius:10;-fx-cursor:hand;");
                Button btnFaux = new Button("❌  FAUX"); btnFaux.setStyle("-fx-background-color:#E53E3E;-fx-text-fill:white;-fx-font-size:14px;-fx-font-weight:bold;-fx-padding:12 30;-fx-background-radius:10;-fx-cursor:hand;");
                Button btnClose = new Button("Fermer"); btnClose.setStyle("-fx-background-color:#E2E8F0;-fx-text-fill:#4A5568;-fx-font-size:12px;-fx-padding:8 20;-fx-background-radius:8;-fx-cursor:hand;"); btnClose.setVisible(false);
                btnClose.setOnAction(ev -> dialogStage.close());
                javafx.event.EventHandler<javafx.event.ActionEvent> check = ev -> {
                    String chosen = (ev.getSource() == btnVrai) ? "Vrai" : "Faux";
                    boolean norm = "Vrai".equals(correctAnswer);
                    boolean correct = chosen.equals(correctAnswer);
                    lblResult.setText(correct ? "🎉 Bonne réponse ! " + (norm ? "C'est VRAI." : "C'est FAUX.") : "❌ Mauvaise réponse. C'était : " + (norm ? "VRAI" : "FAUX"));
                    lblResult.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:" + (correct ? "#00B894" : "#E53E3E") + ";");
                    btnVrai.setDisable(true); btnFaux.setDisable(true); btnClose.setVisible(true);
                };
                btnVrai.setOnAction(check); btnFaux.setOnAction(check);
                buttons.getChildren().addAll(btnVrai, btnFaux);
                root.getChildren().addAll(lblTitle, lblQ, buttons, lblResult, btnClose);
                dialogStage.setScene(new javafx.scene.Scene(root)); dialogStage.show();
            });
        }, "trivia-thread").start();
    }

    // ════════════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ════════════════════════════════════════════════════════════════════

    @FXML void showDashboard(MouseEvent event) {
        setActiveNav(navDashboard, indicDashboard);
        contentArea.getChildren().setAll(dashboardPane);
        refreshStats(null);
    }

    @FXML void showUtilisateurs(MouseEvent event) {
        try {
            setActiveNav(navUtilisateurs, indicUtilisateurs);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GestionUtilisateurs.fxml"));
            VBox page = loader.load();
            ((GestionUtilisateurs) loader.getController()).setCurrentUser(currentUser);
            contentArea.getChildren().setAll(page);
        } catch (IOException e) { e.printStackTrace(); LightDialog.showError("Erreur", "Chargement Utilisateurs impossible."); }
    }

    @FXML void showFormations(MouseEvent event) {
        try {
            setActiveNav(navFormations, indicFormations);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/FormationView.fxml"));
            Parent page = loader.load();
            contentArea.getChildren().setAll(page);
        } catch (IOException e) { e.printStackTrace(); LightDialog.showError("Erreur", "Chargement Formations impossible."); }
    }

    @FXML void showProfil(MouseEvent event) {
        try {
            setActiveNav(navProfil, indicProfil);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Profil.fxml"));
            javafx.scene.control.ScrollPane page = loader.load();
            Profil ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser);
            ctrl.setOnPhotoChanged(this::refreshUserData);
            ctrl.setOnBackToAccueil(this::showDashboardFromProfil);
            contentArea.getChildren().setAll(page);
        } catch (IOException e) { e.printStackTrace(); LightDialog.showError("Erreur", "Chargement Profil impossible."); }
    }

    // ════════════════════════════════════════════════════════════════════
    //  HOVER HANDLERS
    // ════════════════════════════════════════════════════════════════════

    @FXML void onNavDashboardEnter(MouseEvent e)  { if (navDashboard    != currentActiveNav) navDashboard.setStyle(NAV_ACTIVE); }
    @FXML void onNavDashboardExit(MouseEvent e)   { if (navDashboard    != currentActiveNav) navDashboard.setStyle(NAV_NORMAL); }
    @FXML void onNavUtilsEnter(MouseEvent e)      { if (navUtilisateurs != currentActiveNav) navUtilisateurs.setStyle(NAV_ACTIVE); }
    @FXML void onNavUtilsExit(MouseEvent e)       { if (navUtilisateurs != currentActiveNav) navUtilisateurs.setStyle(NAV_NORMAL); }
    @FXML void onNavFormationsEnter(MouseEvent e) { if (navFormations   != currentActiveNav) navFormations.setStyle(NAV_ACTIVE); }
    @FXML void onNavFormationsExit(MouseEvent e)  { if (navFormations   != currentActiveNav) navFormations.setStyle(NAV_NORMAL); }
    @FXML void onNavProfilEnter(MouseEvent e)     { if (navProfil       != currentActiveNav) navProfil.setStyle(NAV_ACTIVE); }
    @FXML void onNavProfilExit(MouseEvent e)      { if (navProfil       != currentActiveNav) navProfil.setStyle(NAV_NORMAL); }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════

    private void showDashboardFromProfil() {
        setActiveNav(navDashboard, indicDashboard);
        contentArea.getChildren().setAll(dashboardPane);
        refreshStats(null);
    }

    private void setActiveNav(VBox nav, HBox indic) {
        VBox[] navs   = {navDashboard, navUtilisateurs, navFormations, navProfil};
        HBox[] indics = {indicDashboard, indicUtilisateurs, indicFormations, indicProfil};
        for (VBox n : navs)   if (n != null) n.setStyle(NAV_NORMAL);
        for (HBox i : indics) if (i != null) i.setStyle(INDIC_HIDDEN);
        if (nav   != null) nav.setStyle(NAV_ACTIVE);
        if (indic != null) indic.setStyle(INDIC_VISIBLE);
        currentActiveNav = nav;
    }

    private String extractStringField(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf("\"" + key + "\""); if (idx == -1) return null;
        idx += key.length() + 2;
        while (idx < json.length() && (json.charAt(idx) == ' ' || json.charAt(idx) == ':')) idx++;
        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        idx++;
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx);
            if (c == '\\') { idx++; if (idx >= json.length()) break; char e = json.charAt(idx); switch(e){case '"':sb.append('"');break;case '\\':sb.append('\\');break;case 'n':sb.append('\n');break;default:sb.append(e);} }
            else if (c == '"') break;
            else sb.append(c);
            idx++;
        }
        return sb.toString();
    }

    private String jsonStr(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","").replace("\t"," ") + "\"";
    }

    @FXML void handleLogout(MouseEvent event) {
        if (LightDialog.showConfirmation("Déconnexion", "Voulez-vous vraiment quitter ?", "👋")) {
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/Login.fxml"));
                ((Stage) lblWelcome.getScene().getWindow()).setScene(new Scene(root));
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}