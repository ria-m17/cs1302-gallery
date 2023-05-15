package cs1302.gallery;

import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.TilePane;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Random;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.io.IOException;

/**
 * Creates an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                         // enable nice output when printing
        .create();                                   // builts and returns a Gson object

    private Stage stage;
    private Scene scene;
    private VBox root;
    private Button play;
    private Label label;
    private Label search;
    private Label edgeLabel;
    private HBox toolBar;
    private ToolBar options;
    private HBox imageArray;
    private HBox bottomEdge;
    private TextField enterText;
    private ComboBox<String> dropDown;
    private Button getImages;
    private ImageView[] iv = new ImageView[20];
    private Image defImg;
    private String[] urls;
    private Image[] tempImgArr;
    private TilePane tile;
    private ProgressBar progressBar;
    private double status = 0.0;
    private double playCnt = -1.0;
    private boolean playValueCond = true;
    private Separator separator;
    private ArrayList<String> distinctUrls;
    private Timeline timeline;
    private Thread task;
    private String uri = "";

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new VBox(2);
        this.toolBar = new HBox();
        this.imageArray = new HBox();
        this.bottomEdge = new HBox(25);
        this.enterText = new TextField("The Weeknd");
        this.play = new Button("Play");
        this.label = new Label("Type in a term, select a media type, then click the button.");
        this.search = new Label("Search:");
        this.edgeLabel = new Label("Images provided by iTunes Search API.");
        this.defImg = new Image("file:resources/default.png");
        this.getImages = new Button("Get Images");
        this.separator = new Separator(Orientation.VERTICAL);
    } // GalleryApp

    @Override
    /** {@inheritDoc} */
    public void init() {
        // feel free to modify this method
        System.out.println("init() called");
        root.getChildren().addAll(toolBar, label, imageArray, bottomEdge);

        /*MAKES THE TOOLBAR*/
        // adds the options to the toolbar
        this.options = new ToolBar(
            play,
            separator,
            search,
            enterText,
            modifyDropDown(),
            getImages
            );
        play.setOnAction(e -> {
            confirmPlay();
        });
        // create a new thread which runs the getImages method.
        getImages.setOnAction(e -> {
            task = new Thread(() -> {
                loadApp();
            });
            task.setDaemon(true);
            task.start();
        });
        /*END*/

        /*MAKES THE TILEPANE*/
        this.tile = new TilePane();
        tile.setPrefRows(4);
        tile.setPrefColumns(5);

        for (int i = 0; i < iv.length; i++) {
            iv[i] = new ImageView(defImg);
            iv[i].setFitHeight(100.0);
            iv[i].setFitWidth(100.0);
            tile.getChildren().addAll(iv[i]);
        }
        /*END*/

        imageArray.getChildren().addAll(tile);
        bottomEdge.getChildren().addAll(modifyProgressBar());
        toolBar.getChildren().addAll(this.options);
        progressBar.setPrefWidth(245);
        bottomEdge.setAlignment(Pos.BASELINE_CENTER);
        imageArray.setAlignment(Pos.BASELINE_CENTER);
        toolBar.setAlignment(Pos.BASELINE_CENTER);
        play.setDisable(true);

    } // init

    @Override
    /** {@inheritDoc} */
    public void start(Stage stage) {
        this.stage = stage;
        this.scene = new Scene(this.root,538,480);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start

    @Override
    /** {@inheritDoc} */
    public void stop() {
        // feel free to modify this method
        System.out.println("stop() called");
        System.exit(0);
    } // stop

    /**
     * Adds and returns a combo box for the drop down.
     * @return ComboBox the combo box to be created.
     */
    private ComboBox modifyDropDown() {
        this.dropDown = new ComboBox<>();
        dropDown.setValue("music");
        dropDown.getItems().addAll(
            "movie",
            "podcast",
            "music",
            "musicVideo",
            "audiobook",
            "shortfilm",
            "tvShow",
            "software",
            "ebook",
            "all");
        return dropDown;
    } // modifyDropDown

    /**
     * Obtains the images based on the query.
     */
    private void loadApp() {
        String itunesLink = "https://itunes.apple.com/search";
        status = 0.0;
        progressBar.setProgress(status);
        Platform.runLater(() -> {
            label.setText("Getting Images...");
            play.setText("Play");
            getImages.setDisable(true);
            play.setDisable(true);
            timeline.pause();
        });
        try {
            String term = URLEncoder.encode(parseEntry(enterText), StandardCharsets.UTF_8);
            String media = URLEncoder.encode(getdropDownValue(), StandardCharsets.UTF_8);
            String limit = URLEncoder.encode("200", StandardCharsets.UTF_8);
            String query = String.format("?term=%s&media=%s&limit=%s", term, media, limit);
            this.uri = itunesLink + query;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .build();
            HttpResponse<String> response = HTTP_CLIENT
                .send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(response.toString());
            } // if
            String jsonString = response.body();
            jsonString.trim();
            ItunesResponse itunesResponse = GSON.fromJson(jsonString, ItunesResponse.class);
            // forms the urls array and prints the quantity and urls implemented
            urls = extractArtworkUrls(itunesResponse);
            addUrls();
            responseNumError(addUrls());
            System.out.println(uri);
            updateUrls();
            System.out.println("Total # of urls found " + urls.length);
            System.out.println("# of distinct urls found " + distinctUrls.size());
            modifyTiles(uri);
            getImages.setDisable(false);
            play.setDisable(false);
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            getImages.setDisable(false);
            play.setDisable(false);
            if (iv[0].getImage().equals(defImg)) {
                play.setDisable(true);
            } // if
            // forms the exception error box
            Platform.runLater(() -> {
                label.setText("Last attempt to get images failed...");
                Alert alert = new Alert(Alert.AlertType.ERROR,
                    "URI: " + uri +  " \nException: " + e.toString(),
                    ButtonType.OK);
                alert.showAndWait();
                play.setText("Play");
            });
        } // try
    } // loadApp

    /**
     * Parses the entry based off the Textfield response.
     * @param textfield the textfield to use.
     * @return String the parsed user entries.
     */
    private String parseEntry(TextField textfield) {
        String entry = textfield.getText();
        String[] words = entry.split(" ");
        String userEntry = words[0];
        for (int r = 1; r < words.length; r++) {
            userEntry += "+" + words[r];
        }
        return userEntry;
    } // parseEntry

    /**
     * Returns the current value.
     * @return String the user's selection.
     */
    private String getdropDownValue() {
        String value = dropDown.getValue();
        return value;
    } // getdropDownValue

    /**
     * Adds the progress bar and the bottom label.
     * @return HBox the bottom HBox with the elements.
     */
    private HBox modifyProgressBar() {
        HBox bar = new HBox();
        this.progressBar = new ProgressBar();
        bar.getChildren().addAll(progressBar, edgeLabel);
        progressBar.setProgress(status);
        return bar;
    } // modifyProgressBar

    /**
     * Increments the progress by 0.05.
     */
    private void growProgressBar() {
        status += 0.05;
        progressBar.setProgress(status);
    } // growProgressBar

    /**
     * Updates the tiles in the app with the new images.
     * @param uri the uri to set text to.
     */
    private void modifyTiles(String uri) {
        int i = 0;
        while (i < iv.length) {
            iv[i].setImage(tempImgArr[i]);
            iv[i].setFitWidth(100.0);
            iv[i].setFitHeight(100.0);
            i++;
        }
        Platform.runLater(() -> {
            label.setText(uri);
        });

        play.setDisable(false);

    } // modifyTiles

    /**
     * Adds distinct urls to the arraylist.
     * @return int the number of distinct urls.
     */

    private int addUrls() {
        distinctUrls = new ArrayList<>();
        for (String s : urls) {
            boolean isDistinct = true;
            for (String distinctUrl : distinctUrls) {
                if (s.equals(distinctUrl)) {
                    isDistinct = false;
                    break;
                } //if
            } //for
            if (isDistinct) {
                distinctUrls.add(s);
            } //if
        } //for
        return distinctUrls.size();
    } // addUrls

    /**
     * Adds all the distinct urls to an imagearray with images.
     *
     */
    private void updateUrls() {
        tempImgArr = new Image[iv.length];
        for (int r = 0; r < iv.length; r++) {
            // parses through and adds the first 20 distinct urls.
            tempImgArr[r] = new Image(distinctUrls.get(r));
            System.out.println(distinctUrls.get(r));
            growProgressBar();
        } // for
    } // updateUrls

