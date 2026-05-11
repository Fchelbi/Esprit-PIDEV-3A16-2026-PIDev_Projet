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
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;

public class AdminHome {

    // ── User stat labels ──────────────────────────────────────────────────
    @FXML private Label lblWelcome, lblHeaderAvatar;
    @FXML private Label lblNbPatients, lblNbCoaches, lblNbAdmins, lblNbTotal;
    @FXML private Label lblPctPatients, lblPctCoaches, lblPctAdmins;
    @FXML private ProgressBar pbPatients, pbCoaches, pbAdmins;

    // ── Formation stat labels ─────────────────────────────────────────────
    @FXML private Label lblNbFormations, lblNbAvecVideo, lblNbAvecQuiz, lblNbCategories;

    // ── Charts ────────────────────────────────────────────────────────────
    @FXML private WebView wvUserChart;
    @FXML private WebView wvFormationChart;

    // ── Layout ────────────────────────────────────────────────────────────
    @FXML private StackPane contentArea;
    @FXML private ScrollPane dashboardPane;
    @FXML private ImageView imgHeaderPhoto;

    // ── Nav ───────────────────────────────────────────────────────────────
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

    private static final String NAV_NORMAL  = "-fx-padding:12 20 12 16;-fx-cursor:hand;-fx-background-color:transparent;-fx-background-radius:0 10 10 0;";
    private static final String NAV_ACTIVE  = "-fx-padding:12 20 12 16;-fx-cursor:hand;-fx-background-color:rgba(255,255,255,0.13);-fx-background-radius:0 10 10 0;";
    private static final String INDIC_HIDDEN  = "-fx-min-width:4;-fx-max-width:4;-fx-min-height:30;-fx-background-color:transparent;-fx-background-radius:0 2 2 0;";
    private static final String INDIC_VISIBLE = "-fx-min-width:4;-fx-max-width:4;-fx-min-height:30;-fx-background-color:#E8956D;-fx-background-radius:0 2 2 0;";

    // ════════════════════════════════════════════════════════════════════
    //  INIT
    // ════════════════════════════════════════════════════════════════════

