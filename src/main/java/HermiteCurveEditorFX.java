import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HermiteCurveEditorFX extends Application {

    private final Canvas canvas = new Canvas(900, 600);
    private final List<Point2D> points = new ArrayList<>();
    private final List<Point2D> manualTangents = new ArrayList<>();
    private final List<Boolean> useManualTangent = new ArrayList<>();
    private int draggingPointIndex = -1;
    private int draggingTangentIndex = -1;
    private final double threshold = 10; // 点击检测阈值

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        HBox toolbar = new HBox();
        Button saveButton = new Button("保存图像");

        saveButton.setOnAction(e -> saveImage(stage));
        toolbar.getChildren().add(saveButton);
        root.setTop(toolbar);
        root.setCenter(canvas);

        draw();

        // 左键点击添加点
        canvas.setOnMousePressed(e -> {
            Point2D click = new Point2D(e.getX(), e.getY());

            // 检查是否点击控制点
            for (int i = 0; i < points.size(); i++) {
                if (click.distance(points.get(i)) < threshold) {
                    draggingPointIndex = i;
                    return;
                }
            }

            // 右键点击虚线调整切线
            if (e.getButton() == MouseButton.SECONDARY) {
                for (int i = 0; i < points.size(); i++) {
                    Point2D tanEnd = points.get(i).add(manualTangents.get(i));
                    if (click.distance(tanEnd) < threshold) {
                        draggingTangentIndex = i;
                        return;
                    }
                }
            }

            // 左键点击空白区域添加新点
            if (e.getButton() == MouseButton.PRIMARY) {
                points.add(click);
                manualTangents.add(new Point2D(50, 0));
                useManualTangent.add(false);
                draw();
            }
        });

        // 鼠标拖动更新控制点或切线
        canvas.setOnMouseDragged(e -> {
            Point2D curr = new Point2D(e.getX(), e.getY());
            if (draggingPointIndex != -1) {
                points.set(draggingPointIndex, curr);
                draw();
            } else if (draggingTangentIndex != -1) {
                Point2D base = points.get(draggingTangentIndex);
                manualTangents.set(draggingTangentIndex, curr.subtract(base));
                useManualTangent.set(draggingTangentIndex, true);
                draw();
            }
        });

        // 鼠标释放
        canvas.setOnMouseReleased(e -> {
            draggingPointIndex = -1;
            draggingTangentIndex = -1;
        });

        stage.setTitle("Hermite Curve Editor (JavaFX)");
        stage.setScene(new Scene(root));
        stage.show();
    }

    // 绘制函数
    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // 生成切线列表（自动和手动混合）
        List<Point2D> tangents = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (useManualTangent.get(i)) {
                tangents.add(manualTangents.get(i));
            } else {
                Point2D t;
                if (i == 0 && points.size() > 1) {
                    t = points.get(1).subtract(points.get(0));
                } else if (i == points.size() - 1 && points.size() > 1) {
                    t = points.get(i).subtract(points.get(i - 1));
                } else if (i > 0 && i < points.size() - 1) {
                    t = points.get(i + 1).subtract(points.get(i - 1)).multiply(0.5);
                } else {
                    t = new Point2D(50, 0);
                }
                tangents.add(t);
            }
        }

        // 绘制切线（虚线）和控制点
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            Point2D t = tangents.get(i);

            // 虚线切线
            gc.setStroke(Color.GRAY);
            gc.setLineDashes(10);
            gc.strokeLine(p.getX(), p.getY(), p.getX() + t.getX(), p.getY() + t.getY());
            gc.setLineDashes(null);

            // 绘制控制点
            gc.setFill(Color.RED);
            gc.fillOval(p.getX() - 5, p.getY() - 5, 10, 10);
        }

        // 绘制 Hermite 曲线
        gc.setStroke(Color.BLUE);
        for (int i = 0; i < points.size() - 1; i++) {
            drawHermite(gc, points.get(i), points.get(i + 1), tangents.get(i), tangents.get(i + 1));
        }
    }

    private void drawHermite(GraphicsContext gc, Point2D p0, Point2D p1, Point2D m0, Point2D m1) {
        Point2D prev = hermitePoint(p0, p1, m0, m1, 0);
        for (int i = 1; i <= 100; i++) {
            double t = i / 100.0;
            Point2D curr = hermitePoint(p0, p1, m0, m1, t);
            gc.strokeLine(prev.getX(), prev.getY(), curr.getX(), curr.getY());
            prev = curr;
        }
    }

    private Point2D hermitePoint(Point2D p0, Point2D p1, Point2D m0, Point2D m1, double t) {
        double h00 = 2 * t * t * t - 3 * t * t + 1;
        double h10 = t * t * t - 2 * t * t + t;
        double h01 = -2 * t * t * t + 3 * t * t;
        double h11 = t * t * t - t * t;

        double x = h00 * p0.getX() + h10 * m0.getX() + h01 * p1.getX() + h11 * m1.getX();
        double y = h00 * p0.getY() + h10 * m0.getY() + h01 * p1.getY() + h11 * m1.getY();
        return new Point2D(x, y);
    }

    private void saveImage(Stage stage) {
        WritableImage image = canvas.snapshot(null, null);
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                BufferedImage bufferedImage = createBufferedImageFromWritableImage(image);
                ImageIO.write(bufferedImage, "png", file);
                System.out.println("图像保存成功：" + file.getAbsolutePath());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private BufferedImage createBufferedImageFromWritableImage(WritableImage writableImage) {
        int width = (int) writableImage.getWidth();
        int height = (int) writableImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bufferedImage.setRGB(x, y, writableImage.getPixelReader().getArgb(x, y));
            }
        }
        return bufferedImage;
    }

    public static void main(String[] args) {
        launch();
    }
}
