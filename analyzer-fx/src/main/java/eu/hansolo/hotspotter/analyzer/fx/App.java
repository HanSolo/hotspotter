package eu.hansolo.hotspotter.analyzer.fx;

import eu.hansolo.hotspotter.analyzer.core.Analyzer;
import eu.hansolo.hotspotter.analyzer.core.Grouping;
import eu.hansolo.hotspotter.analyzer.core.Query;
import eu.hansolo.hotspotter.analyzer.core.Result;
import eu.hansolo.hotspotter.analyzer.core.Row;
import eu.hansolo.hotspotter.analyzer.core.Snapshot;
import eu.hansolo.hotspotter.analyzer.core.SnapshotStore;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * JavaFX front end of the analyzer: sortable table, bar chart, snapshot range selection and a live mode that follows a running application.
 * Start it with an optional path to the agent output directory (or a run directory) as first argument.
 */
public class App extends Application {
    private static final int               MAX_CACHED_SNAPSHOTS = 6;
    private static final int               CHART_ROWS           = 15;
    private static final DateTimeFormatter TIME                 = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Map<Path, Snapshot>      cache         = Collections.synchronizedMap(new LinkedHashMap<Path, Snapshot>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(final Map.Entry<Path, Snapshot> eldest) { return size() > MAX_CACHED_SNAPSHOTS; }
    });
    private final AtomicInteger            requestId     = new AtomicInteger();
    private final List<String>             rangeLabels   = new ArrayList<>();
    private final Label                    pathLabel     = new Label("No folder opened");
    private final Label                    summaryLabel  = new Label("Open the output folder of the agent (hotspotter-out) to start.");
    private final ComboBox<Grouping>       groupingBox   = new ComboBox<>(FXCollections.observableArrayList(Grouping.values()));
    private final Spinner<Integer>         depthSpinner  = new Spinner<>(1, 10, 2);
    private final ComboBox<String>         limitBox      = new ComboBox<>(FXCollections.observableArrayList("10", "25", "50", "100", "All"));
    private final TextField                filterField   = new TextField();
    private final TextField                excludeField  = new TextField();
    private final ComboBox<Integer>        fromBox       = new ComboBox<>();
    private final ComboBox<Integer>        toBox         = new ComboBox<>();
    private final ToggleButton             liveToggle    = new ToggleButton("Live");
    private final Button                   exportButton  = new Button("Export CSV…");
    private final TableView<Row>           table         = new TableView<>();
    private final TableColumn<Row, Number> rateColumn    = numberColumn("Calls/s", Row::callsPerSecond, value -> value.doubleValue() < 0 ? "" : String.format("%,.0f", value.doubleValue()));
    private final CategoryAxis             categoryAxis  = new CategoryAxis();
    private final NumberAxis               numberAxis    = new NumberAxis();
    private final BarChart<Number, String> chart         = new BarChart<>(numberAxis, categoryAxis);
    private final PauseTransition          debounce      = new PauseTransition(Duration.millis(300));
    private final Timeline                 liveTimer     = new Timeline(new KeyFrame(Duration.seconds(2), event -> rescan()));
    private       Stage                    stage;
    private       Path                     root;
    private       List<Path>               files         = List.of();
    private       Result                   lastResult;
    private       boolean                  updating;


    @Override public void start(final Stage stage) {
        this.stage = stage;
        configureControls();

        BorderPane pane = new BorderPane();
        pane.setTop(new VBox(buildFileBar(), buildQueryBar(), buildRangeBar()));
        pane.setCenter(buildCenter());
        HBox summaryBar = new HBox(summaryLabel);
        summaryBar.setPadding(new Insets(6, 10, 6, 10));
        pane.setBottom(summaryBar);

        stage.setTitle("Hotspotter");
        stage.setScene(new Scene(pane, 1200, 760));
        stage.show();

        List<String> parameters = getParameters().getRaw();
        if (!parameters.isEmpty()) { open(Paths.get(parameters.get(0))); }
    }

    @Override public void stop() { liveTimer.stop(); }


    // ******************** Layout ********************
    private HBox buildFileBar() {
        Button openButton = new Button("Open folder…");
        openButton.setOnAction(event -> chooseFolder());
        exportButton.setOnAction(event -> exportCsv());
        exportButton.setDisable(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, openButton, pathLabel, spacer, liveToggle, exportButton);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 4, 10));
        return bar;
    }

    private HBox buildQueryBar() {
        filterField.setPromptText("class filter (regex)");
        excludeField.setPromptText("exclude classes (regex)");
        HBox bar = new HBox(8, new Label("Group by"), groupingBox, new Label("Depth"), depthSpinner, new Label("Show"), limitBox, filterField, excludeField);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 10, 4, 10));
        HBox.setHgrow(filterField, Priority.ALWAYS);
        HBox.setHgrow(excludeField, Priority.ALWAYS);
        return bar;
    }

    private HBox buildRangeBar() {
        HBox bar = new HBox(8, new Label("Compare snapshot"), fromBox, new Label("with"), toBox, new Label("(\"start\" = cumulative since the agent started, Live = last interval)"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 10, 8, 10));
        return bar;
    }

    private SplitPane buildCenter() {
        table.setPlaceholder(new Label("Nothing to show"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(rankColumn());
        table.getColumns().add(nameColumn());
        table.getColumns().add(numberColumn("Calls", Row::calls, value -> String.format("%,d", value.longValue())));
        table.getColumns().add(numberColumn("%", Row::percent, value -> String.format("%.2f %%", value.doubleValue())));
        table.getColumns().add(rateColumn);

        numberAxis.setLabel("calls");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setMinWidth(320);
        chart.setTitle("Top " + CHART_ROWS);

        SplitPane split = new SplitPane(table, chart);
        split.setDividerPositions(0.62);
        return split;
    }

    private TableColumn<Row, Void> rankColumn() {
        TableColumn<Row, Void> column = new TableColumn<>("#");
        column.setSortable(false);
        column.setPrefWidth(46);
        column.setCellFactory(col -> new TableCell<Row, Void>() {
            @Override protected void updateItem(final Void item, final boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        return column;
    }

    private TableColumn<Row, String> nameColumn() {
        TableColumn<Row, String> column = new TableColumn<>("Name");
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        column.setPrefWidth(520);
        return column;
    }

    private static TableColumn<Row, Number> numberColumn(final String title, final Function<Row, Number> getter, final Function<Number, String> format) {
        TableColumn<Row, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<Number>(getter.apply(cell.getValue())));
        column.setCellFactory(col -> new TableCell<Row, Number>() {
            @Override protected void updateItem(final Number item, final boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        column.setPrefWidth(120);
        return column;
    }


    // ******************** Behaviour ********************
    private void configureControls() {
        groupingBox.setValue(Grouping.METHOD);
        limitBox.setValue("25");
        depthSpinner.disableProperty().bind(groupingBox.valueProperty().isNotEqualTo(Grouping.PACKAGE));
        fromBox.disableProperty().bind(liveToggle.selectedProperty());
        toBox.disableProperty().bind(liveToggle.selectedProperty());
        rateColumn.setVisible(false);

        StringConverter<Integer> converter = new StringConverter<Integer>() {
            @Override public String toString(final Integer index) {
                if (index == null) { return ""; }
                if (index < 0) { return "start (cumulative)"; }
                return index < rangeLabels.size() ? rangeLabels.get(index) : "#" + index;
            }

            @Override public Integer fromString(final String text) { return null; }
        };
        fromBox.setConverter(converter);
        toBox.setConverter(converter);

        debounce.setOnFinished(event -> refresh());
        liveTimer.setCycleCount(Animation.INDEFINITE);

        groupingBox.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        depthSpinner.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        limitBox.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        fromBox.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        toBox.valueProperty().addListener((observable, oldValue, newValue) -> refresh());
        filterField.textProperty().addListener((observable, oldValue, newValue) -> debounce.playFromStart());
        excludeField.textProperty().addListener((observable, oldValue, newValue) -> debounce.playFromStart());
        liveToggle.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) {
                liveTimer.play();
                rescan();
            } else {
                liveTimer.stop();
            }
        });
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select the agent output folder (or a run-* folder)");
        File dir = chooser.showDialog(stage);
        if (dir != null) { open(dir.toPath()); }
    }

    private void open(final Path path) {
        root = path;
        pathLabel.setText(path.toAbsolutePath().toString());
        cache.clear();
        try {
            files = SnapshotStore.findSnapshots(path);
        } catch (IOException e) {
            files = List.of();
            summaryLabel.setText("Cannot read " + path + ": " + e.getMessage());
            return;
        }
        if (files.isEmpty()) {
            summaryLabel.setText("No snapshot-*.csv files found in " + path);
            return;
        }
        populateRange();
        refresh();
    }

    /** Called by the live timer: picks up new snapshots (and new runs when the base folder was opened). */
    private void rescan() {
        if (root == null) { return; }
        try {
            List<Path> found = SnapshotStore.findSnapshots(root);
            if (found.isEmpty() || found.equals(files)) { return; }
            files = found;
            populateRange();
            refresh();
        } catch (IOException e) {
            summaryLabel.setText("Cannot read " + root + ": " + e.getMessage());
        }
    }

    private void populateRange() {
        updating = true;
        int last = files.size() - 1;
        rangeLabels.clear();
        for (int i = 0; i <= last; i++) {
            String time = "";
            try {
                time = "  " + TIME.format(Files.getLastModifiedTime(files.get(i)).toInstant());
            } catch (IOException ignored) {
                // label without time is fine
            }
            rangeLabels.add("#" + i + time);
        }
        List<Integer> fromItems = new ArrayList<>();
        List<Integer> toItems   = new ArrayList<>();
        fromItems.add(-1);
        for (int i = 0; i <= last; i++) {
            fromItems.add(i);
            toItems.add(i);
        }
        fromBox.getItems().setAll(fromItems);
        toBox.getItems().setAll(toItems);
        toBox.setValue(last);
        fromBox.setValue(liveToggle.isSelected() && last >= 1 ? last - 1 : -1);
        updating = false;
    }

    private void refresh() {
        if (updating || files.isEmpty() || toBox.getValue() == null) { return; }
        final int        to        = toBox.getValue();
        final int        from      = fromBox.getValue() == null ? -1 : fromBox.getValue();
        final List<Path> available = files;
        if (from >= to) {
            summaryLabel.setText("The first snapshot must be older than the second one.");
            return;
        }
        final Query query = buildQuery();
        final int   id    = requestId.incrementAndGet();
        Task<Result> task = new Task<Result>() {
            @Override protected Result call() throws Exception {
                Snapshot end   = snapshot(available.get(to));
                Snapshot start = from >= 0 ? snapshot(available.get(from)) : null;
                return Analyzer.analyze(end, start, query);
            }
        };
        task.setOnSucceeded(event -> {
            if (id == requestId.get()) { show(task.getValue()); }
        });
        task.setOnFailed(event -> summaryLabel.setText("Analysis failed: " + task.getException()));
        Thread thread = new Thread(task, "hotspotter-analyze");
        thread.setDaemon(true);
        thread.start();
    }

    private Snapshot snapshot(final Path file) throws IOException {
        Snapshot cached = cache.get(file);
        if (cached == null) {
            cached = SnapshotStore.load(file);
            cache.put(file, cached);
        }
        return cached;
    }

    private Query buildQuery() {
        String limitText = limitBox.getValue();
        int    limit     = limitText == null || limitText.equals("All") ? 0 : Integer.parseInt(limitText);
        return new Query(groupingBox.getValue(), depthSpinner.getValue(), parsePattern(filterField), parsePattern(excludeField), limit);
    }

    private static Pattern parsePattern(final TextField field) {
        String text = field.getText();
        field.setStyle("");
        if (text == null || text.isBlank()) { return null; }
        try {
            return Pattern.compile(text);
        } catch (PatternSyntaxException e) {
            field.setStyle("-fx-text-box-border: red; -fx-focus-color: red;");
            return null;
        }
    }

    private void show(final Result result) {
        lastResult = result;
        exportButton.setDisable(false);
        rateColumn.setVisible(result.hasRate());
        table.getItems().setAll(result.rows());
        updateChart(result);
        String range = result.hasRate() ? String.format("delta over %.1f s", result.seconds()) : "cumulative";
        summaryLabel.setText(String.format("%s   |   total calls: %,d   |   rows shown: %d   |   snapshots: %d", range, result.totalCalls(), result.rows().size(), files.size()));
    }

    private void updateChart(final Result result) {
        List<Row>    top   = result.rows().subList(0, Math.min(CHART_ROWS, result.rows().size()));
        List<String> names = new ArrayList<>();
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        // horizontal bar charts list categories bottom-up, so add the least called first
        for (int i = top.size() - 1; i >= 0; i--) {
            String name = (i + 1) + "  " + shorten(top.get(i).name());
            names.add(name);
            series.getData().add(new XYChart.Data<Number, String>(top.get(i).calls(), name));
        }
        chart.getData().clear();
        categoryAxis.setCategories(FXCollections.observableArrayList(names));
        chart.getData().add(series);
    }

    private static String shorten(final String name) { return name.length() <= 46 ? name : "…" + name.substring(name.length() - 45); }

    private void exportCsv() {
        if (lastResult == null) { return; }
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("hotspots.csv");
        File file = chooser.showSaveDialog(stage);
        if (file == null) { return; }
        try {
            Files.writeString(file.toPath(), Analyzer.toCsv(lastResult));
            summaryLabel.setText("Exported " + lastResult.rows().size() + " rows to " + file);
        } catch (IOException e) {
            summaryLabel.setText("Export failed: " + e.getMessage());
        }
    }
}