/**
 * Extracts the artwork URLs from an iTunes response.
 * @param itunesResponse the iTunes response to extract URLs from.
 * @return an array of artwork URLs, or an empty array if no results were found.
 */
    private String[] extractArtworkUrls(ItunesResponse itunesResponse) {
        int numResults = itunesResponse.resultCount;
        if (numResults > 0) {
            String[] artworkUrls = new String[numResults];
            for (int r = 0; r < numResults; r++) {
                ItunesResult result = itunesResponse.results[r];
                artworkUrls[r] = result.artworkUrl100;
            } // for
            return artworkUrls;
        } else {
            return new String[0];
        } // else
    } //extractArtworkUrls

    /**
     * Checks if the amount of responses is less than 21.
     * @param response the number to check.
     * @throws IllegalArgumentException
     */
    private void responseNumError(int response) throws IllegalArgumentException {
        if (response < 21) {
            throw new IllegalArgumentException(addUrls() +
            " distinct results found, but 21 or more are needed.");
        } // if
    } // responseNumError

    /**
     * Sets the timeline and period while  randomizing the images.
     * @param handler the eventhandler of type ActionEvent to use.
     *
     */
    private void setTimeline(EventHandler<ActionEvent> handler) {
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(2), handler);
        timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.getKeyFrames().add(keyFrame);
        timeline.play();
    } // setTimeline

    /**
     * Jumbles the image in a random tile.
     */
    private void imageJumble() {
        EventHandler<ActionEvent> play = (e -> {
            Random jumble = new Random();
            // Picks a random tile to change.
            int jumbleTile = jumble.nextInt(20);
            int tileDisplay = jumbleTile + 1;
            // Picks a random URL from 21, to the end of the # of distinct URLs.
            int jumbleUrl = jumble.nextInt(21, distinctUrls.size());
            for (int r = 0; r < iv.length; r++) {
                if (r == jumbleTile) {
                    iv[r].setImage(new Image(distinctUrls.get(jumbleUrl)));
                } //if
            } //for
            System.out.println("url: " + distinctUrls.get(jumbleUrl));
            System.out.println("edited tile: " + tileDisplay);
        });
        setTimeline(play);
    } // imageJumble

    /**
     * Confirms if it should play/randomize.
     */
    private void confirmPlay() {
        playCnt++;
        if (playCnt % 2 == 0.0) {
            playValueCond = true;
        } // if
        if (playCnt % 2 != 0.0) {
            playValueCond = false;
        } // if
        Platform.runLater(() -> {
            if (playValueCond) {
                play.setText("Pause");
                timeline.play();
            } // if
            if (!playValueCond) {
                play.setText("Play");
                timeline.pause();
            } // if
        });
        if (playValueCond) {
            imageJumble();
        } // if
    } // confirmPlay

} // GalleryApp