    @FXML
    void initialize() {
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
        } catch (SQLException e) {
            System.err.println("refreshUserData: " + e.getMessage());
        }
        lblWelcome.setText(currentUser.getPrenom() + " " + currentUser.getNom());
        lblHeaderAvatar.setText(currentUser.getPrenom().substring(0, 1).toUpperCase());
        updateHeaderPhoto();
    }

    private void updateHeaderPhoto() {
        if (currentUser.getPhoto() != null && !currentUser.getPhoto().isEmpty()) {
            File f = new File(currentUser.getPhoto());
            if (f.exists()) {
                imgHeaderPhoto.setImage(new Image(f.toURI() + "?t=" + System.currentTimeMillis(), 40, 40, false, true));
                imgHeaderPhoto.setVisible(true);
                lblHeaderAvatar.setVisible(false);
                return;
            }
        }
        imgHeaderPhoto.setVisible(false);
        lblHeaderAvatar.setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════════
    //  STATS
    // ════════════════════════════════════════════════════════════════════

    @FXML
    void refreshStats(ActionEvent event) {
        loadUserStats();
        loadFormationStats();
    }

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
                double pp = p / (double) total;
                double pc = c / (double) total;
                double pa = a / (double) total;
                if (pbPatients    != null) pbPatients.setProgress(pp);
                if (pbCoaches     != null) pbCoaches.setProgress(pc);
                if (pbAdmins      != null) pbAdmins.setProgress(pa);
                if (lblPctPatients != null) lblPctPatients.setText(String.format("%.0f%%", pp * 100));
                if (lblPctCoaches  != null) lblPctCoaches.setText(String.format("%.0f%%",  pc * 100));
                if (lblPctAdmins   != null) lblPctAdmins.setText(String.format("%.0f%%",   pa * 100));
            }
            loadUserChart(p, c, a);
        } catch (SQLException e) {
            LightDialog.showError("Erreur", "Impossible de charger les stats utilisateurs.");
        }
    }

    private void loadFormationStats() {
        if (formationService == null) return;
        try {
            List<Formation> formations = formationService.selectALL();
            int total = formations.size();
            int avecVideo = 0, avecQuiz = 0;
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
            System.err.println("Formation stats error: " + e.getMessage());
            for (Label lbl : new Label[]{lblNbFormations, lblNbAvecVideo, lblNbAvecQuiz, lblNbCategories})
                if (lbl != null) lbl.setText("—");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  CHARTS
    //  - User chart: donut (Patients / Coaches / Admins)
    //  - Formation chart: donut (Avec vidéo / Sans vidéo / Avec quiz)
    // ════════════════════════════════════════════════════════════════════

    private void loadUserChart(int patients, int coaches, int admins) {
        if (wvUserChart == null) return;
        int total = patients + coaches + admins;
        if (total == 0) { wvUserChart.getEngine().loadContent("<html><body style='background:transparent;color:#718096;font-family:sans-serif;padding:20px'>Aucune donnée</body></html>"); return; }
        String html = """
        <!DOCTYPE html><html>
        <head>
        <script src="https://www.gstatic.com/charts/loader.js"></script>
        <script>
          google.charts.load('current', {packages: ['corechart']});
          google.charts.setOnLoadCallback(draw);
          function draw() {
            var data = google.visualization.arrayToDataTable([
              ['Rôle', 'Nombre'],
              ['Patients', %d],
              ['Coaches',  %d],
              ['Admins',   %d]
            ]);
            var options = {
              pieHole: 0.45,
              legend: { position: 'bottom', textStyle: { color: '#4A5568', fontSize: 12 } },
              backgroundColor: 'transparent',
              colors: ['#E8956D', '#4A6FA5', '#6C5CE7'],
              chartArea: { width: '90%%', height: '80%%' },
              pieSliceTextStyle: { color: 'white', fontSize: 12 }
            };
            new google.visualization.PieChart(document.getElementById('chart')).draw(data, options);
          }
        </script>
        </head>
        <body style="margin:0;background:transparent;">
          <div id="chart" style="width:100%%;height:270px;"></div>
        </body></html>
        """.formatted(patients, coaches, admins);
        wvUserChart.getEngine().loadContent(html);
    }

    /**
     * Formation donut chart: splits formations by category with enrollment counts.
     * Also shows "avec quiz" vs "sans quiz" breakdown in the legend.
     */
    private void loadFormationDonutChart(List<Formation> formations) {
        if (wvFormationChart == null) return;
        if (formations.isEmpty()) {
            wvFormationChart.getEngine().loadContent("<html><body style='background:transparent;color:#718096;font-family:sans-serif;padding:20px'>Aucune formation</body></html>");
            return;
        }

        // Group formations by category and count enrollments
        Map<String, Long> byCategory = new LinkedHashMap<>();
        try {
            List<Participant> allParts = participantService != null ? participantService.selectALL() : List.of();
            for (Formation f : formations) {
                String cat = f.getCategory() != null && !f.getCategory().isBlank() ? f.getCategory() : "Autre";
                long enrolled = allParts.stream().filter(p -> p.getFormationId() == f.getId()).count();
                byCategory.merge(cat, enrolled + 1, Long::sum); // +1 so empty categories still appear
            }
        } catch (Exception e) {
            for (Formation f : formations) {
                String cat = f.getCategory() != null && !f.getCategory().isBlank() ? f.getCategory() : "Autre";
                byCategory.merge(cat, 1L, Long::sum);
            }
        }

        StringBuilder rows = new StringBuilder();
        byCategory.forEach((cat, count) ->
                rows.append(String.format("['%s', %d],\n", cat.replace("'", "\\'"), count)));

        String html = """
        <!DOCTYPE html><html>
        <head>
        <script src="https://www.gstatic.com/charts/loader.js"></script>
        <script>
          google.charts.load('current', {packages: ['corechart']});
          google.charts.setOnLoadCallback(draw);
          function draw() {
            var data = google.visualization.arrayToDataTable([
              ['Catégorie', 'Inscriptions'],
              %s
            ]);
            var options = {
              pieHole: 0.4,
              legend: { position: 'right', textStyle: { color: '#4A5568', fontSize: 11 } },
              backgroundColor: 'transparent',
              colors: ['#E8956D','#4A6FA5','#6C5CE7','#00B894','#FDCB6E','#E17055','#74B9FF'],
              chartArea: { width: '85%%', height: '82%%' },
              pieSliceTextStyle: { color: 'white', fontSize: 11 },
              title: 'Inscriptions par catégorie',
              titleTextStyle: { color: '#2D3748', fontSize: 12, bold: true }
            };
            new google.visualization.PieChart(document.getElementById('chart')).draw(data, options);
          }
        </script>
        </head>
        <body style="margin:0;background:transparent;">
          <div id="chart" style="width:100%%;height:280px;"></div>
        </body></html>
        """.formatted(rows.toString());
        wvFormationChart.getEngine().loadContent(html);
    }

    // ════════════════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════════════════
    //  API 2 : NumbersAPI — Fun fact about total users count
    // ════════════════════════════════════════════════════════════════════

    // API 2 : Open Trivia DB — interactive Vrai/Faux quiz on communication & soft skills
    // Called after exportReport(). Player clicks Vrai or Faux and gets instant feedback.
    private void showTriviaFact() {
        // Embedded communication & soft skills questions (always available, no network needed)
        // Also tries Open Trivia DB online for variety
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
            String questionText;
            String correctAnswer;

            // Try Open Trivia DB first, fall back to local questions
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                HttpResponse<String> resp = client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("https://opentdb.com/api.php?amount=1&type=boolean&category=9"))
                                .timeout(Duration.ofSeconds(8)).header("User-Agent", "EchoCare/1.0").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                String q = extractQuestionFromTrivia(body);
                String ans = extractAnswerFromTrivia(body);
                if (!q.isBlank() && !ans.isBlank()) {
                    questionText = q;
                    correctAnswer = ans; // "True" or "False"
                } else { throw new Exception("empty"); }
            } catch (Exception e) {
                // Fall back to local communication questions
                int idx = (int)(System.currentTimeMillis() / 5000 % localQuestions.length);
                questionText = localQuestions[idx][0];
                correctAnswer = localQuestions[idx][1]; // "Vrai" or "Faux"
            }

            final String finalQ = questionText;
            final String finalA = correctAnswer;

            Platform.runLater(() -> {
                // Custom dialog with Vrai / Faux buttons
                javafx.stage.Stage dialogStage = new javafx.stage.Stage();
                dialogStage.setTitle("🧠 Fun & Learn");
                dialogStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

                javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(20);
                root.setStyle("-fx-padding:30;-fx-background-color:#F7FAFC;");
                root.setAlignment(javafx.geometry.Pos.CENTER);
                root.setPrefWidth(480);

                javafx.scene.control.Label lblTitle = new javafx.scene.control.Label("🧠 Fun & Learn — Le savais-tu ?");
                lblTitle.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#2D3748;");

                javafx.scene.control.Label lblQ = new javafx.scene.control.Label(finalQ);
                lblQ.setWrapText(true);
                lblQ.setStyle("-fx-font-size:14px;-fx-text-fill:#4A5568;-fx-font-style:italic;"
                        + "-fx-background-color:white;-fx-padding:16;-fx-background-radius:10;"
                        + "-fx-border-color:#E2E8F0;-fx-border-radius:10;");
                lblQ.setMaxWidth(420);

                javafx.scene.control.Label lblResult = new javafx.scene.control.Label("");
                lblResult.setWrapText(true);
                lblResult.setStyle("-fx-font-size:13px;-fx-font-weight:bold;");

                javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(16);
                buttons.setAlignment(javafx.geometry.Pos.CENTER);

                javafx.scene.control.Button btnVrai = new javafx.scene.control.Button("✅  VRAI");
                btnVrai.setStyle("-fx-background-color:#00B894;-fx-text-fill:white;-fx-font-size:14px;"
                        + "-fx-font-weight:bold;-fx-padding:12 30;-fx-background-radius:10;-fx-cursor:hand;");

                javafx.scene.control.Button btnFaux = new javafx.scene.control.Button("❌  FAUX");
                btnFaux.setStyle("-fx-background-color:#E53E3E;-fx-text-fill:white;-fx-font-size:14px;"
                        + "-fx-font-weight:bold;-fx-padding:12 30;-fx-background-radius:10;-fx-cursor:hand;");

                javafx.scene.control.Button btnClose = new javafx.scene.control.Button("Fermer");
                btnClose.setStyle("-fx-background-color:#E2E8F0;-fx-text-fill:#4A5568;-fx-font-size:12px;"
                        + "-fx-padding:8 20;-fx-background-radius:8;-fx-cursor:hand;");
                btnClose.setVisible(false);
                btnClose.setOnAction(ev -> dialogStage.close());

                javafx.event.EventHandler<javafx.event.ActionEvent> checkAnswer = ev -> {
                    String chosen = (ev.getSource() == btnVrai) ? "Vrai" : "Faux";
                    boolean norm = finalA.equalsIgnoreCase("vrai") || finalA.equalsIgnoreCase("true");
                    boolean correct = (chosen.equals("Vrai") && norm) || (chosen.equals("Faux") && !norm);
                    if (correct) {
                        lblResult.setText("🎉 Bonne réponse ! La réponse est : " + (norm ? "VRAI" : "FAUX"));
                        lblResult.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#00B894;");
                    } else {
                        lblResult.setText("❌ Mauvaise réponse. La bonne réponse était : " + (norm ? "VRAI" : "FAUX"));
                        lblResult.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#E53E3E;");
                    }
                    btnVrai.setDisable(true);
                    btnFaux.setDisable(true);
                    btnClose.setVisible(true);
                };

                btnVrai.setOnAction(checkAnswer);
                btnFaux.setOnAction(checkAnswer);
                buttons.getChildren().addAll(btnVrai, btnFaux);

                root.getChildren().addAll(lblTitle, lblQ, buttons, lblResult, btnClose);
                dialogStage.setScene(new javafx.scene.Scene(root));
                dialogStage.show();
            });
        }, "trivia-thread").start();
    }

    private String extractQuestionFromTrivia(String body) {
        try {
            int i = body.indexOf("\"question\":\"");
            if (i == -1) return "";
            i += 12;
            StringBuilder sb = new StringBuilder();
            while (i < body.length()) {
                char ch = body.charAt(i);
                if (ch == '"') break;
                if (ch == '\\' && i + 1 < body.length()) {
                    i++;
                    char nx = body.charAt(i);
                    if (nx == 'u' && i + 4 < body.length()) {
                        try { sb.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16)); i += 5; continue; }
                        catch (Exception ignored) {}
                    }
                    sb.append(nx);
                } else sb.append(ch);
                i++;
            }
            return sb.toString().replace("&quot;", "\"").replace("&#039;", "'")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        } catch (Exception e) { return ""; }
    }

    private String extractAnswerFromTrivia(String body) {
        try {
            int i = body.indexOf("\"correct_answer\":\"");
            if (i == -1) return "";
            i += 19;
            int end = body.indexOf("\"", i);
            return end == -1 ? "" : body.substring(i, end);
        } catch (Exception e) { return ""; }
    }

    // ════════════════════════════════════════════════════════════════════
    //  FONCTIONNALITÉ AVANCÉE : Export rapport statistiques texte
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
                    formations.size(),
                    nbResults, nbPassed, taux);

            String path = System.getProperty("user.home") + "/EchoCare_Rapport_" + java.time.LocalDate.now() + ".txt";
            java.nio.file.Files.writeString(java.nio.file.Path.of(path), report);

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("📄 Rapport exporté");
            a.setHeaderText("✅ Rapport enregistré avec succès !");
            a.setContentText("Votre rapport EchoCare a été sauvegardé dans votre dossier personnel.");
            ButtonType openBtn = new ButtonType("📂 Ouvrir");
            ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(openBtn, ok);
            a.showAndWait().ifPresent(b -> {
                if (b == openBtn) {
                    try { java.awt.Desktop.getDesktop().open(new java.io.File(path)); }
                    catch (Exception ignored) {}
                }
            });
            // Also show a fun fact about total user count
            showTriviaFact();

        } catch (Exception e) {
            LightDialog.showError("Erreur export", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ════════════════════════════════════════════════════════════════════

    @FXML
    void showDashboard(MouseEvent event) {
        setActiveNav(navDashboard, indicDashboard);
        contentArea.getChildren().setAll(dashboardPane);
        refreshStats(null);
    }

    @FXML
    void showUtilisateurs(MouseEvent event) {
        try {
            setActiveNav(navUtilisateurs, indicUtilisateurs);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GestionUtilisateurs.fxml"));
            VBox page = loader.load();
            ((GestionUtilisateurs) loader.getController()).setCurrentUser(currentUser);
            contentArea.getChildren().setAll(page);
        } catch (IOException e) {
            e.printStackTrace();
            LightDialog.showError("Erreur", "Chargement Utilisateurs impossible.");
        }
    }

    @FXML
    void showFormations(MouseEvent event) {
        try {
            setActiveNav(navFormations, indicFormations);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/FormationView.fxml"));
            Parent page = loader.load();
            contentArea.getChildren().setAll(page);
        } catch (IOException e) {
            e.printStackTrace();
            LightDialog.showError("Erreur", "Chargement Formations impossible.");
        }
    }

    @FXML
    void showProfil(MouseEvent event) {
        try {
            setActiveNav(navProfil, indicProfil);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Profil.fxml"));
            ScrollPane page = loader.load();
            Profil ctrl = loader.getController();
            ctrl.setCurrentUser(currentUser);
            ctrl.setOnPhotoChanged(this::refreshUserData);
            ctrl.setOnBackToAccueil(this::showDashboardFromProfil);
            contentArea.getChildren().setAll(page);
        } catch (IOException e) {
            e.printStackTrace();
            LightDialog.showError("Erreur", "Chargement Profil impossible.");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  HOVER HANDLERS
    // ════════════════════════════════════════════════════════════════════

    @FXML void onNavDashboardEnter(MouseEvent e)   { if (navDashboard    != currentActiveNav) navDashboard.setStyle(NAV_ACTIVE); }
    @FXML void onNavDashboardExit(MouseEvent e)    { if (navDashboard    != currentActiveNav) navDashboard.setStyle(NAV_NORMAL); }
    @FXML void onNavUtilsEnter(MouseEvent e)       { if (navUtilisateurs != currentActiveNav) navUtilisateurs.setStyle(NAV_ACTIVE); }
    @FXML void onNavUtilsExit(MouseEvent e)        { if (navUtilisateurs != currentActiveNav) navUtilisateurs.setStyle(NAV_NORMAL); }
    @FXML void onNavFormationsEnter(MouseEvent e)  { if (navFormations   != currentActiveNav) navFormations.setStyle(NAV_ACTIVE); }
    @FXML void onNavFormationsExit(MouseEvent e)   { if (navFormations   != currentActiveNav) navFormations.setStyle(NAV_NORMAL); }
    @FXML void onNavProfilEnter(MouseEvent e)      { if (navProfil       != currentActiveNav) navProfil.setStyle(NAV_ACTIVE); }
    @FXML void onNavProfilExit(MouseEvent e)       { if (navProfil       != currentActiveNav) navProfil.setStyle(NAV_NORMAL); }

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

    private String extractJsonValue(String json, String key) {
        String s = "\"" + key + "\":\"";
        int i = json.indexOf(s);
        if (i < 0) return "";
        i += s.length();
        int end = json.indexOf("\"", i);
        return end < 0 ? "" : json.substring(i, end)
                .replace("\\u2019", "'").replace("\\u2018", "'")
                .replace("\\u201c", "\"").replace("\\u201d", "\"")
                .replace("\\n", " ");
    }

    @FXML
    void handleLogout(MouseEvent event) {
        if (LightDialog.showConfirmation("Déconnexion", "Voulez-vous vraiment quitter ?", "👋")) {
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/Login.fxml"));
                ((Stage) lblWelcome.getScene().getWindow()).setScene(new Scene(root));
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
    @FXML
    void analyzeWithAI(MouseEvent event) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("🤖 Analyse IA");
        a.setHeaderText("Fonctionnalité IA");
        a.setContentText("Analyse IA disponible dans la version complète.");
        a.showAndWait();
    }


}