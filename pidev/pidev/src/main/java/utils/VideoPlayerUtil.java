package utils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * VideoPlayerUtil
 *
 * YouTube + local videos play INSIDE the app via VLCJ.
 * Uses Canvas + RV32 CallbackVideoSurface — fixes black screen on Apple Silicon M4.
 *
 * Falls back to thumbnail + browser button if VLC not available.
 */
public class VideoPlayerUtil {

    private static final String VLC_LIB     = "/Applications/VLC.app/Contents/MacOS/lib";
    private static final String VLC_PLUGINS = "/Applications/VLC.app/Contents/MacOS/plugins";
    private static Boolean vlcAvailable = null;

    static {
        System.setProperty("jna.library.path", VLC_LIB);
    }

    // ════════════════════════════════════════════════════════════════════
    //  YOUTUBE HELPERS
    // ════════════════════════════════════════════════════════════════════

    public static boolean isYouTubeUrl(String url) {
        if (url == null) return false;
        return url.contains("youtube.com") || url.contains("youtu.be");
    }

    public static String extractYouTubeId(String url) {
        if (url == null) return "";
        String id = "";
        try {
            if      (url.contains("v="))        id = url.split("v=")[1];
            else if (url.contains("youtu.be/")) id = url.split("youtu.be/")[1];
            else if (url.contains("embed/"))    id = url.split("embed/")[1];
            if (id.contains("&")) id = id.split("&")[0];
            if (id.contains("?")) id = id.split("\\?")[0];
        } catch (Exception e) { return ""; }
        return id.trim();
    }

    // ════════════════════════════════════════════════════════════════════
    //  VLC CHECK — direct, no reflection
    // ════════════════════════════════════════════════════════════════════

    private static boolean isVlcAvailable() {
        if (vlcAvailable != null) return vlcAvailable;
        try {
            File libFile = new File(VLC_LIB + "/libvlc.dylib");
            if (!libFile.exists()) {
                System.err.println("[VLC] libvlc.dylib not found at: " + VLC_LIB);
                return vlcAvailable = false;
            }
            // Quick factory test
            MediaPlayerFactory testFactory = new MediaPlayerFactory("--quiet");
            testFactory.release();
            System.out.println("[VLC] ✅ VLCJ ready");
            return vlcAvailable = true;
        } catch (Throwable t) {
            System.err.println("[VLC] Not available: " + t.getMessage());
            return vlcAvailable = false;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINTS
    // ════════════════════════════════════════════════════════════════════

    public static VBox createYouTubePlayer(String url) {
        if (isVlcAvailable()) {
            try { return createVlcPlayer(url); }
            catch (Throwable t) {
                System.err.println("[VLC] YouTube player error: " + t.getMessage());
            }
        }
        return createYouTubeFallback(url);
    }

    public static VBox createLocalPlayer(String filePath) {
        if (!new File(filePath).exists())
            return createErrorMessage("Fichier non trouvé", filePath);
        if (isVlcAvailable()) {
            try { return createVlcPlayer(filePath); }
            catch (Throwable t) {
                System.err.println("[VLC] Local player error: " + t.getMessage());
            }
        }
        return createJavaFxPlayer(filePath);
    }

    // ════════════════════════════════════════════════════════════════════
    //  VLCJ CANVAS PLAYER
    //
    //  KEY FIX for Apple Silicon M4 black screen:
    //  ImageViewVideoSurface is broken on arm64 — pixels never reach JavaFX.
    //  Solution: CallbackVideoSurface with RV32BufferFormat writes raw pixels
    //  directly into a WritableImage drawn on a Canvas. Works on all platforms.
    // ════════════════════════════════════════════════════════════════════

    private static VBox createVlcPlayer(String mediaUrl) {
        VBox container = new VBox(0);
        container.setStyle("-fx-background-color:#000;");

        // Canvas receives pixel data from VLC callback
        Canvas canvas = new Canvas(1280, 720);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(javafx.scene.paint.Color.BLACK);
        gc.fillRect(0, 0, 1280, 720);

        StackPane videoPane = new StackPane(canvas);
        videoPane.setStyle("-fx-background-color:#000;");
        videoPane.setPrefHeight(430);
        VBox.setVgrow(videoPane, Priority.ALWAYS);
        videoPane.widthProperty().addListener((obs, ov, nv) -> canvas.setWidth(nv.doubleValue()));
        videoPane.heightProperty().addListener((obs, ov, nv) -> canvas.setHeight(nv.doubleValue()));

        Label lblLoading = new Label("⏳ Chargement de la vidéo...");
        lblLoading.setStyle("-fx-text-fill:white;-fx-font-size:15px;"
                + "-fx-background-color:rgba(0,0,0,0.65);-fx-padding:10 22;-fx-background-radius:8;");
        videoPane.getChildren().add(lblLoading);

        // Controls
        Button btnPlay = new Button("⏸ Pause"); styleBtn(btnPlay, "#fdcb6e");
        Button btnStop = new Button("⏹ Stop");  styleBtn(btnStop, "#d63031");
        Button btnMute = new Button("🔊");
        btnMute.setStyle("-fx-background-color:#4A6FA5;-fx-text-fill:white;"
                + "-fx-cursor:hand;-fx-background-radius:8;-fx-padding:8 14;");
        Slider timeSlider = new Slider(0, 1, 0);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        Label lblTime = new Label("00:00 / 00:00");
        lblTime.setStyle("-fx-text-fill:white;-fx-font-size:12px;"); lblTime.setMinWidth(110);
        Label lblStatus = new Label("⏳ Chargement...");
        lblStatus.setStyle("-fx-text-fill:#fdcb6e;-fx-font-size:12px;");
        Slider volSlider = new Slider(0, 200, 100); volSlider.setPrefWidth(80);
        volSlider.setTooltip(new javafx.scene.control.Tooltip("Volume"));

        // VLCJ factory + player
        MediaPlayerFactory factory = new MediaPlayerFactory(
                "--no-video-title-show", "--quiet",
                "--plugin-path=" + VLC_PLUGINS);
        EmbeddedMediaPlayer player = factory.mediaPlayers().newEmbeddedMediaPlayer();

        // ── Canvas callback surface (fixes M4 black screen) ──────────────
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(
                new BufferFormatCallback() {
                    @Override
                    public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
                        Platform.runLater(() -> {
                            canvas.setWidth(sourceWidth);
                            canvas.setHeight(sourceHeight);
                            videoPane.getChildren().remove(lblLoading);
                        });
                        return new RV32BufferFormat(sourceWidth, sourceHeight);
                    }
                    @Override public void allocatedBuffers(ByteBuffer[] buffers) {}
                },
                new RenderCallback() {
                    @Override
                    public void display(uk.co.caprica.vlcj.player.base.MediaPlayer mp,
                                        ByteBuffer[] nativeBuffers,
                                        BufferFormat bufferFormat) {
                        int w = bufferFormat.getWidth();
                        int h = bufferFormat.getHeight();
                        ByteBuffer buf = nativeBuffers[0];
                        byte[] pixels = new byte[w * h * 4];
                        buf.rewind();
                        buf.get(pixels, 0, Math.min(pixels.length, buf.remaining()));
                        buf.rewind();
                        Platform.runLater(() -> {
                            WritableImage img = new WritableImage(w, h);
                            img.getPixelWriter().setPixels(0, 0, w, h,
                                    PixelFormat.getByteBgraInstance(), pixels, 0, w * 4);
                            double sx = canvas.getWidth() / w;
                            double sy = canvas.getHeight() / h;
                            double sc = Math.min(sx, sy);
                            double dw = w * sc, dh = h * sc;
                            double dx = (canvas.getWidth()  - dw) / 2;
                            double dy = (canvas.getHeight() - dh) / 2;
                            gc.setFill(javafx.scene.paint.Color.BLACK);
                            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                            gc.drawImage(img, dx, dy, dw, dh);
                        });
                    }
                },
                true
        ));

        // ── Button handlers ───────────────────────────────────────────────
        btnPlay.setOnAction(e -> {
            if (player.status().isPlaying()) {
                player.controls().pause();
                btnPlay.setText("▶ Lecture"); styleBtn(btnPlay, "#00b894");
                lblStatus.setText("⏸ En pause");
            } else {
                player.controls().play();
                btnPlay.setText("⏸ Pause"); styleBtn(btnPlay, "#fdcb6e");
                lblStatus.setText("▶ Lecture en cours");
            }
        });
        btnStop.setOnAction(e -> {
            player.controls().stop();
            btnPlay.setText("▶ Lecture"); styleBtn(btnPlay, "#00b894");
            lblStatus.setText("⏹ Arrêté"); timeSlider.setValue(0);
        });
        btnMute.setOnAction(e -> {
            boolean m = player.audio().isMute();
            player.audio().setMute(!m);
            btnMute.setText(m ? "🔊" : "🔇");
        });
        volSlider.valueProperty().addListener((obs, ov, nv) ->
                player.audio().setVolume(nv.intValue()));
        timeSlider.setOnMousePressed(e -> {
            long total = player.status().length();
            if (total > 0)
                player.controls().setTime((long)(timeSlider.getValue() * total));
        });

        // ── Events ────────────────────────────────────────────────────────
        player.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mp) {
                Platform.runLater(() -> {
                    videoPane.getChildren().remove(lblLoading);
                    lblStatus.setText("▶ Lecture en cours");
                    lblStatus.setStyle("-fx-text-fill:#00b894;-fx-font-size:12px;");
                    btnPlay.setText("⏸ Pause"); styleBtn(btnPlay, "#fdcb6e");
                });
            }
            @Override public void timeChanged(uk.co.caprica.vlcj.player.base.MediaPlayer mp, long newTime) {
                Platform.runLater(() -> {
                    long total = mp.status().length();
                    if (total > 0 && !timeSlider.isValueChanging()) {
                        timeSlider.setValue((double) newTime / total);
                        lblTime.setText(fmtMs(newTime) + " / " + fmtMs(total));
                    }
                });
            }
            @Override public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mp) {
                Platform.runLater(() -> {
                    btnPlay.setText("▶ Lecture"); styleBtn(btnPlay, "#00b894");
                    lblStatus.setText("✅ Terminé"); timeSlider.setValue(0);
                });
            }
            @Override public void error(uk.co.caprica.vlcj.player.base.MediaPlayer mp) {
                Platform.runLater(() -> {
                    lblStatus.setText("❌ Erreur lecture");
                    lblStatus.setStyle("-fx-text-fill:#d63031;-fx-font-size:12px;");
                });
            }
        });

        // ── Layout ────────────────────────────────────────────────────────
        Label lblVol = new Label("🔊"); lblVol.setStyle("-fx-text-fill:white;");
        HBox row1 = new HBox(10, btnPlay, btnStop, btnMute, lblStatus);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(8, 15, 4, 15));
        row1.setStyle("-fx-background-color:#1a1a2e;");
        HBox row2 = new HBox(10, timeSlider, lblTime, lblVol, volSlider);
        row2.setAlignment(Pos.CENTER);
        row2.setPadding(new Insets(4, 15, 10, 15));
        row2.setStyle("-fx-background-color:#1a1a2e;");

        container.getChildren().addAll(videoPane, row1, row2);
        container.setUserData(new Object[]{player, factory});

        // Play — VLC streams YouTube URLs natively
        boolean isYT = isYouTubeUrl(mediaUrl);
        String mrl = isYT ? mediaUrl : new File(mediaUrl).toURI().toString();
        player.media().play(mrl);

        return container;
    }

    // ════════════════════════════════════════════════════════════════════
    //  JAVAFX MEDIAPLAYER fallback for local files
    // ════════════════════════════════════════════════════════════════════

    private static VBox createJavaFxPlayer(String filePath) {
        VBox container = new VBox(0);
        container.setStyle("-fx-background-color:black;");
        try {
            Media media = new Media(new File(filePath).toURI().toString());
            MediaPlayer mp = new MediaPlayer(media);
            MediaView mv = new MediaView(mp); mv.setPreserveRatio(true);
            StackPane vp = new StackPane(mv);
            vp.setStyle("-fx-background-color:black;"); vp.setMinHeight(400); vp.setPrefHeight(400);
            VBox.setVgrow(vp, Priority.ALWAYS);
            vp.widthProperty().addListener((o,ov,nv) -> mv.setFitWidth(nv.doubleValue()));
            vp.heightProperty().addListener((o,ov,nv) -> mv.setFitHeight(nv.doubleValue()));
            Label ll = new Label("⏳"); ll.setStyle("-fx-text-fill:white;-fx-font-size:24px;");
            vp.getChildren().add(ll);
            Button bp = new Button("▶ Lecture"); styleBtn(bp,"#00b894"); bp.setDisable(true);
            Button bs = new Button("⏹ Stop");   styleBtn(bs,"#d63031"); bs.setDisable(true);
            Slider ts = new Slider(0,100,0); HBox.setHgrow(ts,Priority.ALWAYS); ts.setDisable(true);
            Label lt = new Label("00:00 / 00:00"); lt.setStyle("-fx-text-fill:white;-fx-font-size:12px;"); lt.setMinWidth(110);
            Slider vs = new Slider(0,1,0.8); vs.setPrefWidth(80);
            mp.setOnReady(() -> {
                vp.getChildren().remove(ll);
                bp.setDisable(false); bs.setDisable(false); ts.setDisable(false);
                ts.setMax(mp.getTotalDuration().toSeconds());
                lt.setText("00:00 / " + fmtDur(mp.getTotalDuration()));
                mp.play(); bp.setText("⏸ Pause"); styleBtn(bp,"#fdcb6e");
            });
            bp.setOnAction(e -> {
                if (mp.getStatus()==MediaPlayer.Status.PLAYING) { mp.pause(); bp.setText("▶ Lecture"); styleBtn(bp,"#00b894"); }
                else { mp.play(); bp.setText("⏸ Pause"); styleBtn(bp,"#fdcb6e"); }
            });
            bs.setOnAction(e -> { mp.stop(); bp.setText("▶ Lecture"); styleBtn(bp,"#00b894"); });
            mp.volumeProperty().bind(vs.valueProperty());
            mp.currentTimeProperty().addListener((obs,ov,nv) -> {
                Duration tot = mp.getTotalDuration();
                if (!ts.isPressed() && tot != null && !tot.isUnknown()) {
                    ts.setValue(nv.toSeconds()); lt.setText(fmtDur(nv) + " / " + fmtDur(tot));
                }
            });
            ts.setOnMousePressed(e -> mp.pause());
            ts.setOnMouseReleased(e -> { mp.seek(Duration.seconds(ts.getValue())); mp.play(); });
            mp.setOnEndOfMedia(() -> { mp.stop(); bp.setText("▶ Lecture"); styleBtn(bp,"#00b894"); });
            mp.setOnError(() -> Platform.runLater(() -> {
                container.getChildren().clear();
                container.getChildren().add(createWebViewLocalPlayer(filePath));
            }));
            Label lv = new Label("🔊"); lv.setStyle("-fx-text-fill:white;");
            HBox r1 = new HBox(10,bp,bs); r1.setAlignment(Pos.CENTER); r1.setPadding(new Insets(8,15,4,15)); r1.setStyle("-fx-background-color:#2d3436;");
            HBox r2 = new HBox(10,ts,lt,lv,vs); r2.setAlignment(Pos.CENTER); r2.setPadding(new Insets(4,15,8,15)); r2.setStyle("-fx-background-color:#2d3436;");
            container.getChildren().addAll(vp,r1,r2); container.setUserData(mp);
        } catch (Exception e) {
            container.getChildren().add(createWebViewLocalPlayer(filePath));
        }
        return container;
    }

    // ════════════════════════════════════════════════════════════════════
    //  YOUTUBE THUMBNAIL FALLBACK (when VLC not available)
    // ════════════════════════════════════════════════════════════════════

    private static VBox createYouTubeFallback(String url) {
        VBox container = new VBox(0);
        container.setStyle("-fx-background-color:#1a1a2e;");
        String videoId = extractYouTubeId(url);
        if (videoId.isEmpty()) return createErrorMessage("URL invalide", url);
        String watchUrl = "https://www.youtube.com/watch?v=" + videoId;

        ImageView img = new ImageView();
        img.setPreserveRatio(true); img.setFitWidth(680); img.setFitHeight(380);
        img.setStyle("-fx-opacity:0.85;");
        try {
            Image hi = new Image("https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg", true);
            hi.errorProperty().addListener((o, w, err) -> {
                if (err) img.setImage(new Image("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg", true));
            });
            img.setImage(hi);
        } catch (Exception ignored) {}

        VBox overlay = new VBox(8); overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color:rgba(0,0,0,0.45);"); overlay.setPrefSize(680, 380);
        Label icon = new Label("▶");
        icon.setStyle("-fx-font-size:64px;-fx-text-fill:white;-fx-background-color:rgba(255,0,0,0.85);"
                + "-fx-background-radius:50;-fx-padding:10 22;-fx-cursor:hand;");
        Label hint = new Label("Cliquez pour regarder sur YouTube");
        hint.setStyle("-fx-text-fill:rgba(255,255,255,0.85);-fx-font-size:13px;");
        overlay.getChildren().addAll(icon, hint);
        overlay.setOnMouseClicked(e -> openInBrowser(watchUrl));

        StackPane tp = new StackPane(img, overlay);
        tp.setPrefHeight(380); VBox.setVgrow(tp, Priority.ALWAYS);

        HBox bar = new HBox(12); bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(14, 20, 14, 20)); bar.setStyle("-fx-background-color:#111827;");
        HBox sp = new HBox(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label ytL  = new Label("▶"); ytL.setStyle("-fx-text-fill:#FF0000;-fx-font-size:18px;");
        Label infoL = new Label("VLC requis pour la lecture intégrée");
        infoL.setStyle("-fx-text-fill:#9CA3AF;-fx-font-size:12px;");
        Button btn = new Button("▶  Regarder");
        btn.setStyle("-fx-background-color:#FF0000;-fx-text-fill:white;-fx-font-size:14px;"
                + "-fx-font-weight:700;-fx-padding:10 28;-fx-background-radius:24;-fx-cursor:hand;");
        btn.setOnAction(e -> openInBrowser(watchUrl));
        bar.getChildren().addAll(ytL, infoL, sp, btn);

        container.getChildren().addAll(tp, bar);
        container.setUserData(null);
        return container;
    }

    // ════════════════════════════════════════════════════════════════════
    //  WEBVIEW LOCAL FALLBACK
    // ════════════════════════════════════════════════════════════════════

    public static VBox createWebViewLocalPlayer(String filePath) {
        VBox c = new VBox(); c.setStyle("-fx-background-color:black;");
        File f = new File(filePath);
        if (!f.exists()) return createErrorMessage("Fichier non trouvé", filePath);
        WebView wv = new WebView(); wv.setPrefHeight(450);
        wv.getEngine().setJavaScriptEnabled(true);
        wv.getEngine().loadContent("<!DOCTYPE html><html><head><style>*{margin:0;padding:0;}"
                + "body{background:#000;display:flex;justify-content:center;align-items:center;height:100vh;}"
                + "video{width:100%;height:100%;object-fit:contain;}</style></head><body>"
                + "<video controls autoplay><source src='" + f.toURI() + "'/></video></body></html>");
        VBox.setVgrow(wv, Priority.ALWAYS);
        c.getChildren().add(wv); c.setUserData(wv);
        return c;
    }

    // ════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ════════════════════════════════════════════════════════════════════

    public static VBox createNoVideoMessage() {
        VBox c = new VBox(10); c.setAlignment(Pos.CENTER); c.setPrefHeight(400);
        c.setStyle("-fx-background-color:#2d3436;-fx-background-radius:10;");
        Label i = new Label("🎬"); i.setStyle("-fx-font-size:60px;");
        Label m = new Label("Aucune vidéo"); m.setStyle("-fx-text-fill:white;-fx-font-size:20px;-fx-font-weight:bold;");
        Label s = new Label("Ajoutez une URL YouTube ou un fichier MP4"); s.setStyle("-fx-text-fill:#b2bec3;");
        c.getChildren().addAll(i, m, s); return c;
    }

    public static VBox createErrorMessage(String title, String detail) {
        VBox c = new VBox(10); c.setAlignment(Pos.CENTER); c.setPrefHeight(400);
        c.setStyle("-fx-background-color:#2d3436;-fx-background-radius:10;");
        Label i = new Label("❌"); i.setStyle("-fx-font-size:50px;");
        Label m = new Label(title); m.setStyle("-fx-text-fill:#d63031;-fx-font-size:18px;-fx-font-weight:bold;");
        Label s = new Label(detail); s.setStyle("-fx-text-fill:#b2bec3;-fx-font-size:12px;"); s.setWrapText(true);
        c.getChildren().addAll(i, m, s); return c;
    }

    public static void openInBrowser(String url) {
        try { java.awt.Desktop.getDesktop().browse(new java.net.URI(url)); }
        catch (Exception e) {
            try { Runtime.getRuntime().exec(new String[]{"open", url}); }
            catch (Exception ex) { System.err.println("Cannot open browser: " + ex.getMessage()); }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  STOP / DISPOSE
    // ════════════════════════════════════════════════════════════════════

    public static void stopMedia(VBox container) {
        if (container == null) return;
        Object data = container.getUserData();
        if (data instanceof Object[] arr) {
            try { if (arr[0] instanceof EmbeddedMediaPlayer p) { p.controls().stop(); p.release(); } }
            catch (Exception ignored) {}
            try { if (arr[1] instanceof MediaPlayerFactory f) { f.release(); } }
            catch (Exception ignored) {}
        } else if (data instanceof MediaPlayer mp) {
            try { mp.stop(); mp.dispose(); } catch (Exception ignored) {}
        } else if (data instanceof WebView wv) {
            try { wv.getEngine().loadContent(""); } catch (Exception ignored) {}
        }
        container.getChildren().forEach(n -> { if (n instanceof VBox vb) stopMedia(vb); });
    }

    // ════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════

    private static void styleBtn(Button b, String color) {
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;"
                + "-fx-font-size:13px;-fx-cursor:hand;-fx-background-radius:8;-fx-padding:8 18;");
    }
    private static String fmtMs(long ms)     { int t=(int)(ms/1000); return String.format("%02d:%02d",t/60,t%60); }
    private static String fmtDur(Duration d) { if(d==null||d.isUnknown()) return "00:00"; int t=(int)d.toSeconds(); return String.format("%02d:%02d",t/60,t%60); }
}